package com.betterslab.mixin;

import com.betterslab.config.BetterSlabConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SlabBlock.class)
public class SlabBlockMixin {

    /**
     * 完美放置：让半砖的侧面也被视为完整面，
     * 这样其他方块可以贴着半砖的侧面放置。
     *
     * <p>原版中下半砖的侧面只有半高，isFaceFullSquare 返回 false，
     * 导致方块无法贴着半砖侧面放置。这里覆写 getOcclusionShape
     * 让半砖返回完整立方体形状。</p>
     *
     * <p>当 perfectPlacement 被禁用时，返回与原版一致的形状。</p>
     */
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        if (BetterSlabConfig.get().perfectPlacement) {
            return Shapes.block();
        }
        // 禁用时复现原版行为
        return switch (state.getValue(SlabBlock.TYPE)) {
            case BOTTOM -> Block.box(0, 0, 0, 16, 8, 16);
            case TOP -> Block.box(0, 8, 0, 16, 16, 16);
            default -> Shapes.block();
        };
    }
}
