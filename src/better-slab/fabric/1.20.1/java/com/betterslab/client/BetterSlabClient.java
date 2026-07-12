package com.betterslab.client;

import com.betterslab.block.GenericVerticalSlabBlock;
import com.betterslab.block.ModBlocks;
import com.betterslab.block.VerticalSlabBlock;
import com.betterslab.client.render.GenericVerticalSlabRenderer;
import com.betterslab.client.render.MergedSlabRenderer;
import com.betterslab.config.BetterSlabConfig;
import com.betterslab.networking.PlacementModePayload;
import com.betterslab.util.PlacementMode;
import com.betterslab.util.PlacementState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public class BetterSlabClient implements ClientModInitializer {

    private static boolean altHeldWithSlab = false;
    private static int lastSentMode = Integer.MIN_VALUE;

    private static boolean cWasPressed = false;
    private static boolean rWasPressed = false;

    private static BlockPos previewPos = null;
    private static BlockState previewState = null;

    @Override
    public void onInitializeClient() {
        ModKeyBindings.register();
        GenericVerticalSlabRenderer.register();
        MergedSlabRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            BetterSlabConfig config = BetterSlabConfig.get();

            boolean altPressed = ModKeyBindings.triggerKey.isPressed();
            boolean holdingSlab = isHoldingSlab(client);

            // Alt+半砖 才抑制移动
            altHeldWithSlab = altPressed && holdingSlab && config.preventMovement;

            // 计算临时 Alt 覆盖（直接读取 GLFW 原始键状态，不经过 KeyBinding）
            PlacementMode altOverride = null;
            if (altHeldWithSlab) {
                if (ModKeyBindings.isLeftPressed()) altOverride = PlacementMode.LEFT;
                else if (ModKeyBindings.isRightPressed()) altOverride = PlacementMode.RIGHT;
                else if (ModKeyBindings.isFrontPressed()) altOverride = PlacementMode.FRONT;
                else if (ModKeyBindings.isBackPressed()) altOverride = PlacementMode.BACK;
                else if (ModKeyBindings.isTopPressed()) altOverride = PlacementMode.TOP;
                else if (ModKeyBindings.isBottomPressed()) altOverride = PlacementMode.BOTTOM;
            }

            // C 键：用 GLFW 原始读取 + 边沿检测
            boolean cPressed = ModKeyBindings.isMovementKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_C);
            if (cPressed && !cWasPressed) {
                boolean altHeld = ModKeyBindings.triggerKey.isPressed();
                if (altHeld && altOverride != null) {
                    // Alt+方向键+C: 锁定当前 Alt 选择的模式
                    PlacementState.setLocked(client.player, altOverride);
                    notify(client, "§a已锁定: §b" + modeName(altOverride));
                } else {
                    // C（不按Alt）: 切换锁定/解锁
                    PlacementMode locked = PlacementState.getLocked(client.player);
                    if (locked != null) {
                        PlacementState.setLocked(client.player, null);
                        notify(client, "§e已解锁半砖放置模式");
                    } else {
                        PlacementMode cur = computeEffective(client.player, null);
                        PlacementState.setLocked(client.player, cur);
                        notify(client, "§a已锁定: §b" + modeName(cur));
                    }
                }
            }
            cWasPressed = cPressed;

            // R 键：切换默认横/竖（GLFW 原始读取 + 边沿检测）
            boolean rPressed = ModKeyBindings.isMovementKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_R);
            if (rPressed && !rWasPressed) {
                PlacementState.DefaultOrientation o = PlacementState.toggleDefault(client.player);
                notify(client, "§a默认半砖: §b" + (o == PlacementState.DefaultOrientation.VERTICAL ? "竖半砖" : "横半砖"));
            }
            rWasPressed = rPressed;

            // 计算并同步生效模式
            PlacementMode effective = computeEffective(client.player, altOverride);
            // 客户端也同步本地状态（用于客户端预测放置时读取正确模式）
            PlacementState.setMode(client.player, effective);
            if (effective.getId() != lastSentMode) {
                net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                PlacementModePayload.write(buf, effective.getId());
                ClientPlayNetworking.send(PlacementModePayload.ID, buf);
                lastSentMode = effective.getId();
            }

            updatePreview(client, effective, holdingSlab);
        });

        // 预览渲染（线框，Iris/Sodium 兼容）
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (context.consumers() == null) return;
            renderPreview(context.matrixStack(), context.camera().getPos(), context.consumers());
        });
    }

    private static PlacementMode computeEffective(net.minecraft.entity.player.PlayerEntity player, PlacementMode altOverride) {
        if (altOverride != null) return altOverride;
        PlacementMode locked = PlacementState.getLocked(player);
        if (locked != null) return locked;
        return PlacementState.getDefault(player) == PlacementState.DefaultOrientation.VERTICAL
                ? PlacementMode.AUTO_V : PlacementMode.AUTO_H;
    }

    private static void updatePreview(MinecraftClient client, PlacementMode mode, boolean holdingSlab) {
        previewPos = null;
        previewState = null;
        if (!holdingSlab || client.world == null || client.player == null) return;

        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        if (!(hit instanceof BlockHitResult blockHit)) return;

        BlockPos pos = blockHit.getBlockPos();
        Direction side = blockHit.getSide();
        ItemStack mainHand = client.player.getMainHandStack();
        Block slabBlock = getSlabBlock(mainHand);
        if (slabBlock == null) {
            slabBlock = getSlabBlock(client.player.getOffHandStack());
            if (slabBlock == null) return;
        }

        Direction playerFacing = client.player.getHorizontalFacing();

        // 具体方向模式
        if (mode == PlacementMode.LEFT) {
            setVerticalPreview(client, pos, slabBlock, playerFacing.rotateYCounterclockwise());
            return;
        }
        if (mode == PlacementMode.RIGHT) {
            setVerticalPreview(client, pos, slabBlock, playerFacing.rotateYClockwise());
            return;
        }
        if (mode == PlacementMode.FRONT) {
            setVerticalPreview(client, pos, slabBlock, playerFacing);
            return;
        }
        if (mode == PlacementMode.BACK) {
            setVerticalPreview(client, pos, slabBlock, playerFacing.getOpposite());
            return;
        }
        if (mode == PlacementMode.TOP) {
            previewPos = pos;
            previewState = slabBlock.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
            return;
        }
        if (mode == PlacementMode.BOTTOM) {
            previewPos = pos;
            previewState = slabBlock.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
            return;
        }

        if (mode == PlacementMode.AUTO_H) {
            // AUTO_H 完全原版交互：点击顶/底面放横半砖，点击侧面交给原版（不显示预览或显示横半砖）
            if (side.getAxis() == Direction.Axis.Y) {
                SlabType t = side == Direction.UP ? SlabType.BOTTOM : SlabType.TOP;
                previewPos = pos;
                previewState = slabBlock.getDefaultState().with(SlabBlock.TYPE, t);
            }
            // 点击侧面不显示预览（交给原版处理）
            return;
        }

        if (mode == PlacementMode.AUTO_V) {
            if (side.getAxis() != Direction.Axis.Y) {
                setVerticalPreview(client, pos, slabBlock, side.getOpposite());
            } else {
                Direction f = quadrantFacingPreview(blockHit, pos, side, playerFacing);
                setVerticalPreview(client, pos, slabBlock, f);
            }
            return;
        }
    }

    private static void setVerticalPreview(MinecraftClient client, BlockPos pos, Block sourceSlab, Direction facing) {
        Block vertical = ModBlocks.hasDedicatedVertical(sourceSlab)
                ? ModBlocks.getVerticalSlab(sourceSlab)
                : ModBlocks.GENERIC_VERTICAL_SLAB;
        previewPos = pos;
        previewState = vertical.getDefaultState()
                .with(GenericVerticalSlabBlock.FACING, facing)
                .with(GenericVerticalSlabBlock.DOUBLE, false);
    }

    private static Direction quadrantFacingPreview(BlockHitResult blockHit, BlockPos pos, Direction side, Direction playerFacing) {
        // 点击的方块在 pos.offset(side.opposite)；准星相对其中心
        BlockPos clicked = pos.offset(side.getOpposite());
        Vec3d hit = blockHit.getPos();
        double dx = hit.x - clicked.getX() - 0.5;
        double dz = hit.z - clicked.getZ() - 0.5;

        double along, sideR;
        switch (playerFacing) {
            case SOUTH -> { along =  dz; sideR = -dx; }
            case EAST  -> { along =  dx; sideR =  dz; }
            case WEST  -> { along = -dx; sideR = -dz; }
            default    -> { along = -dz; sideR =  dx; }
        }
        if (Math.abs(along) >= Math.abs(sideR)) {
            return along >= 0 ? playerFacing : playerFacing.getOpposite();
        }
        return sideR >= 0 ? playerFacing.rotateYClockwise() : playerFacing.rotateYCounterclockwise();
    }

    private static void renderPreview(MatrixStack matrices, Vec3d cameraPos, VertexConsumerProvider provider) {
        if (previewPos == null || previewState == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        VoxelShape shape = previewState.getOutlineShape(client.world, previewPos);
        if (shape.isEmpty()) shape = VoxelShapes.fullCube();

        double x = previewPos.getX() - cameraPos.x;
        double y = previewPos.getY() - cameraPos.y;
        double z = previewPos.getZ() - cameraPos.z;

        var box = shape.getBoundingBox();

        matrices.push();
        matrices.translate(x, y, z);
        try {
            VertexConsumer consumer = provider.getBuffer(RenderLayer.getLines());
            float r = 0.3f, g = 0.85f, b = 1.0f, a = 0.9f;
            WorldRenderer.drawBox(matrices, consumer,
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                    r, g, b, a);
        } catch (Throwable ignored) {
            // 渲染失败不崩溃
        }
        matrices.pop();
    }

    private static boolean isHoldingSlab(MinecraftClient client) {
        if (client.player == null) return false;
        return getSlabBlock(client.player.getMainHandStack()) != null
                || getSlabBlock(client.player.getOffHandStack()) != null;
    }

    private static Block getSlabBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof BlockItem bi)) return null;
        if (bi.getBlock() instanceof SlabBlock) return bi.getBlock();
        if (bi.getBlock() instanceof VerticalSlabBlock) return bi.getBlock();
        if (bi.getBlock() instanceof GenericVerticalSlabBlock) return bi.getBlock();
        return null;
    }

    private static String modeName(PlacementMode m) {
        return switch (m) {
            case AUTO_H -> "默认(横)";
            case AUTO_V -> "默认(竖)";
            case LEFT -> "左竖半砖";
            case RIGHT -> "右竖半砖";
            case FRONT -> "前竖半砖";
            case BACK -> "后竖半砖";
            case TOP -> "上半砖";
            case BOTTOM -> "下半砖";
        };
    }

    private static void notify(MinecraftClient client, String msg) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(msg), true);
        }
    }

    public static boolean isAltHeldWithSlab() {
        return altHeldWithSlab;
    }
}
