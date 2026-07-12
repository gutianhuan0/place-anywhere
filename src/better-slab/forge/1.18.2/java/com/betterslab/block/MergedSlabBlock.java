package com.betterslab.block;

import com.betterslab.block.entity.MergedSlabEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.StringRepresentable;

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
public class MergedSlabBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final EnumProperty<Orientation> ORIENTATION = EnumProperty.create("orientation", Orientation.class);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public enum Orientation implements StringRepresentable {
        HORIZONTAL("horizontal"),
        VERTICAL("vertical");

        private final String name;

        Orientation(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public MergedSlabBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(ORIENTATION, Orientation.HORIZONTAL)
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    public MergedSlabEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MergedSlabEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ORIENTATION, FACING, WATERLOGGED);
    }

    /** 合并后是完整方块，碰撞箱始终为 fullCube。 */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
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

    /** 由 BER 渲染，返回 ENTITYBLOCK_ANIMATED。 */
    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** 掉落由 BreakBlockHandler 处理，这里仅返回 super。 */
    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        super.playerWillDestroy(world, pos, state, player);
    }
}
