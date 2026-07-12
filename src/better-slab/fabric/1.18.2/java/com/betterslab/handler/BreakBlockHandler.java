package com.betterslab.handler;

import com.betterslab.block.GenericVerticalSlabBlock;
import com.betterslab.block.MergedSlabBlock;
import com.betterslab.block.ModBlocks;
import com.betterslab.block.VerticalSlabBlock;
import com.betterslab.block.entity.GenericVerticalSlabEntity;
import com.betterslab.block.entity.MergedSlabEntity;
import com.betterslab.config.BetterSlabConfig;
import com.betterslab.util.MergedSlabTracker;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class BreakBlockHandler {

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            // MergedSlabBlock 总是选择性破坏（无 loot table，必须自定义掉落）
            if (state.getBlock() instanceof MergedSlabBlock) {
                handleMergedSlabBreak(world, player, pos, state, blockEntity);
                return false;
            }

            // 竖半砖 DOUBLE 选择性破坏（同种两个单体，各挖各的）
            if ((state.getBlock() instanceof VerticalSlabBlock
                    || state.getBlock() instanceof GenericVerticalSlabBlock)
                    && state.get(GenericVerticalSlabBlock.DOUBLE)) {
                handleVerticalSlabDoubleBreak(world, player, pos, state, blockEntity);
                return false;
            }

            // 通用竖半砖单体破坏（需要自定义掉落来源半砖）
            if (state.getBlock() instanceof GenericVerticalSlabBlock) {
                if (!player.isCreative() && blockEntity instanceof GenericVerticalSlabEntity g) {
                    Block source = g.getSourceSlab();
                    if (source != null) {
                        dropSlabItem(world, pos, source, player);
                    }
                }
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                return false;
            }

            if (!BetterSlabConfig.get().selectiveMining) return true;
            if (!(state.getBlock() instanceof SlabBlock)) return true;
            if (state.get(SlabBlock.TYPE) != SlabType.DOUBLE) return true;

            boolean lookingAtUpper = isLookingAtUpperHalf(player, pos);
            MergedSlabTracker.MergedEntry merged = MergedSlabTracker.getMerged(world, pos);

            if (merged == null) {
                // 同种类 DOUBLE：原版行为，破坏对应一半，保留另一半
                if (lookingAtUpper) {
                    world.setBlockState(pos, state.with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_ALL);
                    dropSlabItem(world, pos, state.getBlock(), player);
                } else {
                    world.setBlockState(pos, state.with(SlabBlock.TYPE, SlabType.TOP), Block.NOTIFY_ALL);
                    dropSlabItem(world, pos, state.getBlock(), player);
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
                    BlockState remainState = remainBlock.getDefaultState().with(SlabBlock.TYPE, remainType);
                    world.setBlockState(pos, remainState, Block.NOTIFY_ALL);
                    dropSlabItem(world, pos, upperBlock, player);
                } else {
                    Block lowerBlock = aIsUpper ? bBlock : aBlock;
                    Block remainBlock = aIsUpper ? aBlock : bBlock;
                    SlabType remainType = aIsUpper ? SlabType.TOP : SlabType.BOTTOM;
                    BlockState remainState = remainBlock.getDefaultState().with(SlabBlock.TYPE, remainType);
                    world.setBlockState(pos, remainState, Block.NOTIFY_ALL);
                    dropSlabItem(world, pos, lowerBlock, player);
                }
                MergedSlabTracker.remove(world, pos);
            }

            return false;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (state.getBlock() instanceof SlabBlock) {
                MergedSlabTracker.remove(world, pos);
            }
        });
    }

    private static void handleMergedSlabBreak(World world, PlayerEntity player, BlockPos pos,
                                              BlockState state, BlockEntity blockEntity) {
        if (!(blockEntity instanceof MergedSlabEntity be)) return;
        Block slabA = be.getSlabA();
        Block slabB = be.getSlabB();
        MergedSlabBlock.Orientation orientation = state.get(MergedSlabBlock.ORIENTATION);
        Direction facing = state.get(MergedSlabBlock.FACING);
        boolean waterlogged = state.get(MergedSlabBlock.WATERLOGGED);

        if (orientation == MergedSlabBlock.Orientation.HORIZONTAL) {
            boolean lookingAtUpper = isLookingAtUpperHalf(player, pos);
            if (lookingAtUpper) {
                dropSlabItem(world, pos, slabB, player);
                BlockState remainState = slabA.getDefaultState()
                        .with(SlabBlock.TYPE, SlabType.BOTTOM);
                if (remainState.contains(Properties.WATERLOGGED)) {
                    remainState = remainState.with(Properties.WATERLOGGED, waterlogged);
                }
                world.setBlockState(pos, remainState, Block.NOTIFY_ALL);
            } else {
                dropSlabItem(world, pos, slabA, player);
                BlockState remainState = slabB.getDefaultState()
                        .with(SlabBlock.TYPE, SlabType.TOP);
                if (remainState.contains(Properties.WATERLOGGED)) {
                    remainState = remainState.with(Properties.WATERLOGGED, waterlogged);
                }
                world.setBlockState(pos, remainState, Block.NOTIFY_ALL);
            }
        } else {
            boolean lookingAtSlabA = isLookingAtVerticalPrimaryHalf(player, pos, facing);
            if (lookingAtSlabA) {
                dropSlabItem(world, pos, slabA, player);
                setSingleVerticalSlab(world, pos, slabB, facing.getOpposite(), waterlogged);
            } else {
                dropSlabItem(world, pos, slabB, player);
                setSingleVerticalSlab(world, pos, slabA, facing, waterlogged);
            }
        }
    }

    private static void handleVerticalSlabDoubleBreak(World world, PlayerEntity player, BlockPos pos,
                                                       BlockState state, BlockEntity blockEntity) {
        Direction facing = state.get(GenericVerticalSlabBlock.FACING);
        boolean lookingAtFacingHalf = isLookingAtVerticalPrimaryHalf(player, pos, facing);
        boolean waterlogged = state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED);

        Block sourceSlab = null;
        if (state.getBlock() instanceof VerticalSlabBlock) {
            sourceSlab = ModBlocks.getVanillaSlab(state.getBlock());
        } else if (blockEntity instanceof GenericVerticalSlabEntity g) {
            sourceSlab = g.getSourceSlab();
        }

        if (sourceSlab != null) {
            dropSlabItem(world, pos, sourceSlab, player);
        }

        Direction remainFacing = lookingAtFacingHalf ? facing.getOpposite() : facing;
        setSingleVerticalSlab(world, pos, sourceSlab, remainFacing, waterlogged);
    }

    private static void setSingleVerticalSlab(World world, BlockPos pos, Block sourceSlab,
                                              Direction facing, boolean waterlogged) {
        boolean dedicated = ModBlocks.hasDedicatedVertical(sourceSlab);
        Block verticalBlock = dedicated ? ModBlocks.getVerticalSlab(sourceSlab) : ModBlocks.GENERIC_VERTICAL_SLAB;
        BlockState state = verticalBlock.getDefaultState()
                .with(GenericVerticalSlabBlock.FACING, facing)
                .with(GenericVerticalSlabBlock.DOUBLE, false)
                .with(GenericVerticalSlabBlock.WATERLOGGED, waterlogged);
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        if (!dedicated) {
            if (world.getBlockEntity(pos) instanceof GenericVerticalSlabEntity g) {
                g.setSourceSlab(sourceSlab);
            }
        }
    }

    private static boolean isLookingAtVerticalPrimaryHalf(PlayerEntity player, BlockPos pos, Direction facing) {
        World world = player.getWorld();
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0F);
        Vec3d endPos = eyePos.add(lookVec.multiply(6.0));

        BlockHitResult hitResult = world.raycast(new RaycastContext(
                eyePos, endPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hitResult != null
                && hitResult.getType() == HitResult.Type.BLOCK
                && hitResult.getBlockPos().equals(pos)) {
            double localX = hitResult.getPos().x - pos.getX();
            double localZ = hitResult.getPos().z - pos.getZ();
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

    private static boolean isLookingAtUpperHalf(PlayerEntity player, BlockPos pos) {
        World world = player.getWorld();
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0F);
        Vec3d endPos = eyePos.add(lookVec.multiply(6.0));

        BlockHitResult hitResult = world.raycast(new RaycastContext(
                eyePos, endPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hitResult != null
                && hitResult.getType() == HitResult.Type.BLOCK
                && hitResult.getBlockPos().equals(pos)) {
            double localY = hitResult.getPos().y - pos.getY();
            return localY >= 0.5;
        }

        return player.getEyePos().y > pos.getY() + 0.5;
    }

    private static void dropSlabItem(World world, BlockPos pos, Block slabBlock, PlayerEntity player) {
        if (player.isCreative()) return;
        if (world instanceof ServerWorld) {
            ItemStack stack = new ItemStack(slabBlock.asItem());
            Block.dropStack(world, pos, stack);
        }
    }
}
