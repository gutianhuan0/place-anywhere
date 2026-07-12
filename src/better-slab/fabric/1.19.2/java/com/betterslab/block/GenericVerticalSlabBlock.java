package com.betterslab.block;

import com.betterslab.block.entity.GenericVerticalSlabEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * 通用竖半砖方块：用于没有专属竖半砖的模组半砖。
 * 外观由 {@link GenericVerticalSlabEntity} 记录的来源半砖状态决定，由 BER 渲染。
 */
public class GenericVerticalSlabBlock extends BlockWithEntity implements Waterloggable {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    public static final BooleanProperty DOUBLE = BooleanProperty.of("double");

    protected static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 8.0);
    protected static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 8.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape EAST_SHAPE  = Block.createCuboidShape(8.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape WEST_SHAPE  = Block.createCuboidShape(0.0, 0.0, 0.0, 8.0, 16.0, 16.0);

    public GenericVerticalSlabBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(WATERLOGGED, false)
                .with(DOUBLE, false));
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new GenericVerticalSlabEntity(pos, state);
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
                || belowState.getBlock() instanceof VerticalSlabBlock
                || belowState.getBlock() instanceof GenericVerticalSlabBlock;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getPlayer().getHorizontalFacing());
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                 WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(WATERLOGGED)) {
            world.createAndScheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    /** 破坏时根据是否 DOUBLE 掉落来源半砖（创造模式不掉落，由 BreakBlockHandler 处理）。 */
    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && !player.isCreative() && world.getBlockEntity(pos) instanceof GenericVerticalSlabEntity be) {
            Block source = be.getSourceSlab();
            if (source != null) {
                dropStack(world, pos, new ItemStack(source.asItem()));
                if (state.get(DOUBLE)) {
                    dropStack(world, pos, new ItemStack(source.asItem()));
                }
            }
        }
        super.onBreak(world, pos, state, player);
    }
}
