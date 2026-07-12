package com.betterslab.client.render;

import com.betterslab.block.GenericVerticalSlabBlock;
import com.betterslab.block.entity.GenericVerticalSlabEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 通用竖半砖渲染器：把来源半砖的下半模型旋转到对应方向渲染。
 * DOUBLE 时同时渲染两个互补方向的半砖。
 */
public class GenericVerticalSlabRenderer implements BlockEntityRenderer<GenericVerticalSlabEntity> {

    @Override
    public void render(GenericVerticalSlabEntity entity, float partialTick, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light, int overlay) {
        BlockState state = entity.getBlockState();
        Direction facing = state.getValue(GenericVerticalSlabBlock.FACING);
        boolean dbl = state.getValue(GenericVerticalSlabBlock.DOUBLE);
        BlockState sourceState = entity.getSourceSlabState();

        Minecraft client = Minecraft.getInstance();
        BlockRenderDispatcher brm = client.getBlockRenderer();

        // 渲染主半边
        renderHalf(brm, sourceState, facing, matrices, vertexConsumers, light, overlay);

        if (dbl) {
            // 渲染对边
            renderHalf(brm, sourceState, facing.getOpposite(), matrices, vertexConsumers, light, overlay);
        }
    }

    /**
     * 把“下半砖模型”(y:0-8) 旋转到指定 FACING 占据对应半边并渲染。
     */
    private void renderHalf(BlockRenderDispatcher brm, BlockState sourceState, Direction facing,
                            PoseStack matrices, MultiBufferSource vertexConsumers,
                            int light, int overlay) {
        matrices.pushPose();
        applyVerticalTransform(matrices, facing);
        try {
            brm.renderSingleBlock(sourceState, matrices, vertexConsumers, light, overlay);
        } catch (Throwable ignored) {
            // 渲染失败时静默，避免崩溃
        }
        matrices.popPose();
    }

    /**
     * 将下半砖模型 (y:0-8) 映射到指定 FACING 的竖半边。
     * 顶点先旋转再平移（translate 在前、multiply 在后，使得 M=T*R，顶点先 R 后 T）。
     */
    private void applyVerticalTransform(PoseStack matrices, Direction facing) {
        switch (facing) {
            case NORTH -> {
                matrices.translate(0, 1, 0);
                matrices.mulPose(Vector3f.XP.rotationDegrees(90));
            }
            case SOUTH -> {
                matrices.translate(0, 0, 1);
                matrices.mulPose(Vector3f.XP.rotationDegrees(-90));
            }
            case EAST -> {
                matrices.translate(1, 0, 0);
                matrices.mulPose(Vector3f.ZP.rotationDegrees(90));
            }
            case WEST -> {
                matrices.translate(0, 1, 0);
                matrices.mulPose(Vector3f.ZP.rotationDegrees(-90));
            }
            default -> {}
        }
    }

    @Override
    public boolean shouldRenderOffScreen(GenericVerticalSlabEntity entity) {
        return true;
    }
}
