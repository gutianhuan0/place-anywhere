package com.betterslab.block;

import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

public class VerticalSlabBlock extends Block implements Waterloggable {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    /** 与 {@link GenericVerticalSlabBlock#DOUBLE} 共享同一实例，以便通用代码统一操作两种方块的状态。 */
    public static final BooleanProperty DOUBLE = GenericVerticalSlabBlock.DOUBLE;

    // 碰撞箱定义 - 每个方向的半砖占半个方块
    // north = z:0-8, south = z:8-16, east = x:8-16, west = x:0-8
    protected static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 8.0);
    protected static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 8.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape EAST_SHAPE  = Block.createCuboidShape(8.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape WEST_SHAPE  = Block.createCuboidShape(0.0, 0.0, 0.0, 8.0, 16.0, 16.0);

    public VerticalSlabBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(WATERLOGGED, false)
                .with(DOUBLE, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED, DOUBLE);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (state.get(DOUBLE)) return VoxelShapes.fullCube();
        return switch (state.get(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getSidesShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.fullCube();
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos belowPos = pos.down();
        BlockState belowState = world.getBlockState(belowPos);
        return Block.isFaceFullSquare(belowState.getCollisionShape(world, belowPos), Direction.UP)
                || belowState.getBlock() instanceof SlabBlock
                || belowState.getBlock() instanceof VerticalSlabBlock;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        WorldView world = ctx.getWorld();
        FluidState fluidState = world.getFluidState(pos);

        Direction facing = ctx.getSide().getOpposite();
        if (ctx.getSide().getAxis() == Direction.Axis.Y) {
            facing = ctx.getPlayer().getHorizontalFacing();
        }

        return getDefaultState()
                .with(FACING, facing)
                .with(DOUBLE, false)
                .with(WATERLOGGED, fluidState.isOf(Fluids.WATER) && fluidState.isStill());
    }

    /**
     * 检查新放置的方向是否与现有方向互补（可以合并为DOUBLE）。
     * 互补方向：NORTH-SOUTH, EAST-WEST
     */
    public static boolean isOppositeFacing(Direction existing, Direction placing) {
        return existing.getOpposite() == placing;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                  WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(WATERLOGGED)) {
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }
}
