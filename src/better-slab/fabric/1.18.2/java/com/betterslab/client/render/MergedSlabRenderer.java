package com.betterslab.client.render;

import com.betterslab.block.MergedSlabBlock;
import com.betterslab.block.entity.MergedSlabEntity;
import com.betterslab.block.entity.ModBlockEntities;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3f;

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

    public static void register() {
        BlockEntityRendererRegistry.register(
                ModBlockEntities.MERGED_SLAB_ENTITY,
                ctx -> new MergedSlabRenderer()
        );
    }

    @Override
    public void render(MergedSlabEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        MergedSlabBlock.Orientation orientation = state.get(MergedSlabBlock.ORIENTATION);
        Direction facing = state.get(MergedSlabBlock.FACING);
        Block slabA = entity.getSlabA();
        Block slabB = entity.getSlabB();

        MinecraftClient client = MinecraftClient.getInstance();
        BlockRenderManager brm = client.getBlockRenderManager();

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
        BlockState defaultState = slab.getDefaultState();
        if (defaultState.contains(SlabBlock.TYPE)) {
            return defaultState.with(SlabBlock.TYPE, type);
        }
        return defaultState;
    }

    /** 无变换渲染（HORIZONTAL 用）。 */
    private void renderBlock(BlockRenderManager brm, BlockState state,
                             MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                             int light, int overlay) {
        matrices.push();
        try {
            brm.renderBlockAsEntity(state, matrices, vertexConsumers, light, overlay);
        } catch (Throwable ignored) {
            // 渲染失败时静默，避免崩溃
        }
        matrices.pop();
    }

    /** 旋转渲染（VERTICAL 用）。 */
    private void renderVertical(BlockRenderManager brm, BlockState state, Direction facing,
                                MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                int light, int overlay) {
        matrices.push();
        applyVerticalTransform(matrices, facing);
        try {
            brm.renderBlockAsEntity(state, matrices, vertexConsumers, light, overlay);
        } catch (Throwable ignored) {
            // 渲染失败时静默，避免崩溃
        }
        matrices.pop();
    }

    /**
     * 将下半砖模型 (y:0-8) 映射到指定 FACING 的竖半边。
     * 顶点先旋转再平移（translate 在前、multiply 在后，使得 M=T*R，顶点先 R 后 T）。
     * 与 {@link GenericVerticalSlabRenderer#applyVerticalTransform} 一致。
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
    public boolean rendersOutsideBoundingBox(MergedSlabEntity entity) {
        return true;
    }
}
