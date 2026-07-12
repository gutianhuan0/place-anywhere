package com.betterslab.client;

import com.betterslab.block.GenericVerticalSlabBlock;
import com.betterslab.block.ModBlocks;
import com.betterslab.block.VerticalSlabBlock;
import com.betterslab.client.render.GenericVerticalSlabRenderer;
import com.betterslab.client.render.MergedSlabRenderer;
import com.betterslab.config.BetterSlabConfig;
import com.betterslab.networking.ModNetworking;
import com.betterslab.networking.PlacementModePayload;
import com.betterslab.util.PlacementMode;
import com.betterslab.util.PlacementState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

public class BetterSlabClient {

    private static boolean altHeldWithSlab = false;
    private static int lastSentMode = Integer.MIN_VALUE;

    private static boolean cWasPressed = false;
    private static boolean rWasPressed = false;

    private static BlockPos previewPos = null;
    private static BlockState previewState = null;

    public static void init(IEventBus modBus) {
        modBus.addListener(BetterSlabClient::onRegisterRenderers);
        ModKeyBindings.register();
        modBus.addListener(BetterSlabClient::onRegisterRenderers);
        MinecraftForge.EVENT_BUS.register(BetterSlabClient.class);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.betterslab.block.entity.ModBlockEntities.GENERIC_VERTICAL_SLAB_ENTITY.get(),
                ctx -> new GenericVerticalSlabRenderer());
        event.registerBlockEntityRenderer(com.betterslab.block.entity.ModBlockEntities.MERGED_SLAB_ENTITY.get(),
                ctx -> new MergedSlabRenderer());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        BetterSlabConfig config = BetterSlabConfig.get();

        boolean altPressed = ModKeyBindings.triggerKey.isDown();
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
        boolean cPressed = ModKeyBindings.isMovementKeyPressed(GLFW.GLFW_KEY_C);
        if (cPressed && !cWasPressed) {
            boolean altHeld = ModKeyBindings.triggerKey.isDown();
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
        boolean rPressed = ModKeyBindings.isMovementKeyPressed(GLFW.GLFW_KEY_R);
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
            ModNetworking.INSTANCE.sendToServer(new PlacementModePayload(effective.getId()));
            lastSentMode = effective.getId();
        }

        updatePreview(client, effective, holdingSlab);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        MultiBufferSource.BufferSource provider = Minecraft.getInstance().renderBuffers().bufferSource();
        renderPreview(event.getPoseStack(), event.getCamera().getPosition(), provider);
    }

    private static PlacementMode computeEffective(Player player, PlacementMode altOverride) {
        if (altOverride != null) return altOverride;
        PlacementMode locked = PlacementState.getLocked(player);
        if (locked != null) return locked;
        return PlacementState.getDefault(player) == PlacementState.DefaultOrientation.VERTICAL
                ? PlacementMode.AUTO_V : PlacementMode.AUTO_H;
    }

    private static void updatePreview(Minecraft client, PlacementMode mode, boolean holdingSlab) {
        previewPos = null;
        previewState = null;
        if (!holdingSlab || client.level == null || client.player == null) return;

        HitResult hit = client.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        if (!(hit instanceof BlockHitResult blockHit)) return;

        BlockPos pos = blockHit.getBlockPos();
        Direction side = blockHit.getDirection();
        ItemStack mainHand = client.player.getMainHandItem();
        Block slabBlock = getSlabBlock(mainHand);
        if (slabBlock == null) {
            slabBlock = getSlabBlock(client.player.getOffhandItem());
            if (slabBlock == null) return;
        }

        Direction playerFacing = client.player.getDirection();

        // 具体方向模式
        if (mode == PlacementMode.LEFT) {
            setVerticalPreview(client, pos, slabBlock, playerFacing.getCounterClockWise());
            return;
        }
        if (mode == PlacementMode.RIGHT) {
            setVerticalPreview(client, pos, slabBlock, playerFacing.getClockWise());
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
            previewState = slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
            return;
        }
        if (mode == PlacementMode.BOTTOM) {
            previewPos = pos;
            previewState = slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            return;
        }

        if (mode == PlacementMode.AUTO_H) {
            // AUTO_H 完全原版交互：点击顶/底面放横半砖，点击侧面交给原版（不显示预览或显示横半砖）
            if (side.getAxis() == Direction.Axis.Y) {
                SlabType t = side == Direction.UP ? SlabType.BOTTOM : SlabType.TOP;
                previewPos = pos;
                previewState = slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, t);
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

    private static void setVerticalPreview(Minecraft client, BlockPos pos, Block sourceSlab, Direction facing) {
        Block vertical = ModBlocks.hasDedicatedVertical(sourceSlab)
                ? ModBlocks.getVerticalSlab(sourceSlab)
                : ModBlocks.getGenericVerticalSlab();
        previewPos = pos;
        previewState = vertical.defaultBlockState()
                .setValue(GenericVerticalSlabBlock.FACING, facing)
                .setValue(GenericVerticalSlabBlock.DOUBLE, false);
    }

    private static Direction quadrantFacingPreview(BlockHitResult blockHit, BlockPos pos, Direction side, Direction playerFacing) {
        // 点击的方块在 pos.offset(side.opposite)；准星相对其中心
        BlockPos clicked = pos.relative(side.getOpposite());
        Vec3 hit = blockHit.getLocation();
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
        return sideR >= 0 ? playerFacing.getClockWise() : playerFacing.getCounterClockWise();
    }

    private static void renderPreview(PoseStack matrices, Vec3 cameraPos, MultiBufferSource provider) {
        if (previewPos == null || previewState == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        VoxelShape shape = previewState.getShape(client.level, previewPos);
        if (shape.isEmpty()) shape = Shapes.block();

        double x = previewPos.getX() - cameraPos.x;
        double y = previewPos.getY() - cameraPos.y;
        double z = previewPos.getZ() - cameraPos.z;

        var box = shape.bounds();

        matrices.pushPose();
        matrices.translate(x, y, z);
        try {
            VertexConsumer consumer = provider.getBuffer(RenderType.lines());
            float r = 0.3f, g = 0.85f, b = 1.0f, a = 0.9f;
            LevelRenderer.renderLineBox(matrices, consumer,
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                    r, g, b, a);
        } catch (Throwable ignored) {
            // 渲染失败不崩溃
        }
        matrices.popPose();
    }

    private static boolean isHoldingSlab(Minecraft client) {
        if (client.player == null) return false;
        return getSlabBlock(client.player.getMainHandItem()) != null
                || getSlabBlock(client.player.getOffhandItem()) != null;
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

    private static void notify(Minecraft client, String msg) {
        if (client.player != null) {
            client.player.displayClientMessage(new TextComponent(msg), true);
        }
    }

    public static boolean isAltHeldWithSlab() {
        return altHeldWithSlab;
    }
}
