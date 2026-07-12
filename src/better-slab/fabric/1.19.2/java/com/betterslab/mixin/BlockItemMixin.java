package com.betterslab.mixin;

import com.betterslab.block.GenericVerticalSlabBlock;
import com.betterslab.block.MergedSlabBlock;
import com.betterslab.block.ModBlocks;
import com.betterslab.block.VerticalSlabBlock;
import com.betterslab.block.entity.GenericVerticalSlabEntity;
import com.betterslab.block.entity.MergedSlabEntity;
import com.betterslab.config.BetterSlabConfig;
import com.betterslab.util.MergedSlabTracker;
import com.betterslab.util.PlacementMode;
import com.betterslab.util.PlacementState;
import com.placeanywhere.core.FreeBlocks;
import com.placeanywhere.core.PlacedFreeBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"), cancellable = true)
    private void onPlace(ItemPlacementContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (!(ctx.getStack().getItem() instanceof BlockItem blockItem)) return;
        Block block = blockItem.getBlock();

        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        Direction side = ctx.getSide();

        // === 半格放置：完整方块放在半砖/竖半砖上 → 骑跨（用自由方块偏移半格）===
        if (BetterSlabConfig.get().perfectPlacement
                && !(block instanceof SlabBlock)
                && !(block instanceof VerticalSlabBlock)
                && !(block instanceof GenericVerticalSlabBlock)
                && !(block instanceof MergedSlabBlock)
                && !isMultiblockBlock(block)) {
            // 1. 完美放置：完整方块放在横半砖上
            if (tryPerfectPlacementOnSlab(world, pos, block, side, cir)) return;
            // 2. 完美放置：完整方块放在竖半砖上
            if (tryPerfectPlacementOnVerticalSlab(world, pos, block, side, cir)) return;
        }

        // === 竖半砖物品（VerticalSlabBlock）的直接放置与合并 ===
        if (block instanceof VerticalSlabBlock) {
            if (!BetterSlabConfig.get().verticalSlab) return;
            Block sourceSlab = ModBlocks.getVanillaSlab(block);
            if (sourceSlab == null) return;
            Direction playerFacing = ctx.getPlayer().getHorizontalFacing();
            if (tryMergeSameVerticalSlab(world, pos, block, playerFacing, cir)) return;
            BlockPos other = pos.offset(side.getOpposite());
            if (!other.equals(pos) && tryMergeSameVerticalSlab(world, other, block, playerFacing, cir)) return;
            return;
        }

        if (!(block instanceof SlabBlock)) return;
        if (!BetterSlabConfig.get().verticalSlab) return;

        PlacementMode mode = PlacementState.getMode(ctx.getPlayer());
        if (mode == null) mode = PlacementMode.AUTO_H;
        Direction playerFacing = ctx.getPlayer().getHorizontalFacing();

        if (mode == PlacementMode.LEFT) {
            if (placeVertical(world, pos, block, playerFacing.rotateYCounterclockwise(), ctx)) cancel(cir);
            return;
        }
        if (mode == PlacementMode.RIGHT) {
            if (placeVertical(world, pos, block, playerFacing.rotateYClockwise(), ctx)) cancel(cir);
            return;
        }
        if (mode == PlacementMode.FRONT) {
            if (placeVertical(world, pos, block, playerFacing, ctx)) cancel(cir);
            return;
        }
        if (mode == PlacementMode.BACK) {
            if (placeVertical(world, pos, block, playerFacing.getOpposite(), ctx)) cancel(cir);
            return;
        }
        if (mode == PlacementMode.TOP) {
            if (placeHorizontal(world, pos, block, SlabType.TOP, side, ctx)) cancel(cir);
            return;
        }
        if (mode == PlacementMode.BOTTOM) {
            if (placeHorizontal(world, pos, block, SlabType.BOTTOM, side, ctx)) cancel(cir);
            return;
        }

        if (mode == PlacementMode.AUTO_H) {
            // 先检测：放在半格高的自由方块上 → 上半砖
            if (tryPlaceOnHalfHeightFreeBlock(world, pos, block, side, ctx, cir)) return;
            if (tryMergeHorizontal(world, pos, block, side, ctx, cir)) return;
            return;
        }

        if (mode == PlacementMode.AUTO_V) {
            if (side.getAxis() != Direction.Axis.Y) {
                if (placeVertical(world, pos, block, side.getOpposite(), ctx)) cancel(cir);
                return;
            }
            Direction f = quadrantFacing(ctx, playerFacing, side);
            if (placeVertical(world, pos, block, f, ctx)) cancel(cir);
            return;
        }
    }

    private static void cancel(CallbackInfoReturnable<ActionResult> cir) {
        cir.setReturnValue(ActionResult.SUCCESS);
        cir.cancel();
    }

    /** 判断是否为多格方块（占多个格子的方块，不参与骑跨，走原版逻辑）。 */
    private static boolean isMultiblockBlock(Block block) {
        if (block instanceof net.minecraft.block.DoorBlock) return true;
        if (block instanceof net.minecraft.block.BedBlock) return true;
        if (block instanceof net.minecraft.block.FenceGateBlock) return true;
        if (block instanceof net.minecraft.block.TallPlantBlock) return true;
        if (block instanceof net.minecraft.block.TallSeagrassBlock) return true;
        if (block instanceof net.minecraft.block.ScaffoldingBlock) return true;
        if (block instanceof net.minecraft.block.LeverBlock) return true;
        if (block instanceof net.minecraft.block.AbstractRedstoneGateBlock) return true;
        if (block instanceof net.minecraft.block.RedstoneWireBlock) return true;
        if (block instanceof net.minecraft.block.RedstoneTorchBlock) return true;
        if (block instanceof net.minecraft.block.PistonBlock) return true;
        if (block instanceof net.minecraft.block.HopperBlock) return true;
        if (block instanceof net.minecraft.block.DispenserBlock) return true;
        if (block instanceof net.minecraft.block.DropperBlock) return true;
        if (block instanceof net.minecraft.block.ObserverBlock) return true;
        return false;
    }

    // ===================== 自由方块半格检测 =====================

    /**
     * 检测准星命中的方块下方是否有半格高的自由方块。
     * 如果有（如下半砖偏移 0.5），则在整数网格放置上半砖，使其与自由方块顶部对齐。
     */
    private static boolean tryPlaceOnHalfHeightFreeBlock(World world, BlockPos pos, Block slabBlock,
                                                          Direction side, ItemPlacementContext ctx,
                                                          CallbackInfoReturnable<ActionResult> cir) {
        if (side != Direction.UP) return false;
        // 检测 pos 下方一格范围内是否有自由方块，且其碰撞箱顶部在 0.5 高度附近
        Vec3d hitPos = ctx.getHitPos();
        double queryX = hitPos.x;
        double queryY = pos.getY() - 0.1;
        double queryZ = hitPos.z;
        PlacedFreeBlock fb = FreeBlocks.getBlockAt(world, queryX, queryY, queryZ, 0.7);
        if (fb == null) return false;
        // 取自由方块的碰撞箱，检查其顶部是否在半格高度（0.5 附近）
        VoxelShape shape = fb.state().getCollisionShape(world, fb.pos().toBlockPos());
        if (shape.isEmpty()) return false;
        Box box = shape.getBoundingBox();
        if (box == null) return false;
        double topY = fb.pos().y() + box.maxY;
        // 如果自由方块顶部在 0.4~0.6 之间（半格高），放上半砖
        double frac = topY - Math.floor(topY);
        if (frac > 0.35 && frac < 0.65) {
            // 在整数网格放置上半砖
            BlockState slabState = slabBlock.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
            BlockPos placePos = pos;
            if (!world.getBlockState(placePos).canReplace(ctx)) {
                placePos = pos.offset(side);
                if (!world.getBlockState(placePos).canReplace(ctx)) return false;
            }
            world.setBlockState(placePos, slabState, Block.NOTIFY_ALL);
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
    private static boolean tryPerfectPlacementOnSlab(World world, BlockPos pos, Block fullBlock, Direction side,
                                                      CallbackInfoReturnable<ActionResult> cir) {
        if (side == Direction.UP) {
            BlockPos belowPos = pos.down();
            BlockState belowState = world.getBlockState(belowPos);
            if (belowState.getBlock() instanceof SlabBlock
                    && belowState.get(SlabBlock.TYPE) == SlabType.BOTTOM) {
                // 完整方块放在下方格子，y+0.5（骑在下半砖上方半格）
                double x = belowPos.getX();
                double y = belowPos.getY() + 0.5;
                double z = belowPos.getZ();
                BlockState state = fullBlock.getDefaultState();
                if (FreeBlocks.placeBlock(world, x, y, z, state)) {
                    cancel(cir);
                    return true;
                }
            }
        } else if (side == Direction.DOWN) {
            BlockPos abovePos = pos.up();
            BlockState aboveState = world.getBlockState(abovePos);
            if (aboveState.getBlock() instanceof SlabBlock
                    && aboveState.get(SlabBlock.TYPE) == SlabType.TOP) {
                // 完整方块放在上方格子，y-0.5（挂在上半砖下方半格）
                double x = abovePos.getX();
                double y = abovePos.getY() - 0.5;
                double z = abovePos.getZ();
                BlockState state = fullBlock.getDefaultState();
                if (FreeBlocks.placeBlock(world, x, y, z, state)) {
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
    private static boolean tryPerfectPlacementOnVerticalSlab(World world, BlockPos pos, Block fullBlock, Direction side,
                                                              CallbackInfoReturnable<ActionResult> cir) {
        BlockPos slabPos = pos.offset(side.getOpposite());
        BlockState slabState = world.getBlockState(slabPos);

        Direction slabFacing = null;
        boolean isDouble = false;

        if (slabState.getBlock() instanceof VerticalSlabBlock) {
            slabFacing = slabState.get(VerticalSlabBlock.FACING);
            isDouble = slabState.get(VerticalSlabBlock.DOUBLE);
        } else if (slabState.getBlock() instanceof GenericVerticalSlabBlock) {
            isDouble = slabState.get(GenericVerticalSlabBlock.DOUBLE);
            if (isDouble) return false;
            slabFacing = slabState.get(GenericVerticalSlabBlock.FACING);
        }

        if (slabFacing == null) return false;
        // 只有点击竖半砖的开口面才骑跨
        if (side != slabFacing.getOpposite()) return false;

        // 完整方块放在竖半砖格子，朝开口方向偏移 0.5
        double x = slabPos.getX() + slabFacing.getOpposite().getOffsetX() * 0.5;
        double y = slabPos.getY();
        double z = slabPos.getZ() + slabFacing.getOpposite().getOffsetZ() * 0.5;
        BlockState state = fullBlock.getDefaultState();
        if (FreeBlocks.placeBlock(world, x, y, z, state)) {
            cancel(cir);
            return true;
        }
        return false;
    }

    // ===================== 竖半砖 =====================

    private static boolean tryMergeSameVerticalSlab(World world, BlockPos pos, Block verticalBlock,
                                                     Direction playerFacing, CallbackInfoReturnable<ActionResult> cir) {
        BlockState s = world.getBlockState(pos);
        if (s.getBlock() == verticalBlock
                && s.contains(VerticalSlabBlock.DOUBLE)
                && !s.get(VerticalSlabBlock.DOUBLE)) {
            Direction existingFacing = s.get(VerticalSlabBlock.FACING);
            boolean waterlogged = s.get(VerticalSlabBlock.WATERLOGGED);
            Block sourceSlab = ModBlocks.getVanillaSlab(verticalBlock);
            BlockState mergedState = ModBlocks.MERGED_SLAB.getDefaultState()
                    .with(MergedSlabBlock.ORIENTATION, MergedSlabBlock.Orientation.VERTICAL)
                    .with(MergedSlabBlock.FACING, existingFacing)
                    .with(MergedSlabBlock.WATERLOGGED, waterlogged);
            world.setBlockState(pos, mergedState, Block.NOTIFY_ALL);
            if (world.getBlockEntity(pos) instanceof MergedSlabEntity be) {
                be.setSlabA(sourceSlab);
                be.setSlabB(sourceSlab);
                be.sync();
            }
            cancel(cir);
            return true;
        }
        return false;
    }

    private static boolean placeVertical(World world, BlockPos pos, Block sourceSlab,
                                         Direction facing, ItemPlacementContext ctx) {
        boolean dedicated = ModBlocks.hasDedicatedVertical(sourceSlab);
        Block verticalBlock = dedicated ? ModBlocks.getVerticalSlab(sourceSlab) : ModBlocks.GENERIC_VERTICAL_SLAB;

        BlockState existing = world.getBlockState(pos);
        Block existingSource = getVerticalSource(world, pos, existing);

        if (existingSource != null) {
            BlockState ex = world.getBlockState(pos);
            if (!getVerticalDouble(ex) && getVerticalFacing(ex) == facing.getOpposite()) {
                boolean waterlogged = ex.get(GenericVerticalSlabBlock.WATERLOGGED);
                Direction existingFacing = getVerticalFacing(ex);
                BlockState mergedState = ModBlocks.MERGED_SLAB.getDefaultState()
                        .with(MergedSlabBlock.ORIENTATION, MergedSlabBlock.Orientation.VERTICAL)
                        .with(MergedSlabBlock.FACING, existingFacing)
                        .with(MergedSlabBlock.WATERLOGGED, waterlogged);
                world.setBlockState(pos, mergedState, Block.NOTIFY_ALL);
                if (world.getBlockEntity(pos) instanceof MergedSlabEntity be) {
                    be.setSlabA(existingSource);
                    be.setSlabB(sourceSlab);
                    be.sync();
                }
                return true;
            }
        }

        BlockState verticalState = verticalBlock.getDefaultState()
                .with(GenericVerticalSlabBlock.FACING, facing)
                .with(GenericVerticalSlabBlock.DOUBLE, false)
                .with(GenericVerticalSlabBlock.WATERLOGGED, isWater(world, pos));

        BlockPos placePos = pos;
        if (!existing.canReplace(ctx)) {
            BlockPos adj = pos.offset(facing.getOpposite());
            if (world.getBlockState(adj).canReplace(ctx)) {
                placePos = adj;
            } else {
                return false;
            }
        }

        world.setBlockState(placePos, verticalState, Block.NOTIFY_ALL);
        if (!dedicated) {
            if (world.getBlockEntity(placePos) instanceof GenericVerticalSlabEntity g) {
                g.setSourceSlab(sourceSlab);
                g.sync();
            }
        }
        return true;
    }

    private static Block getVerticalSource(World world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof VerticalSlabBlock) {
            return ModBlocks.getVanillaSlab(state.getBlock());
        }
        if (state.getBlock() instanceof GenericVerticalSlabBlock) {
            if (world.getBlockEntity(pos) instanceof GenericVerticalSlabEntity g) {
                return g.getSourceSlab();
            }
        }
        return null;
    }

    private static boolean isWater(World world, BlockPos pos) {
        FluidState fs = world.getFluidState(pos);
        return fs.isOf(Fluids.WATER) && fs.isStill();
    }

    private static boolean getVerticalDouble(BlockState s) {
        if (s.getBlock() instanceof VerticalSlabBlock) return s.get(VerticalSlabBlock.DOUBLE);
        if (s.getBlock() instanceof GenericVerticalSlabBlock) return s.get(GenericVerticalSlabBlock.DOUBLE);
        return false;
    }

    private static Direction getVerticalFacing(BlockState s) {
        return s.get(GenericVerticalSlabBlock.FACING);
    }

    // ===================== 横半砖 =====================

    private static boolean placeHorizontal(World world, BlockPos pos, Block slabBlock, SlabType type,
                                           Direction side, ItemPlacementContext ctx) {
        if (tryMergeHorizontal(world, pos, slabBlock, side, ctx, null)) return true;

        if (sameTypeSlabAt(world, pos, slabBlock) || sameTypeSlabAt(world, pos.offset(side.getOpposite()), slabBlock)) {
            return false;
        }

        BlockState existing = world.getBlockState(pos);
        BlockState slabState = slabBlock.getDefaultState().with(SlabBlock.TYPE, type);

        BlockPos placePos = pos;
        if (!existing.canReplace(ctx)) {
            BlockPos adj = pos.offset(side);
            if (world.getBlockState(adj).canReplace(ctx)) {
                placePos = adj;
            } else {
                return false;
            }
        }
        world.setBlockState(placePos, slabState, Block.NOTIFY_ALL);
        return true;
    }

    private static boolean sameTypeSlabAt(World world, BlockPos pos, Block slabBlock) {
        BlockState s = world.getBlockState(pos);
        return s.getBlock() == slabBlock
                && s.contains(SlabBlock.TYPE)
                && s.get(SlabBlock.TYPE) != SlabType.DOUBLE;
    }

    private static boolean tryMergeHorizontal(World world, BlockPos pos, Block newSlab, Direction side,
                                              ItemPlacementContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (mergeAt(world, pos, newSlab, ctx)) {
            if (cir != null) cancel(cir);
            return true;
        }
        BlockPos other = pos.offset(side.getOpposite());
        if (!other.equals(pos) && mergeAt(world, other, newSlab, ctx)) {
            if (cir != null) cancel(cir);
            return true;
        }
        return false;
    }

    private static boolean mergeAt(World world, BlockPos pos, Block newSlab, ItemPlacementContext ctx) {
        BlockState existing = world.getBlockState(pos);
        if (!(existing.getBlock() instanceof SlabBlock)) return false;
        SlabType existingType = existing.get(SlabBlock.TYPE);
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
        boolean waterlogged = existing.contains(Properties.WATERLOGGED)
                && existing.get(Properties.WATERLOGGED);
        BlockState mergedState = ModBlocks.MERGED_SLAB.getDefaultState()
                .with(MergedSlabBlock.ORIENTATION, MergedSlabBlock.Orientation.HORIZONTAL)
                .with(MergedSlabBlock.FACING, Direction.NORTH)
                .with(MergedSlabBlock.WATERLOGGED, waterlogged);
        world.setBlockState(pos, mergedState, Block.NOTIFY_ALL);
        if (world.getBlockEntity(pos) instanceof MergedSlabEntity be) {
            be.setSlabA(slabA);
            be.setSlabB(slabB);
            be.sync();
        }
        return true;
    }

    // ===================== AUTO_V 象限判定 =====================

    private static Direction quadrantFacing(ItemPlacementContext ctx, Direction playerFacing, Direction side) {
        BlockPos clicked = ctx.getBlockPos().offset(side.getOpposite());
        Vec3d hit = ctx.getHitPos();
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
            facing = sideR >= 0 ? playerFacing.rotateYClockwise() : playerFacing.rotateYCounterclockwise();
        }
        return facing;
    }
}
