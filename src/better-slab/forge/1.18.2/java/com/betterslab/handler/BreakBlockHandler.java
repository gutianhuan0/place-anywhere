package com.betterslab.handler;

import com.betterslab.block.GenericVerticalSlabBlock;
import com.betterslab.block.MergedSlabBlock;
import com.betterslab.block.ModBlocks;
import com.betterslab.block.VerticalSlabBlock;
import com.betterslab.block.entity.GenericVerticalSlabEntity;
import com.betterslab.block.entity.MergedSlabEntity;
import com.betterslab.config.BetterSlabConfig;
import com.betterslab.util.MergedSlabTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BreakBlockHandler {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getWorld() instanceof Level level)) return;
        if (level.isClientSide) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Player player = event.getPlayer();
        BlockEntity blockEntity = level.getBlockEntity(pos);

        // MergedSlabBlock 总是选择性破坏（无 loot table，必须自定义掉落）
        if (state.getBlock() instanceof MergedSlabBlock) {
            event.setCanceled(true);
            handleMergedSlabBreak(level, player, pos, state, blockEntity);
            return;
        }

        // 竖半砖 DOUBLE 选择性破坏（同种两个单体，各挖各的）
        if ((state.getBlock() instanceof VerticalSlabBlock
                || state.getBlock() instanceof GenericVerticalSlabBlock)
                && state.getValue(GenericVerticalSlabBlock.DOUBLE)) {
            event.setCanceled(true);
            handleVerticalSlabDoubleBreak(level, player, pos, state, blockEntity);
            return;
        }

        // 通用竖半砖单体破坏（需要自定义掉落来源半砖）
        if (state.getBlock() instanceof GenericVerticalSlabBlock) {
            event.setCanceled(true);
            if (!player.isCreative() && blockEntity instanceof GenericVerticalSlabEntity g) {
                Block source = g.getSourceSlab();
                if (source != null) {
                    dropSlabItem(level, pos, source, player);
                }
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }

        if (!BetterSlabConfig.get().selectiveMining) {
            if (state.getBlock() instanceof SlabBlock) {
                MergedSlabTracker.remove(level, pos);
            }
            return;
        }
        if (!(state.getBlock() instanceof SlabBlock)) return;
        if (state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) return;

        event.setCanceled(true);

        boolean lookingAtUpper = isLookingAtUpperHalf(player, pos);
        MergedSlabTracker.MergedEntry merged = MergedSlabTracker.getMerged(level, pos);

        if (merged == null) {
            // 同种类 DOUBLE：原版行为，破坏对应一半，保留另一半
            if (lookingAtUpper) {
                level.setBlock(pos, state.setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
                dropSlabItem(level, pos, state.getBlock(), player);
            } else {
                level.setBlock(pos, state.setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL);
                dropSlabItem(level, pos, state.getBlock(), player);
            }
        } else {
            // 不同种类合并的 DOUBLE：A=原方块，B=合并方块（层位=merged.type）
            Block aBlock = state.getBlock();
            Block bBlock = merged.slab();
            SlabType bType = merged.type();
            boolean aIsUpper = (bType == SlabType.BOTTOM);

            if (lookingAtUpper) {
                Block upperBlock = aIsUpper ? aBlock : bBlock;
                Block remainBlock = aIsUpper ? bBlock : aBlock;
                SlabType remainType = aIsUpper ? SlabType.BOTTOM : SlabType.TOP;
                BlockState remainState = remainBlock.defaultBlockState().setValue(SlabBlock.TYPE, remainType);
                level.setBlock(pos, remainState, Block.UPDATE_ALL);
                dropSlabItem(level, pos, upperBlock, player);
            } else {
                Block lowerBlock = aIsUpper ? bBlock : aBlock;
                Block remainBlock = aIsUpper ? aBlock : bBlock;
                SlabType remainType = aIsUpper ? SlabType.TOP : SlabType.BOTTOM;
                BlockState remainState = remainBlock.defaultBlockState().setValue(SlabBlock.TYPE, remainType);
                level.setBlock(pos, remainState, Block.UPDATE_ALL);
                dropSlabItem(level, pos, lowerBlock, player);
            }
            MergedSlabTracker.remove(level, pos);
        }
    }

    private static void handleMergedSlabBreak(Level level, Player player, BlockPos pos,
                                              BlockState state, BlockEntity blockEntity) {
        if (!(blockEntity instanceof MergedSlabEntity be)) return;
        Block slabA = be.getSlabA();
        Block slabB = be.getSlabB();
        MergedSlabBlock.Orientation orientation = state.getValue(MergedSlabBlock.ORIENTATION);
        Direction facing = state.getValue(MergedSlabBlock.FACING);
        boolean waterlogged = state.getValue(MergedSlabBlock.WATERLOGGED);

        if (orientation == MergedSlabBlock.Orientation.HORIZONTAL) {
            boolean lookingAtUpper = isLookingAtUpperHalf(player, pos);
            if (lookingAtUpper) {
                dropSlabItem(level, pos, slabB, player);
                BlockState remainState = slabA.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                if (remainState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                    remainState = remainState.setValue(BlockStateProperties.WATERLOGGED, waterlogged);
                }
                level.setBlock(pos, remainState, Block.UPDATE_ALL);
            } else {
                dropSlabItem(level, pos, slabA, player);
                BlockState remainState = slabB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.TOP);
                if (remainState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                    remainState = remainState.setValue(BlockStateProperties.WATERLOGGED, waterlogged);
                }
                level.setBlock(pos, remainState, Block.UPDATE_ALL);
            }
        } else {
            boolean lookingAtSlabA = isLookingAtVerticalPrimaryHalf(player, pos, facing);
            if (lookingAtSlabA) {
                dropSlabItem(level, pos, slabA, player);
                setSingleVerticalSlab(level, pos, slabB, facing.getOpposite(), waterlogged);
            } else {
                dropSlabItem(level, pos, slabB, player);
                setSingleVerticalSlab(level, pos, slabA, facing, waterlogged);
            }
        }
    }

    private static void handleVerticalSlabDoubleBreak(Level level, Player player, BlockPos pos,
                                                      BlockState state, BlockEntity blockEntity) {
        Direction facing = state.getValue(GenericVerticalSlabBlock.FACING);
        boolean lookingAtFacingHalf = isLookingAtVerticalPrimaryHalf(player, pos, facing);
        boolean waterlogged = state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);

        Block sourceSlab = null;
        if (state.getBlock() instanceof VerticalSlabBlock) {
            sourceSlab = ModBlocks.getVanillaSlab(state.getBlock());
        } else if (blockEntity instanceof GenericVerticalSlabEntity g) {
            sourceSlab = g.getSourceSlab();
        }

        if (sourceSlab != null) {
            dropSlabItem(level, pos, sourceSlab, player);
        }

        Direction remainFacing = lookingAtFacingHalf ? facing.getOpposite() : facing;
        setSingleVerticalSlab(level, pos, sourceSlab, remainFacing, waterlogged);
    }

    private static void setSingleVerticalSlab(Level level, BlockPos pos, Block sourceSlab,
                                              Direction facing, boolean waterlogged) {
        boolean dedicated = ModBlocks.hasDedicatedVertical(sourceSlab);
        Block verticalBlock = dedicated ? ModBlocks.getVerticalSlab(sourceSlab) : ModBlocks.getGenericVerticalSlab();
        BlockState state = verticalBlock.defaultBlockState()
                .setValue(GenericVerticalSlabBlock.FACING, facing)
                .setValue(GenericVerticalSlabBlock.DOUBLE, false)
                .setValue(GenericVerticalSlabBlock.WATERLOGGED, waterlogged);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!dedicated) {
            if (level.getBlockEntity(pos) instanceof GenericVerticalSlabEntity g) {
                g.setSourceSlab(sourceSlab);
            }
        }
    }

    private static boolean isLookingAtVerticalPrimaryHalf(Player player, BlockPos pos, Direction facing) {
        Level level = player.level;
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(6.0));

        BlockHitResult hitResult = level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hitResult != null
                && hitResult.getType() == HitResult.Type.BLOCK
                && hitResult.getBlockPos().equals(pos)) {
            double localX = hitResult.getLocation().x - pos.getX();
            double localZ = hitResult.getLocation().z - pos.getZ();
            return switch (facing) {
                case NORTH -> localZ < 0.5;
                case SOUTH -> localZ >= 0.5;
                case EAST -> localX >= 0.5;
                case WEST -> localX < 0.5;
                default -> true;
            };
        }
        return true;
    }

    private static boolean isLookingAtUpperHalf(Player player, BlockPos pos) {
        Level level = player.level;
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(6.0));

        BlockHitResult hitResult = level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hitResult != null
                && hitResult.getType() == HitResult.Type.BLOCK
                && hitResult.getBlockPos().equals(pos)) {
            double localY = hitResult.getLocation().y - pos.getY();
            return localY >= 0.5;
        }

        return player.getEyePosition().y > pos.getY() + 0.5;
    }

    private static void dropSlabItem(Level level, BlockPos pos, Block slabBlock, Player player) {
        if (player.isCreative()) return;
        if (level instanceof ServerLevel) {
            ItemStack stack = new ItemStack(slabBlock.asItem());
            Block.popResource(level, pos, stack);
        }
    }
}
