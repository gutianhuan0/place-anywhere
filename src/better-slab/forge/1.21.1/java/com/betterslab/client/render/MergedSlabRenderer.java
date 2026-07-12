package com.betterslab.client.render;

import com.betterslab.block.MergedSlabBlock;
import com.betterslab.block.entity.MergedSlabEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * 合并半砖渲染器：渲染两种来源半砖。
 *
 * <ul>
 *   <li>HORIZONTAL：slabA 渲染为下半砖，slabB 渲染为上半砖</li>
 *   <li>VERTICAL：slabA 渲染到 FACING 方向，slabB 渲染到 FACING.getOpposite() 方向
 *       （复用 GenericVerticalSlabRenderer 的旋转逻辑）</li>
 * </ul>
 */
public class MergedSlabRenderer implements BlockEntityRenderer<MergedSlabEntity> {

    @Override
    public void render(MergedSlabEntity entity, float partialTick, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light, int overlay) {
        BlockState state = entity.getBlockState();
        MergedSlabBlock.Orientation orientation = state.getValue(MergedSlabBlock.ORIENTATION);
        Direction facing = state.getValue(MergedSlabBlock.FACING);
        Block slabA = entity.getSlabA();
        Block slabB = entity.getSlabB();

        Minecraft client = Minecraft.getInstance();
        BlockRenderDispatcher brm = client.getBlockRenderer();

        if (orientation == MergedSlabBlock.Orientation.HORIZONTAL) {
            // slabA 下半砖（y:0-8）
            BlockState stateA = getSlabState(slabA, SlabType.BOTTOM);
            renderBlock(brm, stateA, matrices, vertexConsumers, light, overlay);
            // slabB 上半砖（y:8-16），TOP 模型已在 y:8-16，无需平移
            BlockState stateB = getSlabState(slabB, SlabType.TOP);
            renderBlock(brm, stateB, matrices, vertexConsumers, light, overlay);
        } else {
            // VERTICAL：把下半砖模型旋转到对应方向
            BlockState stateA = getSlabState(slabA, SlabType.BOTTOM);
            BlockState stateB = getSlabState(slabB, SlabType.BOTTOM);
            renderVertical(brm, stateA, facing, matrices, vertexConsumers, light, overlay);
            renderVertical(brm, stateB, facing.getOpposite(), matrices, vertexConsumers, light, overlay);
        }
    }

    /** 获取半砖的指定层位状态；非 SlabBlock 时返回默认状态。 */
    private BlockState getSlabState(Block slab, SlabType type) {
        BlockState defaultState = slab.defaultBlockState();
        if (defaultState.hasProperty(SlabBlock.TYPE)) {
            return defaultState.setValue(SlabBlock.TYPE, type);
        }
        return defaultState;
    }

    /** 无变换渲染（HORIZONTAL 用）。 */
    private void renderBlock(BlockRenderDispatcher brm, BlockState state,
                             PoseStack matrices, MultiBufferSource vertexConsumers,
                             int light, int overlay) {
        matrices.pushPose();
        try {
            brm.renderSingleBlock(state, matrices, vertexConsumers, light, overlay);
        } catch (Throwable ignored) {
            // 渲染失败时静默，避免崩溃
        }
        matrices.popPose();
    }

    /** 旋转渲染（VERTICAL 用）。 */
    private void renderVertical(BlockRenderDispatcher brm, BlockState state, Direction facing,
                                PoseStack matrices, MultiBufferSource vertexConsumers,
                                int light, int overlay) {
        matrices.pushPose();
        applyVerticalTransform(matrices, facing);
        try {
            brm.renderSingleBlock(state, matrices, vertexConsumers, light, overlay);
        } catch (Throwable ignored) {
            // 渲染失败时静默，避免崩溃
        }
        matrices.popPose();
    }

    /**
     * 将下半砖模型 (y:0-8) 映射到指定 FACING 的竖半边。
     * 顶点先旋转再平移（translate 在前、multiply 在后，使得 M=T*R，顶点先 R 后 T）。
     * 与 {@link GenericVerticalSlabRenderer#applyVerticalTransform} 一致。
     */
    private void applyVerticalTransform(PoseStack matrices, Direction facing) {
        switch (facing) {
            case NORTH -> {
                matrices.translate(0, 1, 0);
                matrices.mulPose(Axis.XP.rotationDegrees(90));
            }
            case SOUTH -> {
                matrices.translate(0, 0, 1);
                matrices.mulPose(Axis.XP.rotationDegrees(-90));
            }
            case EAST -> {
                matrices.translate(1, 0, 0);
                matrices.mulPose(Axis.ZP.rotationDegrees(90));
            }
            case WEST -> {
                matrices.translate(0, 1, 0);
                matrices.mulPose(Axis.ZP.rotationDegrees(-90));
            }
            default -> {}
        }
    }

    @Override
    public boolean shouldRenderOffScreen(MergedSlabEntity entity) {
        return true;
    }
}
