package com.placeanywhere.fp;

import com.placeanywhere.core.FreeBlockInteractPayload;
import com.placeanywhere.core.FreeBlocks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class FreePlacementMode {
    private static final double REACH = 6.0;
    private static final float STEP_NORMAL = 15.0f;
    private static final float STEP_FINE = 5.0f;

    private enum Axis { YAW, PITCH, ROLL }

    private static KeyBinding toggleKey;   
    private static KeyBinding axisKey;     
    private static KeyBinding upKey;       
    private static KeyBinding downKey;     
    private static KeyBinding resetKey;    

    private static boolean active = false;
    private static Axis currentAxis = Axis.YAW;
    private static float pitchDeg = 0f, yawDeg = 0f, rollDeg = 0f;

    private static float yOffset = 0f;

    private static boolean mineHandled = false;

    private static final float[] CUBE_X = {0,1,0,1,0,1,0,1};
    private static final float[] CUBE_Y = {0,0,1,1,0,0,1,1};
    private static final float[] CUBE_Z = {0,0,0,0,1,1,1,1};

    private static final int[][] EDGES = {
        {0,1},{2,3},{4,5},{6,7},
        {0,2},{1,3},{4,6},{5,7},
        {0,4},{1,5},{2,6},{3,7}
    };

    private FreePlacementMode() {}

    public static boolean isActive() { return active; }
    public static boolean isMineHandled() { return mineHandled; }
    public static void setMineHandled() { mineHandled = true; }
    public static void resetMineHandled() { mineHandled = false; }

    public static void register() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.freeplacement.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.freeplacement"));
        axisKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.freeplacement.axis", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.freeplacement"));
        upKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.freeplacement.up", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, "key.categories.freeplacement"));
        downKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.freeplacement.down", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, "key.categories.freeplacement"));
        resetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.freeplacement.reset", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.freeplacement"));

        ClientTickEvents.END_CLIENT_TICK.register(FreePlacementMode::onTick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(FreePlacementMode::renderPreview);
    }

    private static void onTick(MinecraftClient client) {
        if (client.player == null) return;

        while (toggleKey.wasPressed()) {
            active = !active;
            String msg = active
                    ? "§a[自由放置] 已开启 §7(V切换轴 滚轮调角度 Shift微调)"
                    : "§c[自由放置] 已关闭";
            client.player.sendMessage(Text.literal(msg), true);
        }

        while (axisKey.wasPressed()) {
            currentAxis = switch (currentAxis) {
                case YAW -> Axis.PITCH;
                case PITCH -> Axis.ROLL;
                case ROLL -> Axis.YAW;
            };
            String axisName = switch (currentAxis) {
                case YAW -> "§b偏航(Yaw/Y轴)";
                case PITCH -> "§b俯仰(Pitch/X轴)";
                case ROLL -> "§b翻滚(Roll/Z轴)";
            };
            client.player.sendMessage(Text.literal("§e旋转轴: " + axisName), true);
        }

        while (upKey.wasPressed()) {
            yOffset += 0.5f;
            client.player.sendMessage(Text.literal("§eY偏移: " + yOffset), true);
        }
        while (downKey.wasPressed()) {
            yOffset -= 0.5f;
            client.player.sendMessage(Text.literal("§eY偏移: " + yOffset), true);
        }

        while (resetKey.wasPressed()) {
            resetTransform();
            client.player.sendMessage(Text.literal("§e已重置旋转和Y偏移"), true);
        }
    }

    public static void resetTransform() {
        pitchDeg = 0f;
        yawDeg = 0f;
        rollDeg = 0f;
        yOffset = 0f;
    }

    public static void onScroll(double vertical) {
        if (!active) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        float step = (client.player.isSneaking() ? STEP_FINE : STEP_NORMAL);
        float delta = vertical > 0 ? -step : step;
        switch (currentAxis) {
            case YAW -> yawDeg += delta;
            case PITCH -> pitchDeg += delta;
            case ROLL -> rollDeg += delta;
        }
    }

    public static Quaternionf currentQuaternion() {
        float yaw = (float) Math.toRadians(yawDeg);
        float pitch = (float) Math.toRadians(pitchDeg);
        float roll = (float) Math.toRadians(rollDeg);
        return new Quaternionf().rotationZYX(roll, yaw, pitch);
    }

    private static Vec3d getPlacementPos(MinecraftClient client, Quaternionf q) {
        Vec3d center;
        Vec3d eye = client.player.getEyePos();
        Vec3d dir = client.player.getRotationVec(1.0F);
        Vec3d end = eye.add(dir.multiply(REACH));

        var freeHit = FreeBlocks.raycast(client.world, eye, end);
        if (freeHit.isPresent()) {
            var fb = freeHit.get();

            org.joml.Quaternionf fbQ = new org.joml.Quaternionf(
                    fb.qx(), fb.qy(), fb.qz(), fb.qw()).normalize();
            org.joml.Vector3f nDir = new org.joml.Vector3f(
                    fb.side().getOffsetX(),
                    fb.side().getOffsetY(),
                    fb.side().getOffsetZ());
            fbQ.transform(nDir);
            center = fb.point().add(nDir.x * 0.5, nDir.y * 0.5, nDir.z * 0.5);
        } else if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {

            BlockHitResult bhr = (BlockHitResult) client.crosshairTarget;
            Vec3d hitPos = bhr.getPos();
            Direction side = bhr.getSide();
            center = hitPos.add(
                    side.getOffsetX() * 0.5,
                    side.getOffsetY() * 0.5,
                    side.getOffsetZ() * 0.5);
        } else {

            center = end;
        }

        center = center.add(0, yOffset, 0);

        double x = center.x - 0.5;
        double y = center.y - 0.5;
        double z = center.z - 0.5;

        double minY = client.world.getBottomY() + 1;
        if (y < minY) y = minY;

        return new Vec3d(x, y, z);
    }

    private static boolean isOverlapping(MinecraftClient client, Vec3d pos, Quaternionf q) {
        org.joml.Vector3f axisX = new org.joml.Vector3f(1, 0, 0);
        org.joml.Vector3f axisY = new org.joml.Vector3f(0, 1, 0);
        org.joml.Vector3f axisZ = new org.joml.Vector3f(0, 0, 1);
        q.transform(axisX);
        q.transform(axisY);
        q.transform(axisZ);
        org.joml.Vector3f center = new org.joml.Vector3f(
                (float) (pos.x + 0.5), (float) (pos.y + 0.5), (float) (pos.z + 0.5));

        Box searchBox = new Box(pos.x - 1, pos.y - 1, pos.z - 1, pos.x + 2, pos.y + 2, pos.z + 2);
        boolean[] overlap = { false };
        FreeBlocks.forEachPlaced(client.world, searchBox, fb -> {
            if (overlap[0]) return;
            org.joml.Quaternionf fq = new org.joml.Quaternionf(fb.qx(), fb.qy(), fb.qz(), fb.qw());
            fq.normalize();
            org.joml.Vector3f fAxisX = new org.joml.Vector3f(1, 0, 0);
            org.joml.Vector3f fAxisY = new org.joml.Vector3f(0, 1, 0);
            org.joml.Vector3f fAxisZ = new org.joml.Vector3f(0, 0, 1);
            fq.transform(fAxisX);
            fq.transform(fAxisY);
            fq.transform(fAxisZ);
            org.joml.Vector3f fCenter = new org.joml.Vector3f(
                    (float) (fb.pos().x() + 0.5), (float) (fb.pos().y() + 0.5), (float) (fb.pos().z() + 0.5));

            org.joml.Vector3f[] myEdges = { axisX, axisY, axisZ };
            org.joml.Vector3f[] fbEdges = { fAxisX, fAxisY, fAxisZ };
            java.util.List<org.joml.Vector3f> axesList = new java.util.ArrayList<>();
            axesList.add(axisX); axesList.add(axisY); axesList.add(axisZ);
            axesList.add(fAxisX); axesList.add(fAxisY); axesList.add(fAxisZ);
            for (org.joml.Vector3f me : myEdges) {
                for (org.joml.Vector3f fe : fbEdges) {
                    org.joml.Vector3f cross = new org.joml.Vector3f();
                    me.cross(fe, cross);
                    if (cross.lengthSquared() > 1e-12f) axesList.add(cross);
                }
            }
            boolean separated = false;
            for (org.joml.Vector3f axis : axesList) {
                float len = axis.length();
                if (len < 1e-6f) continue;
                float ax = axis.x / len, ay = axis.y / len, az = axis.z / len;
                float r1 = 0.5f * Math.abs(axisX.x * ax + axisX.y * ay + axisX.z * az)
                         + 0.5f * Math.abs(axisY.x * ax + axisY.y * ay + axisY.z * az)
                         + 0.5f * Math.abs(axisZ.x * ax + axisZ.y * ay + axisZ.z * az);
                float r2 = 0.5f * Math.abs(fAxisX.x * ax + fAxisX.y * ay + fAxisX.z * az)
                         + 0.5f * Math.abs(fAxisY.x * ax + fAxisY.y * ay + fAxisY.z * az)
                         + 0.5f * Math.abs(fAxisZ.x * ax + fAxisZ.y * ay + fAxisZ.z * az);
                float dist = Math.abs((center.x - fCenter.x) * ax + (center.y - fCenter.y) * ay + (center.z - fCenter.z) * az);
                if (dist >= r1 + r2) { separated = true; break; }
            }
            if (!separated) overlap[0] = true;
        });
        if (overlap[0]) return true;

        Box obbBoundingBox = FreeBlocks.rotateBoxAABB(
                new Box(0, 0, 0, 1, 1, 1),
                new com.placeanywhere.core.DecimalBlockPos(pos.x, pos.y, pos.z),
                q.x, q.y, q.z, q.w);
        BlockPos minBp = BlockPos.ofFloored(obbBoundingBox.minX, obbBoundingBox.minY, obbBoundingBox.minZ);
        BlockPos maxBp = BlockPos.ofFloored(obbBoundingBox.maxX, obbBoundingBox.maxY, obbBoundingBox.maxZ);
        for (BlockPos bp : BlockPos.iterate(minBp, maxBp)) {
            BlockState vs = client.world.getBlockState(bp);
            if (vs.isAir()) continue;
            VoxelShape vsShape = vs.getCollisionShape(client.world, bp);
            if (vsShape.isEmpty()) continue;
            Box vsBox = vsShape.getBoundingBox();
            if (vsBox == null) continue;
            Box worldVsBox = vsBox.offset(bp.getX(), bp.getY(), bp.getZ());

            org.joml.Vector3f vCenter = new org.joml.Vector3f(
                    (float) ((worldVsBox.minX + worldVsBox.maxX) * 0.5),
                    (float) ((worldVsBox.minY + worldVsBox.maxY) * 0.5),
                    (float) ((worldVsBox.minZ + worldVsBox.maxZ) * 0.5));
            float vHalfX = (float) ((worldVsBox.maxX - worldVsBox.minX) * 0.5);
            float vHalfY = (float) ((worldVsBox.maxY - worldVsBox.minY) * 0.5);
            float vHalfZ = (float) ((worldVsBox.maxZ - worldVsBox.minZ) * 0.5);

            org.joml.Vector3f[] obbEdges = { axisX, axisY, axisZ };
            org.joml.Vector3f[] aabbEdges = {
                new org.joml.Vector3f(1, 0, 0), new org.joml.Vector3f(0, 1, 0), new org.joml.Vector3f(0, 0, 1)
            };
            java.util.List<org.joml.Vector3f> satAxes = new java.util.ArrayList<>();
            satAxes.add(axisX); satAxes.add(axisY); satAxes.add(axisZ);
            satAxes.add(new org.joml.Vector3f(1, 0, 0));
            satAxes.add(new org.joml.Vector3f(0, 1, 0));
            satAxes.add(new org.joml.Vector3f(0, 0, 1));
            for (org.joml.Vector3f oe : obbEdges) {
                for (org.joml.Vector3f ae : aabbEdges) {
                    org.joml.Vector3f cross = new org.joml.Vector3f();
                    oe.cross(ae, cross);
                    if (cross.lengthSquared() > 1e-12f) satAxes.add(cross);
                }
            }
            boolean separated = false;
            for (org.joml.Vector3f axis : satAxes) {
                float len = axis.length();
                if (len < 1e-6f) continue;
                float ax = axis.x / len, ay = axis.y / len, az = axis.z / len;
                float r1 = 0.5f * Math.abs(axisX.x * ax + axisX.y * ay + axisX.z * az)
                         + 0.5f * Math.abs(axisY.x * ax + axisY.y * ay + axisY.z * az)
                         + 0.5f * Math.abs(axisZ.x * ax + axisZ.y * ay + axisZ.z * az);
                float r2 = vHalfX * Math.abs(ax) + vHalfY * Math.abs(ay) + vHalfZ * Math.abs(az);
                float dist = Math.abs((center.x - vCenter.x) * ax + (center.y - vCenter.y) * ay + (center.z - vCenter.z) * az);
                if (dist >= r1 + r2) { separated = true; break; }
            }
            if (!separated) return true;
        }
        return false;
    }

    public static boolean tryPlace() {
        if (!active) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return false;
        Hand hand = Hand.MAIN_HAND;
        if (!(client.player.getStackInHand(hand).getItem() instanceof BlockItem)) {
            hand = Hand.OFF_HAND;
            if (!(client.player.getStackInHand(hand).getItem() instanceof BlockItem)) return false;
        }
        Quaternionf q = currentQuaternion();
        Vec3d pos = getPlacementPos(client, q);

        if (isOverlapping(client, pos, q)) {
            client.player.sendMessage(Text.literal("§c无法放置：与现有方块重叠"), true);
            return true; 
        }
        ClientPlayNetworking.send(new FreeBlockInteractPayload(
                FreeBlockInteractPayload.ACTION_PLACE_FREE,
                pos.x, pos.y, pos.z,
                0, hand == Hand.OFF_HAND ? 1 : 0,
                q.x, q.y, q.z, q.w));
        return true;
    }

    private static void renderPreview(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (context.consumers() == null || context.matrixStack() == null) return;

        if (client.getEntityRenderDispatcher().shouldRenderHitboxes()) {
            renderAllHitboxes(context, client);
        }

        if (!active) return;

        Hand hand = Hand.MAIN_HAND;
        if (!(client.player.getStackInHand(hand).getItem() instanceof BlockItem)) {
            hand = Hand.OFF_HAND;
            if (!(client.player.getStackInHand(hand).getItem() instanceof BlockItem)) return;
        }

        Quaternionf q = currentQuaternion();
        Vec3d pos = getPlacementPos(client, q);
        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        double lx = pos.x - cam.x, ly = pos.y - cam.y, lz = pos.z - cam.z;

        BlockItem bi = (BlockItem) client.player.getStackInHand(hand).getItem();
        BlockState state = bi.getBlock().getDefaultState();

        if (FreeBlocks.placeCallback != null) {
            try {
                var result = FreeBlocks.placeCallback.onPlace(
                        client.world, pos.x, pos.y, pos.z, q.x, q.y, q.z, q.w, state);
                if (result != null && result.state() != null) {
                    state = result.state();
                }
            } catch (Throwable ignored) {}
        }

        Direction playerFacing = client.player.getHorizontalFacing();
        if (state.contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING)) {
            state = state.with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, playerFacing);
        }

        boolean overlap = isOverlapping(client, pos, q);
        float boxR = overlap ? 1f : 0f;
        float boxG = overlap ? 0.2f : 1f;
        float boxB = overlap ? 0.2f : 0.5f;

        VoxelShape outline = state.getOutlineShape(client.world, BlockPos.ofFloored(pos.x, pos.y, pos.z));
        if (outline.isEmpty()) outline = VoxelShapes.fullCube();
        Box outlineBox = outline.getBoundingBox();
        if (outlineBox != null) {
            drawRotatedBox(lines, matrices, lx, ly, lz, q, boxR, boxG, boxB, 0.7f,
                    outlineBox.minX, outlineBox.minY, outlineBox.minZ,
                    outlineBox.maxX, outlineBox.maxY, outlineBox.maxZ);
        }

        VoxelShape collision = state.getCollisionShape(client.world, BlockPos.ofFloored(pos.x, pos.y, pos.z));
        if (collision.isEmpty()) collision = VoxelShapes.fullCube();
        Box collisionBox = collision.getBoundingBox();
        if (collisionBox != null && !collisionBox.equals(outlineBox)) {
            drawRotatedBox(lines, matrices, lx, ly, lz, q, 1f, 0.3f, 0.3f, 0.8f,
                    collisionBox.minX, collisionBox.minY, collisionBox.minZ,
                    collisionBox.maxX, collisionBox.maxY, collisionBox.maxZ);
        }
    }

    private static void drawRotatedBox(VertexConsumer lines, MatrixStack matrices,
                                        double ox, double oy, double oz, Quaternionf q,
                                        float r, float g, float b, float a,
                                        double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ) {
        double[] xs = {minX, maxX, minX, maxX, minX, maxX, minX, maxX};
        double[] ys = {minY, minY, maxY, maxY, minY, minY, maxY, maxY};
        double[] zs = {minZ, minZ, minZ, minZ, maxZ, maxZ, maxZ, maxZ};
        double[][] p = new double[8][3];
        Vector3f v = new Vector3f();
        for (int i = 0; i < 8; i++) {
            v.set((float) xs[i], (float) ys[i], (float) zs[i]);
            q.transform(v);
            p[i][0] = v.x + ox;
            p[i][1] = v.y + oy;
            p[i][2] = v.z + oz;
        }
        var pose = matrices.peek().getPositionMatrix();
        for (int[] e : EDGES) {
            lines.vertex(pose, (float) p[e[0]][0], (float) p[e[0]][1], (float) p[e[0]][2])
                 .color(r, g, b, a).normal(1f, 0f, 0f)
                 .vertex(pose, (float) p[e[1]][0], (float) p[e[1]][1], (float) p[e[1]][2])
                 .color(r, g, b, a).normal(1f, 0f, 0f);
        }
    }

    private static void renderAllHitboxes(WorldRenderContext context, MinecraftClient client) {
        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());

        Box queryRange = new Box(
                cam.x - 32, cam.y - 32, cam.z - 32,
                cam.x + 32, cam.y + 32, cam.z + 32);

        var pose = matrices.peek().getPositionMatrix();

        List<VoxelShape> collisionShapes = FreeBlocks.collectCollisionShapes(client.world, queryRange);
        for (VoxelShape vs : collisionShapes) {
            Box b = vs.getBoundingBox();
            if (b == null) continue;
            drawBoxLines(lines, pose,
                    b.minX - cam.x, b.minY - cam.y, b.minZ - cam.z,
                    b.maxX - cam.x, b.maxY - cam.y, b.maxZ - cam.z,
                    1.0f, 0.2f, 0.2f, 0.5f);
        }

        FreeBlocks.forEachPlaced(client.world, queryRange, fb -> {
            boolean hasRotation = fb.qx() != 0f || fb.qy() != 0f || fb.qz() != 0f || fb.qw() != 1f;
            if (!hasRotation) return;
            VoxelShape shape = fb.state().getCollisionShape(client.world, fb.pos().toBlockPos());
            if (shape.isEmpty()) shape = VoxelShapes.fullCube();
            Box localBox = shape.getBoundingBox();
            if (localBox == null) return;

            double[][] corners = FreeBlocks.rotatedCorners(localBox, fb.pos(), fb.qx(), fb.qy(), fb.qz(), fb.qw());
            double[][] p = new double[8][3];
            for (int i = 0; i < 8; i++) {
                p[i][0] = corners[i][0] - cam.x;
                p[i][1] = corners[i][1] - cam.y;
                p[i][2] = corners[i][2] - cam.z;
            }
            for (int[] e : EDGES) {
                lines.vertex(pose, (float) p[e[0]][0], (float) p[e[0]][1], (float) p[e[0]][2])
                     .color(1.0f, 0.2f, 0.2f, 0.5f).normal(1f, 0f, 0f)
                     .vertex(pose, (float) p[e[1]][0], (float) p[e[1]][1], (float) p[e[1]][2])
                     .color(1.0f, 0.2f, 0.2f, 0.5f).normal(1f, 0f, 0f);
            }
        });
    }

    private static void drawBoxLines(VertexConsumer lines, org.joml.Matrix4f pose,
                                      double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ,
                                      float r, float g, float b, float a) {
        double[][] p = {
            {minX, minY, minZ}, {maxX, minY, minZ},
            {minX, maxY, minZ}, {maxX, maxY, minZ},
            {minX, minY, maxZ}, {maxX, minY, maxZ},
            {minX, maxY, maxZ}, {maxX, maxY, maxZ}
        };
        int[][] edges = {
            {0,1},{2,3},{4,5},{6,7},
            {0,2},{1,3},{4,6},{5,7},
            {0,4},{1,5},{2,6},{3,7}
        };
        for (int[] e : edges) {
            lines.vertex(pose, (float) p[e[0]][0], (float) p[e[0]][1], (float) p[e[0]][2])
                 .color(r, g, b, a).normal(1f, 0f, 0f)
                 .vertex(pose, (float) p[e[1]][0], (float) p[e[1]][1], (float) p[e[1]][2])
                 .color(r, g, b, a).normal(1f, 0f, 0f);
        }
    }
}
