package com.betterslab.block;

import com.betterslab.block.entity.MergedSlabEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * 合并半砖方块：用于不同种类半砖合并到同一格。
 *
 * <p>由 {@link MergedSlabEntity} 记录两种来源半砖，由 BER 渲染两种半砖外观。</p>
 *
 * <p>约定：</p>
 * <ul>
 *   <li>HORIZONTAL：slabA 在下半（y:0-8），slabB 在上半（y:8-16），FACING 不使用</li>
 *   <li>VERTICAL：slabA 在 FACING 方向的半边，slabB 在 FACING.getOpposite() 方向的半边</li>
 * </ul>
 */
public class MergedSlabBlock extends BlockWithEntity implements Waterloggable {
    public static final EnumProperty<Orientation> ORIENTATION = EnumProperty.of("orientation", Orientation.class);
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    public enum Orientation implements StringIdentifiable {
        HORIZONTAL("horizontal"),
        VERTICAL("vertical");

        private final String name;

        Orientation(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return this.name;
        }
    }

    public MergedSlabBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState()
                .with(ORIENTATION, Orientation.HORIZONTAL)
                .with(FACING, Direction.NORTH)
                .with(WATERLOGGED, false));
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MergedSlabEntity(pos, state);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ORIENTATION, FACING, WATERLOGGED);
    }

    /** 合并后是完整方块，碰撞箱始终为 fullCube。 */
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.fullCube();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.fullCube();
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

    /** 由 BER 渲染，返回 ENTITYBLOCK_ANIMATED。 */
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    /** 掉落由 BreakBlockHandler 处理，这里仅返回 super。 */
    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        super.onBreak(world, pos, state, player);
    }
}
