package com.betterslab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class VerticalSlabBlock extends Block implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** 与 {@link GenericVerticalSlabBlock#DOUBLE} 共享同一实例，以便通用代码统一操作两种方块的状态。 */
    public static final BooleanProperty DOUBLE = GenericVerticalSlabBlock.DOUBLE;

    // 碰撞箱定义 - 每个方向的半砖占半个方块
    // north = z:0-8, south = z:8-16, east = x:8-16, west = x:0-8
    protected static final VoxelShape NORTH_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 8.0);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(0.0, 0.0, 8.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape EAST_SHAPE  = Block.box(8.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape WEST_SHAPE  = Block.box(0.0, 0.0, 0.0, 8.0, 16.0, 16.0);

    public VerticalSlabBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false)
                .setValue(DOUBLE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED, DOUBLE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (state.getValue(DOUBLE)) return Shapes.block();
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return getShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        BlockPos belowPos = pos.below();
        BlockState belowState = world.getBlockState(belowPos);
        return Block.isFaceFull(belowState.getCollisionShape(world, belowPos), Direction.UP)
                || belowState.getBlock() instanceof SlabBlock
                || belowState.getBlock() instanceof VerticalSlabBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        LevelReader world = ctx.getLevel();
        FluidState fluidState = world.getFluidState(pos);

        Direction facing = ctx.getClickedFace().getOpposite();
        if (ctx.getClickedFace().getAxis() == Direction.Axis.Y) {
            facing = ctx.getPlayer().getDirection();
        }

        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(DOUBLE, false)
                .setValue(WATERLOGGED, fluidState.is(Fluids.WATER) && fluidState.isSource());
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
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }
}
