package com.betterslab.mixin;

import com.betterslab.block.GenericVerticalSlabBlock;
import com.betterslab.block.MergedSlabBlock;
import com.betterslab.block.ModBlocks;
import com.betterslab.block.VerticalSlabBlock;
import com.betterslab.block.entity.GenericVerticalSlabEntity;
import com.betterslab.block.entity.MergedSlabEntity;
import com.betterslab.config.BetterSlabConfig;
import com.betterslab.util.PlacementMode;
import com.betterslab.util.PlacementState;
import com.placeanywhere.core.FreeBlocks;
import com.placeanywhere.core.PlacedFreeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {

    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true)
    private void onPlace(BlockPlaceContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(ctx.getItemInHand().getItem() instanceof BlockItem blockItem)) return;
        Block block = blockItem.getBlock();

        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction side = ctx.getClickedFace();

        // === 半格放置：完整方块放在半砖/竖半砖上 → 骑跨（用自由方块偏移半格）===
        if (BetterSlabConfig.get().perfectPlacement
                && !(block instanceof SlabBlock)
                && !(block instanceof VerticalSlabBlock)
                && !(block instanceof GenericVerticalSlabBlock)
                && !(block instanceof MergedSlabBlock)
                && !isMultiblockBlock(block)) {
            // 1. 完美放置：完整方块放在横半砖上
            if (tryPerfectPlacementOnSlab(level, pos, block, side, cir)) return;
            // 2. 完美放置：完整方块放在竖半砖上
            if (tryPerfectPlacementOnVerticalSlab(level, pos, block, side, cir)) return;
        }

        // === 竖半砖物品（VerticalSlabBlock）的直接放置与合并 ===
        if (block instanceof VerticalSlabBlock) {
            if (!BetterSlabConfig.get().verticalSlab) return;
            Block sourceSlab = ModBlocks.getVanillaSlab(block);
            if (sourceSlab == null) return;
            Direction playerFacing = ctx.getPlayer().getDirection();
            if (tryMergeSameVerticalSlab(level, pos, block, playerFacing, cir)) return;
            BlockPos other = pos.relative(side.getOpposite());
            if (!other.equals(pos) && tryMergeSameVerticalSlab(level, other, block, playerFacing, cir)) return;
            return;
        }

        if (!(block instanceof SlabBlock)) return;
        if (!BetterSlabConfig.get().verticalSlab) return;

        PlacementMode mode = PlacementState.getMode(ctx.getPlayer());
        if (mode == null) mode = PlacementMode.AUTO_H;
        Direction playerFacing = ctx.getPlayer().getDirection();

        if (mode == PlacementMode.LEFT) {
            if (placeVertical(level, pos, block, playerFacing.getCounterClockWise(), ctx)) cancel(cir);
            return;
        }
        if (mode == PlacementMode.RIGHT) {
            if (placeVertical(level, pos, block, playerFacing.getClockWise(), ctx)) cancel(cir);
            return;
        }
        if (mode == PlacementMode.FRONT) {
            if (placeVertical(level, pos, block, playerFacing, ctx)) cancel(cir);
            return;
        }
        if (mode == PlacementMode.BACK) {
            if (placeVertical(level, pos, block, playerFacing.getOpposite(), ctx)) cancel(cir);
            return;
        }
        if (mode == PlacementMode.TOP) {
            if (placeHorizontal(level, pos, block, SlabType.TOP, side, ctx)) cancel(cir);
            return;
        }
        if (mode == PlacementMode.BOTTOM) {
            if (placeHorizontal(level, pos, block, SlabType.BOTTOM, side, ctx)) cancel(cir);
            return;
        }

        if (mode == PlacementMode.AUTO_H) {
            // 先检测：放在半格高的自由方块上 → 上半砖
            if (tryPlaceOnHalfHeightFreeBlock(level, pos, block, side, ctx, cir)) return;
            if (tryMergeHorizontal(level, pos, block, side, ctx, cir)) return;
            return;
        }

        if (mode == PlacementMode.AUTO_V) {
            if (side.getAxis() != Direction.Axis.Y) {
                if (placeVertical(level, pos, block, side.getOpposite(), ctx)) cancel(cir);
                return;
            }
            Direction f = quadrantFacing(ctx, playerFacing, side);
            if (placeVertical(level, pos, block, f, ctx)) cancel(cir);
            return;
        }
    }

    private static void cancel(CallbackInfoReturnable<InteractionResult> cir) {
        cir.setReturnValue(InteractionResult.SUCCESS);
        cir.cancel();
    }

    /** 判断是否为多格方块（占多个格子的方块，不参与骑跨，走原版逻辑）。 */
    private static boolean isMultiblockBlock(Block block) {
        if (block instanceof net.minecraft.world.level.block.DoorBlock) return true;
        if (block instanceof net.minecraft.world.level.block.BedBlock) return true;
        if (block instanceof net.minecraft.world.level.block.FenceGateBlock) return true;
        if (block instanceof net.minecraft.world.level.block.TallGrassBlock) return true;
        if (block instanceof net.minecraft.world.level.block.TallSeagrassBlock) return true;
        if (block instanceof net.minecraft.world.level.block.ScaffoldingBlock) return true;
        if (block instanceof net.minecraft.world.level.block.LeverBlock) return true;
        if (block instanceof net.minecraft.world.level.block.DiodeBlock) return true;
        if (block instanceof net.minecraft.world.level.block.RedStoneWireBlock) return true;
        if (block instanceof net.minecraft.world.level.block.RedstoneTorchBlock) return true;
        if (block instanceof net.minecraft.world.level.block.piston.PistonBaseBlock) return true;
        if (block instanceof net.minecraft.world.level.block.HopperBlock) return true;
        if (block instanceof net.minecraft.world.level.block.DispenserBlock) return true;
        if (block instanceof net.minecraft.world.level.block.DropperBlock) return true;
        if (block instanceof net.minecraft.world.level.block.ObserverBlock) return true;
        return false;
    }

    // ===================== 自由方块半格检测 =====================

    /**
     * 检测准星命中的方块下方是否有半格高的自由方块。
     * 如果有（如下半砖偏移 0.5），则在整数网格放置上半砖，使其与自由方块顶部对齐。
     */
    private static boolean tryPlaceOnHalfHeightFreeBlock(Level level, BlockPos pos, Block slabBlock,
                                                          Direction side, BlockPlaceContext ctx,
                                                          CallbackInfoReturnable<InteractionResult> cir) {
        if (side != Direction.UP) return false;
        // 检测 pos 下方一格范围内是否有自由方块，且其碰撞箱顶部在 0.5 高度附近
        Vec3 hitPos = ctx.getClickLocation();
        double queryX = hitPos.x;
        double queryY = pos.getY() - 0.1;
        double queryZ = hitPos.z;
        PlacedFreeBlock fb = FreeBlocks.getBlockAt(level, queryX, queryY, queryZ, 0.7);
        if (fb == null) return false;
        // 取自由方块的碰撞箱，检查其顶部是否在半格高度（0.5 附近）
        VoxelShape shape = fb.state().getCollisionShape(level, fb.pos().toBlockPos());
        if (shape.isEmpty()) return false;
        AABB box = shape.bounds();
        if (box == null) return false;
        double topY = fb.pos().y() + box.maxY;
        // 如果自由方块顶部在 0.4~0.6 之间（半格高），放上半砖
        double frac = topY - Math.floor(topY);
        if (frac > 0.35 && frac < 0.65) {
            // 在整数网格放置上半砖
            BlockState slabState = slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
            BlockPos placePos = pos;
            if (!level.getBlockState(placePos).canBeReplaced(ctx)) {
                placePos = pos.relative(side);
                if (!level.getBlockState(placePos).canBeReplaced(ctx)) return false;
            }
            level.setBlock(placePos, slabState, Block.UPDATE_ALL);
            cancel(cir);
            return true;
        }
        return false;
    }

    // ===================== 骑跨：横半砖 =====================

    /**
     * 完整方块放在横半砖上 → 用自由方块在半砖所在格子偏移半格放置。
     * <ul>
     *   <li>点击下半砖顶面（side=UP）→ 完整方块放在下方格子，y 偏移 +0.5（骑在下半砖上方）</li>
     *   <li>点击上半砖底面（side=DOWN）→ 完整方块放在上方格子，y 偏移 -0.5（挂在上半砖下方）</li>
     * </ul>
     * 半砖保留在原位（整数网格），完整方块作为自由方块偏移半格。
     */
    private static boolean tryPerfectPlacementOnSlab(Level level, BlockPos pos, Block fullBlock, Direction side,
                                                      CallbackInfoReturnable<InteractionResult> cir) {
        if (side == Direction.UP) {
            BlockPos belowPos = pos.below();
            BlockState belowState = level.getBlockState(belowPos);
            if (belowState.getBlock() instanceof SlabBlock
                    && belowState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
                // 完整方块放在下方格子，y+0.5（骑在下半砖上方半格）
                double x = belowPos.getX();
                double y = belowPos.getY() + 0.5;
                double z = belowPos.getZ();
                BlockState state = fullBlock.defaultBlockState();
                if (FreeBlocks.placeBlock(level, x, y, z, state)) {
                    cancel(cir);
                    return true;
                }
            }
        } else if (side == Direction.DOWN) {
            BlockPos abovePos = pos.above();
            BlockState aboveState = level.getBlockState(abovePos);
            if (aboveState.getBlock() instanceof SlabBlock
                    && aboveState.getValue(SlabBlock.TYPE) == SlabType.TOP) {
                // 完整方块放在上方格子，y-0.5（挂在上半砖下方半格）
                double x = abovePos.getX();
                double y = abovePos.getY() - 0.5;
                double z = abovePos.getZ();
                BlockState state = fullBlock.defaultBlockState();
                if (FreeBlocks.placeBlock(level, x, y, z, state)) {
                    cancel(cir);
                    return true;
                }
            }
        }
        return false;
    }

    // ===================== 骑跨：竖半砖 =====================

    /**
     * 完整方块放在竖半砖旁边 → 用自由方块在竖半砖所在格子水平偏移半格放置。
     * 点击竖半砖的开口面（与 FACING 反向）→ 完整方块放在竖半砖格子，朝开口方向偏移 0.5。
     */
    private static boolean tryPerfectPlacementOnVerticalSlab(Level level, BlockPos pos, Block fullBlock, Direction side,
                                                              CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos slabPos = pos.relative(side.getOpposite());
        BlockState slabState = level.getBlockState(slabPos);

        Direction slabFacing = null;
        boolean isDouble = false;

        if (slabState.getBlock() instanceof VerticalSlabBlock) {
            slabFacing = slabState.getValue(VerticalSlabBlock.FACING);
            isDouble = slabState.getValue(VerticalSlabBlock.DOUBLE);
        } else if (slabState.getBlock() instanceof GenericVerticalSlabBlock) {
            isDouble = slabState.getValue(GenericVerticalSlabBlock.DOUBLE);
            if (isDouble) return false;
            slabFacing = slabState.getValue(GenericVerticalSlabBlock.FACING);
        }

        if (slabFacing == null) return false;
        // 只有点击竖半砖的开口面才骑跨
        if (side != slabFacing.getOpposite()) return false;

        // 完整方块放在竖半砖格子，朝开口方向偏移 0.5
        double x = slabPos.getX() + slabFacing.getOpposite().getStepX() * 0.5;
        double y = slabPos.getY();
        double z = slabPos.getZ() + slabFacing.getOpposite().getStepZ() * 0.5;
        BlockState state = fullBlock.defaultBlockState();
        if (FreeBlocks.placeBlock(level, x, y, z, state)) {
            cancel(cir);
            return true;
        }
        return false;
    }

    // ===================== 竖半砖 =====================

    private static boolean tryMergeSameVerticalSlab(Level level, BlockPos pos, Block verticalBlock,
                                                     Direction playerFacing, CallbackInfoReturnable<InteractionResult> cir) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() == verticalBlock
                && s.hasProperty(VerticalSlabBlock.DOUBLE)
                && !s.getValue(VerticalSlabBlock.DOUBLE)) {
            Direction existingFacing = s.getValue(VerticalSlabBlock.FACING);
            boolean waterlogged = s.getValue(VerticalSlabBlock.WATERLOGGED);
            Block sourceSlab = ModBlocks.getVanillaSlab(verticalBlock);
            BlockState mergedState = ModBlocks.getMergedSlab().defaultBlockState()
                    .setValue(MergedSlabBlock.ORIENTATION, MergedSlabBlock.Orientation.VERTICAL)
                    .setValue(MergedSlabBlock.FACING, existingFacing)
                    .setValue(MergedSlabBlock.WATERLOGGED, waterlogged);
            level.setBlock(pos, mergedState, Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof MergedSlabEntity be) {
                be.setSlabA(sourceSlab);
                be.setSlabB(sourceSlab);
                be.sync();
            }
            cancel(cir);
            return true;
        }
        return false;
    }

    private static boolean placeVertical(Level level, BlockPos pos, Block sourceSlab,
                                         Direction facing, BlockPlaceContext ctx) {
        boolean dedicated = ModBlocks.hasDedicatedVertical(sourceSlab);
        Block verticalBlock = dedicated ? ModBlocks.getVerticalSlab(sourceSlab) : ModBlocks.getGenericVerticalSlab();

        BlockState existing = level.getBlockState(pos);
        Block existingSource = getVerticalSource(level, pos, existing);

        if (existingSource != null) {
            BlockState ex = level.getBlockState(pos);
            if (!getVerticalDouble(ex) && getVerticalFacing(ex) == facing.getOpposite()) {
                boolean waterlogged = ex.getValue(GenericVerticalSlabBlock.WATERLOGGED);
                Direction existingFacing = getVerticalFacing(ex);
                BlockState mergedState = ModBlocks.getMergedSlab().defaultBlockState()
                        .setValue(MergedSlabBlock.ORIENTATION, MergedSlabBlock.Orientation.VERTICAL)
                        .setValue(MergedSlabBlock.FACING, existingFacing)
                        .setValue(MergedSlabBlock.WATERLOGGED, waterlogged);
                level.setBlock(pos, mergedState, Block.UPDATE_ALL);
                if (level.getBlockEntity(pos) instanceof MergedSlabEntity be) {
                    be.setSlabA(existingSource);
                    be.setSlabB(sourceSlab);
                    be.sync();
                }
                return true;
            }
        }

        BlockState verticalState = verticalBlock.defaultBlockState()
                .setValue(GenericVerticalSlabBlock.FACING, facing)
                .setValue(GenericVerticalSlabBlock.DOUBLE, false)
                .setValue(GenericVerticalSlabBlock.WATERLOGGED, isWater(level, pos));

        BlockPos placePos = pos;
        if (!existing.canBeReplaced(ctx)) {
            BlockPos adj = pos.relative(facing.getOpposite());
            if (level.getBlockState(adj).canBeReplaced(ctx)) {
                placePos = adj;
            } else {
                return false;
            }
        }

        level.setBlock(placePos, verticalState, Block.UPDATE_ALL);
        if (!dedicated) {
            if (level.getBlockEntity(placePos) instanceof GenericVerticalSlabEntity g) {
                g.setSourceSlab(sourceSlab);
                g.sync();
            }
        }
        return true;
    }

    private static Block getVerticalSource(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof VerticalSlabBlock) {
            return ModBlocks.getVanillaSlab(state.getBlock());
        }
        if (state.getBlock() instanceof GenericVerticalSlabBlock) {
            if (level.getBlockEntity(pos) instanceof GenericVerticalSlabEntity g) {
                return g.getSourceSlab();
            }
        }
        return null;
    }

    private static boolean isWater(Level level, BlockPos pos) {
        FluidState fs = level.getFluidState(pos);
        return fs.is(Fluids.WATER) && fs.isSource();
    }

    private static boolean getVerticalDouble(BlockState s) {
        if (s.getBlock() instanceof VerticalSlabBlock) return s.getValue(VerticalSlabBlock.DOUBLE);
        if (s.getBlock() instanceof GenericVerticalSlabBlock) return s.getValue(GenericVerticalSlabBlock.DOUBLE);
        return false;
    }

    private static Direction getVerticalFacing(BlockState s) {
        return s.getValue(GenericVerticalSlabBlock.FACING);
    }

    // ===================== 横半砖 =====================

    private static boolean placeHorizontal(Level level, BlockPos pos, Block slabBlock, SlabType type,
                                           Direction side, BlockPlaceContext ctx) {
        if (tryMergeHorizontal(level, pos, slabBlock, side, ctx, null)) return true;

        if (sameTypeSlabAt(level, pos, slabBlock) || sameTypeSlabAt(level, pos.relative(side.getOpposite()), slabBlock)) {
            return false;
        }

        BlockState existing = level.getBlockState(pos);
        BlockState slabState = slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, type);

        BlockPos placePos = pos;
        if (!existing.canBeReplaced(ctx)) {
            BlockPos adj = pos.relative(side);
            if (level.getBlockState(adj).canBeReplaced(ctx)) {
                placePos = adj;
            } else {
                return false;
            }
        }
        level.setBlock(placePos, slabState, Block.UPDATE_ALL);
        return true;
    }

    private static boolean sameTypeSlabAt(Level level, BlockPos pos, Block slabBlock) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() == slabBlock
                && s.hasProperty(SlabBlock.TYPE)
                && s.getValue(SlabBlock.TYPE) != SlabType.DOUBLE;
    }

    private static boolean tryMergeHorizontal(Level level, BlockPos pos, Block newSlab, Direction side,
                                              BlockPlaceContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        if (mergeAt(level, pos, newSlab, ctx)) {
            if (cir != null) cancel(cir);
            return true;
        }
        BlockPos other = pos.relative(side.getOpposite());
        if (!other.equals(pos) && mergeAt(level, other, newSlab, ctx)) {
            if (cir != null) cancel(cir);
            return true;
        }
        return false;
    }

    private static boolean mergeAt(Level level, BlockPos pos, Block newSlab, BlockPlaceContext ctx) {
        BlockState existing = level.getBlockState(pos);
        if (!(existing.getBlock() instanceof SlabBlock)) return false;
        SlabType existingType = existing.getValue(SlabBlock.TYPE);
        if (existingType == SlabType.DOUBLE) return false;
        if (existing.getBlock() == newSlab) return false;

        Block slabA, slabB;
        if (existingType == SlabType.BOTTOM) {
            slabA = existing.getBlock();
            slabB = newSlab;
        } else {
            slabA = newSlab;
            slabB = existing.getBlock();
        }
        boolean waterlogged = existing.hasProperty(BlockStateProperties.WATERLOGGED)
                && existing.getValue(BlockStateProperties.WATERLOGGED);
        BlockState mergedState = ModBlocks.getMergedSlab().defaultBlockState()
                .setValue(MergedSlabBlock.ORIENTATION, MergedSlabBlock.Orientation.HORIZONTAL)
                .setValue(MergedSlabBlock.FACING, Direction.NORTH)
                .setValue(MergedSlabBlock.WATERLOGGED, waterlogged);
        level.setBlock(pos, mergedState, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof MergedSlabEntity be) {
            be.setSlabA(slabA);
            be.setSlabB(slabB);
            be.sync();
        }
        return true;
    }

    // ===================== AUTO_V 象限判定 =====================

    private static Direction quadrantFacing(BlockPlaceContext ctx, Direction playerFacing, Direction side) {
        BlockPos clicked = ctx.getClickedPos().relative(side.getOpposite());
        Vec3 hit = ctx.getClickLocation();
        double dx = hit.x - clicked.getX() - 0.5;
        double dz = hit.z - clicked.getZ() - 0.5;

        double along, sideR;
        switch (playerFacing) {
            case SOUTH -> { along =  dz; sideR = -dx; }
            case EAST  -> { along =  dx; sideR =  dz; }
            case WEST  -> { along = -dx; sideR = -dz; }
            default    -> { along = -dz; sideR =  dx; }
        }

        Direction facing;
        if (Math.abs(along) >= Math.abs(sideR)) {
            facing = along >= 0 ? playerFacing : playerFacing.getOpposite();
        } else {
            facing = sideR >= 0 ? playerFacing.getClockWise() : playerFacing.getCounterClockWise();
        }
        return facing;
    }
}
