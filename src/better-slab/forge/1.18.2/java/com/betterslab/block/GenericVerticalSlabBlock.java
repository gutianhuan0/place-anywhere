package com.betterslab.block;

import com.betterslab.block.entity.GenericVerticalSlabEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import net.minecraft.world.entity.player.Player;

/**
 * 通用竖半砖方块：用于没有专属竖半砖的模组半砖。
 * 外观由 {@link GenericVerticalSlabEntity} 记录的来源半砖状态决定，由 BER 渲染。
 */
public class GenericVerticalSlabBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty DOUBLE = BooleanProperty.create("double");

    protected static final VoxelShape NORTH_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 8.0);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(0.0, 0.0, 8.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape EAST_SHAPE  = Block.box(8.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape WEST_SHAPE  = Block.box(0.0, 0.0, 0.0, 8.0, 16.0, 16.0);

    public GenericVerticalSlabBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false)
                .setValue(DOUBLE, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GenericVerticalSlabEntity(pos, state);
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
                || belowState.getBlock() instanceof VerticalSlabBlock
                || belowState.getBlock() instanceof GenericVerticalSlabBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getPlayer().getDirection());
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

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** 破坏时根据是否 DOUBLE 掉落来源半砖（创造模式不掉落，由 BreakBlockHandler 处理）。 */
    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide && !player.isCreative() && world.getBlockEntity(pos) instanceof GenericVerticalSlabEntity be) {
            Block source = be.getSourceSlab();
            if (source != null) {
                Block.popResource(world, pos, new ItemStack(source.asItem()));
                if (state.getValue(DOUBLE)) {
                    Block.popResource(world, pos, new ItemStack(source.asItem()));
                }
            }
        }
        super.playerWillDestroy(world, pos, state, player);
    }
}
