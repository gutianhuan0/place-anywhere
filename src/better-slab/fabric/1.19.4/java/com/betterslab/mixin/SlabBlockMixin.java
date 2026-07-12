package com.betterslab.mixin;

import com.betterslab.config.BetterSlabConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SlabBlock.class)
public class SlabBlockMixin {

    /**
     * 完美放置：让半砖的侧面也被视为完整面，
     * 这样其他方块可以贴着半砖的侧面放置。
     *
     * <p>原版中下半砖的侧面只有半高，isFaceFullSquare 返回 false，
     * 导致方块无法贴着半砖侧面放置。这里覆写 getSidesShape
     * 让半砖返回完整立方体形状。</p>
     *
     * <p>当 perfectPlacement 被禁用时，返回与原版一致的形状。</p>
     */
    public VoxelShape getSidesShape(BlockState state, BlockView world, BlockPos pos) {
        if (BetterSlabConfig.get().perfectPlacement) {
            return VoxelShapes.fullCube();
        }
        // 禁用时复现原版行为
        return switch (state.get(SlabBlock.TYPE)) {
            case BOTTOM -> Block.createCuboidShape(0, 0, 0, 16, 8, 16);
            case TOP -> Block.createCuboidShape(0, 8, 0, 16, 16, 16);
            default -> VoxelShapes.fullCube();
        };
    }
}
