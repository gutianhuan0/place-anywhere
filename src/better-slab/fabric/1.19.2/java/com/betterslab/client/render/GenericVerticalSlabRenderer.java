package com.betterslab.client.render;

import com.betterslab.block.GenericVerticalSlabBlock;
import com.betterslab.block.entity.GenericVerticalSlabEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3f;
import net.minecraft.util.math.BlockPos;

/**
 * 通用竖半砖渲染器：把来源半砖的下半模型旋转到对应方向渲染。
 * DOUBLE 时同时渲染两个互补方向的半砖。
 */
public class GenericVerticalSlabRenderer implements BlockEntityRenderer<GenericVerticalSlabEntity> {

    public static void register() {
        BlockEntityRendererFactories.register(
                com.betterslab.block.entity.ModBlockEntities.GENERIC_VERTICAL_SLAB_ENTITY,
                ctx -> new GenericVerticalSlabRenderer()
        );
    }

    @Override
    public void render(GenericVerticalSlabEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        Direction facing = state.get(GenericVerticalSlabBlock.FACING);
        boolean dbl = state.get(GenericVerticalSlabBlock.DOUBLE);
        BlockState sourceState = entity.getSourceSlabState();

        MinecraftClient client = MinecraftClient.getInstance();
        BlockRenderManager brm = client.getBlockRenderManager();

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
    private void renderHalf(BlockRenderManager brm, BlockState sourceState, Direction facing,
                            MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                            int light, int overlay) {
        matrices.push();
        applyVerticalTransform(matrices, facing);
        try {
            brm.renderBlockAsEntity(sourceState, matrices, vertexConsumers, light, overlay);
        } catch (Throwable ignored) {
            // 渲染失败时静默，避免崩溃
        }
        matrices.pop();
    }

    /**
     * 将下半砖模型 (y:0-8) 映射到指定 FACING 的竖半边。
     * 顶点先旋转再平移（translate 在前、multiply 在后，使得 M=T*R，顶点先 R 后 T）。
     */
    private void applyVerticalTransform(MatrixStack matrices, Direction facing) {
        switch (facing) {
            case NORTH -> {
                matrices.translate(0, 1, 0);
                matrices.multiply(new Quaternion(new Vec3f(1.0F, 0.0F, 0.0F), 90, true));
            }
            case SOUTH -> {
                matrices.translate(0, 0, 1);
                matrices.multiply(new Quaternion(new Vec3f(1.0F, 0.0F, 0.0F), -90, true));
            }
            case EAST -> {
                matrices.translate(1, 0, 0);
                matrices.multiply(new Quaternion(new Vec3f(0.0F, 0.0F, 1.0F), 90, true));
            }
            case WEST -> {
                matrices.translate(0, 1, 0);
                matrices.multiply(new Quaternion(new Vec3f(0.0F, 0.0F, 1.0F), -90, true));
            }
            default -> {}
        }
    }

    @Override
    public boolean rendersOutsideBoundingBox(GenericVerticalSlabEntity entity) {
        return true;
    }
}
