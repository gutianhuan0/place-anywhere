package com.placeanywhere.fp;

import com.placeanywhere.core.FreeBlockInteractPayload;
import com.placeanywhere.core.FreeBlocks;
import com.placeanywhere.core.GJKEPA;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 自由放置模式：按 G 切换开关。
 *
 * 交互方式：
 *   G      — 切换自由放置模式
 *   V      — 切换旋转轴（偏航→俯仰→翻滚）
 *   滚轮    — 调整当前轴的旋转角度（Shift 微调 5°，否则 15°）
 *   F3+B   — 原版显示碰撞箱时，同时显示自由方块的碰撞箱（红色）
 *   左键    — 放置方块到准星位置，带当前旋转四元数
 *
 * 预览框贴着准星命中的方块表面，而非固定距离。
 * 旋转顺序采用航空学 Tait-Bryan ZYX（yaw→pitch→roll）。
 */
public final class FreePlacementMode {
    private static final double REACH = 6.0;
    private static final float STEP_NORMAL = 15.0f;
    private static final float STEP_FINE = 5.0f;

    private enum Axis { YAW, PITCH, ROLL }

    private static KeyBinding toggleKey;   // G
    private static KeyBinding axisKey;     // V
    private static KeyBinding upKey;       // PageUp
    private static KeyBinding downKey;     // PageDown
    private static KeyBinding resetKey;    // R

    private static boolean active = false;
    private static Axis currentAxis = Axis.YAW;
    private static float pitchDeg = 0f, yawDeg = 0f, rollDeg = 0f;
    /** Y 偏移调整（用于方块上下移动）。 */
    private static float yOffset = 0f;
    /** 左键放置冷却标志（由 Mixin 重置）。 */
    private static boolean mineHandled = false;

    /** 立方体 8 角局部坐标。 */
    private static final float[] CUBE_X = {0,1,0,1,0,1,0,1};
    private static final float[] CUBE_Y = {0,0,1,1,0,0,1,1};
    private static final float[] CUBE_Z = {0,0,0,0,1,1,1,1};
    /** 12 条边的顶点索引。 */
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
            client.player.sendMessage(new LiteralText(msg), true);
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
            client.player.sendMessage(new LiteralText("§e旋转轴: " + axisName), true);
        }

        // PageUp/PageDown 调整 Y 偏移
        while (upKey.wasPressed()) {
            yOffset += 0.5f;
            client.player.sendMessage(new LiteralText("§eY偏移: " + yOffset), true);
        }
        while (downKey.wasPressed()) {
            yOffset -= 0.5f;
            client.player.sendMessage(new LiteralText("§eY偏移: " + yOffset), true);
        }

        // R 键重置旋转和偏移
        while (resetKey.wasPressed()) {
            resetTransform();
            client.player.sendMessage(new LiteralText("§e已重置旋转和Y偏移"), true);
        }
    }

    /** 重置旋转和偏移（由外部调用）。 */
    public static void resetTransform() {
        pitchDeg = 0f;
        yawDeg = 0f;
        rollDeg = 0f;
        yOffset = 0f;
    }

    /** 由 MouseMixin 调用：滚轮调整当前轴的旋转角度。 */
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

    public static Quaternion currentQuaternion() {
        float yaw = (float) Math.toRadians(yawDeg);
        float pitch = (float) Math.toRadians(pitchDeg);
        float roll = (float) Math.toRadians(rollDeg);
        // ZYX Tait-Bryan rotation: qZ * qY * qX (equivalent to JOML's rotationZYX(roll, yaw, pitch))
        Quaternion q = new Quaternion(0, 0, (float) Math.sin(roll / 2), (float) Math.cos(roll / 2));
        q.hamiltonProduct(new Quaternion(0, (float) Math.sin(yaw / 2), 0, (float) Math.cos(yaw / 2)));
        q.hamiltonProduct(new Quaternion((float) Math.sin(pitch / 2), 0, 0, (float) Math.cos(pitch / 2)));
        return q;
    }

    /** 用四元数旋转向量（原地修改）。替代 JOML 的 Quaternionf.transform(Vector3f)。 */
    private static void transformQuat(Quaternion q, GJKEPA.Vec3 v) {
        float qx = q.getX(), qy = q.getY(), qz = q.getZ(), qw = q.getW();
        float tx = 2 * (qy * v.z - qz * v.y);
        float ty = 2 * (qz * v.x - qx * v.z);
        float tz = 2 * (qx * v.y - qy * v.x);
        v.x += qw * tx + (qy * tz - qz * ty);
        v.y += qw * ty + (qz * tx - qx * tz);
        v.z += qw * tz + (qx * ty - qy * tx);
    }

    /** 预览位置：贴着准星命中的方块表面放置（精确坐标，不在整数网格上）。
     *  先用 FreeBlocks.raycast 检测自由方块命中，再回退到 crosshairTarget 检测原版方块。
     *  方块中心放在命中点 + 法线方向 * 0.5 处，再减去 (0.5,0.5,0.5) 得到最小角。
     *  加上 Y 偏移调整高度。确保旋转后包围 AABB 不低于玩家脚底（防止到地下）。 */
    private static Vec3d getPlacementPos(MinecraftClient client, Quaternion q) {
        Vec3d center;
        Vec3d eye = client.player.getEyePos();
        Vec3d dir = client.player.getRotationVec(1.0F);
        Vec3d end = eye.add(dir.multiply(REACH));

        // 优先用 FreeBlocks.raycast 检测自由方块命中（获取精确命中点和法线）
        var freeHit = FreeBlocks.raycast(client.world, eye, end);
        if (freeHit.isPresent()) {
            var fb = freeHit.get();
            center = fb.point().add(
                    fb.side().getOffsetX() * 0.5,
                    fb.side().getOffsetY() * 0.5,
                    fb.side().getOffsetZ() * 0.5);
        } else if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            // 原版方块命中
            BlockHitResult bhr = (BlockHitResult) client.crosshairTarget;
            Vec3d hitPos = bhr.getPos();
            Direction side = bhr.getSide();
            center = hitPos.add(
                    side.getOffsetX() * 0.5,
                    side.getOffsetY() * 0.5,
                    side.getOffsetZ() * 0.5);
        } else {
            // 没有命中方块，用 reach 距离
            center = end;
        }
        // 加 Y 偏移
        center = center.add(0, yOffset, 0);
        // 转换为最小角坐标（方块模型 0-1 范围，pos 是最小角）
        double x = center.x - 0.5;
        double y = center.y - 0.5;
        double z = center.z - 0.5;
        // 确保 Y 不低于世界底部
        double minY = client.world.getBottomY() + 1;
        if (y < minY) y = minY;
        // 基于旋转后包围 AABB 限制 Y，确保 OBB 最低点不低于玩家脚底
        // 旋转后方块的实际最低点可能低于 pos.y（如 45 度旋转对角线更长）
        Box rotatedBBox = FreeBlocks.rotateBoxAABB(
                new Box(0, 0, 0, 1, 1, 1),
                new com.placeanywhere.core.DecimalBlockPos(x, y, z),
                q.getX(), q.getY(), q.getZ(), q.getW());
        double playerFeetY = client.player.getY();
        if (rotatedBBox.minY < playerFeetY) {
            y += playerFeetY - rotatedBBox.minY;
        }
        return new Vec3d(x, y, z);
    }

    /** 检查预览位置是否与现有自由方块或原版方块重叠。
     *  自由方块用 SAT OBB vs OBB 检测，原版方块用 OBB 的包围 AABB 检测碰撞形状。 */
    private static boolean isOverlapping(MinecraftClient client, Vec3d pos, Quaternion q) {
        GJKEPA.Vec3 axisX = new GJKEPA.Vec3(1, 0, 0);
        GJKEPA.Vec3 axisY = new GJKEPA.Vec3(0, 1, 0);
        GJKEPA.Vec3 axisZ = new GJKEPA.Vec3(0, 0, 1);
        transformQuat(q, axisX);
        transformQuat(q, axisY);
        transformQuat(q, axisZ);
        GJKEPA.Vec3 center = new GJKEPA.Vec3(
                (float) (pos.x + 0.5), (float) (pos.y + 0.5), (float) (pos.z + 0.5));

        // 1) 检查与自由方块的重叠（SAT OBB vs OBB）
        Box searchBox = new Box(pos.x - 1, pos.y - 1, pos.z - 1, pos.x + 2, pos.y + 2, pos.z + 2);
        boolean[] overlap = { false };
        FreeBlocks.forEachPlaced(client.world, searchBox, fb -> {
            if (overlap[0]) return;
            Quaternion fq = new Quaternion(fb.qx(), fb.qy(), fb.qz(), fb.qw());
            fq.normalize();
            GJKEPA.Vec3 fAxisX = new GJKEPA.Vec3(1, 0, 0);
            GJKEPA.Vec3 fAxisY = new GJKEPA.Vec3(0, 1, 0);
            GJKEPA.Vec3 fAxisZ = new GJKEPA.Vec3(0, 0, 1);
            transformQuat(fq, fAxisX);
            transformQuat(fq, fAxisY);
            transformQuat(fq, fAxisZ);
            GJKEPA.Vec3 fCenter = new GJKEPA.Vec3(
                    (float) (fb.pos().x() + 0.5), (float) (fb.pos().y() + 0.5), (float) (fb.pos().z() + 0.5));

            GJKEPA.Vec3[] axes = { axisX, axisY, axisZ, fAxisX, fAxisY, fAxisZ };
            boolean separated = false;
            for (GJKEPA.Vec3 axis : axes) {
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

        // 2) 检查与原版方块的重叠（用 OBB 包围 AABB 查原版碰撞形状）
        Box obbBoundingBox = FreeBlocks.rotateBoxAABB(
                new Box(0, 0, 0, 1, 1, 1),
                new com.placeanywhere.core.DecimalBlockPos(pos.x, pos.y, pos.z),
                q.getX(), q.getY(), q.getZ(), q.getW());
        BlockPos minBp = new BlockPos(MathHelper.floor(obbBoundingBox.minX), MathHelper.floor(obbBoundingBox.minY), MathHelper.floor(obbBoundingBox.minZ));
        BlockPos maxBp = new BlockPos(MathHelper.floor(obbBoundingBox.maxX), MathHelper.floor(obbBoundingBox.maxY), MathHelper.floor(obbBoundingBox.maxZ));
        for (BlockPos bp : BlockPos.iterate(minBp, maxBp)) {
            BlockState vs = client.world.getBlockState(bp);
            if (vs.isAir()) continue;
            VoxelShape vsShape = vs.getCollisionShape(client.world, bp);
            if (vsShape.isEmpty()) continue;
            Box vsBox = vsShape.getBoundingBox();
            if (vsBox == null) continue;
            Box worldVsBox = vsBox.offset(bp.getX(), bp.getY(), bp.getZ());
            // SAT OBB vs AABB：6 轴检测
            GJKEPA.Vec3 vCenter = new GJKEPA.Vec3(
                    (float) ((worldVsBox.minX + worldVsBox.maxX) * 0.5),
                    (float) ((worldVsBox.minY + worldVsBox.maxY) * 0.5),
                    (float) ((worldVsBox.minZ + worldVsBox.maxZ) * 0.5));
            float vHalfX = (float) ((worldVsBox.maxX - worldVsBox.minX) * 0.5);
            float vHalfY = (float) ((worldVsBox.maxY - worldVsBox.minY) * 0.5);
            float vHalfZ = (float) ((worldVsBox.maxZ - worldVsBox.minZ) * 0.5);
            GJKEPA.Vec3[] satAxes = {
                axisX, axisY, axisZ,
                new GJKEPA.Vec3(1, 0, 0), new GJKEPA.Vec3(0, 1, 0), new GJKEPA.Vec3(0, 0, 1)
            };
            boolean separated = false;
            for (GJKEPA.Vec3 axis : satAxes) {
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
        Hand hand = client.player.getMainHandStack().isEmpty() ? Hand.OFF_HAND : Hand.MAIN_HAND;
        if (!(client.player.getStackInHand(hand).getItem() instanceof BlockItem)) return false;
        Quaternion q = currentQuaternion();
        Vec3d pos = getPlacementPos(client, q);
        // 检查重叠
        if (isOverlapping(client, pos, q)) {
            client.player.sendMessage(new LiteralText("§c无法放置：与现有方块重叠"), true);
            return true; // 拦截原版放置，但不发送放置包
        }
        FreeBlockInteractPayload payload = new FreeBlockInteractPayload(
                FreeBlockInteractPayload.ACTION_PLACE_FREE,
                pos.x, pos.y, pos.z,
                0, hand == Hand.OFF_HAND ? 1 : 0,
                q.getX(), q.getY(), q.getZ(), q.getW());
        PacketByteBuf buf = PacketByteBufs.create();
        payload.encode(buf);
        ClientPlayNetworking.send(FreeBlockInteractPayload.ID, buf);
        return true;
    }

    // ---------- 渲染 ----------

    private static void renderPreview(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (context.consumers() == null || context.matrixStack() == null) return;

        // F3+B 碰撞箱显示（原版 shouldRenderHitboxes）
        if (client.getEntityRenderDispatcher().shouldRenderHitboxes()) {
            renderAllHitboxes(context, client);
        }

        if (!active) return;

        Hand hand = client.player.getMainHandStack().isEmpty() ? Hand.OFF_HAND : Hand.MAIN_HAND;
        if (!(client.player.getStackInHand(hand).getItem() instanceof BlockItem)) return;

        Quaternion q = currentQuaternion();
        Vec3d pos = getPlacementPos(client, q);
        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        double lx = pos.x - cam.x, ly = pos.y - cam.y, lz = pos.z - cam.z;

        // 检查重叠，重叠时预览框变红
        boolean overlap = isOverlapping(client, pos, q);
        float boxR = overlap ? 1f : 0f;
        float boxG = overlap ? 0.2f : 1f;
        float boxB = overlap ? 0.2f : 0.5f;

        // 预览线框（重叠时红色，否则绿色）— 旋转后的 1x1x1 立方体
        drawRotatedBox(lines, matrices, lx, ly, lz, q, boxR, boxG, boxB, 0.7f,
                0, 0, 0, 1, 1, 1);

        // 预览方块的碰撞箱（红色）— 用方块实际碰撞形状
        BlockItem bi = (BlockItem) client.player.getStackInHand(hand).getItem();
        BlockState state = bi.getBlock().getDefaultState();
        VoxelShape collision = state.getCollisionShape(client.world, new BlockPos(MathHelper.floor(pos.x), MathHelper.floor(pos.y), MathHelper.floor(pos.z)));
        if (collision.isEmpty()) collision = VoxelShapes.fullCube();
        Box collisionBox = collision.getBoundingBox();
        if (collisionBox != null) {
            drawRotatedBox(lines, matrices, lx, ly, lz, q, 1f, 0.3f, 0.3f, 0.8f,
                    collisionBox.minX, collisionBox.minY, collisionBox.minZ,
                    collisionBox.maxX, collisionBox.maxY, collisionBox.maxZ);
        }
    }

    /** 画一个旋转后的盒子线框（局部坐标 + 四元数旋转 + 偏移）。 */
    private static void drawRotatedBox(VertexConsumer lines, MatrixStack matrices,
                                        double ox, double oy, double oz, Quaternion q,
                                        float r, float g, float b, float a,
                                        double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ) {
        double[] xs = {minX, maxX, minX, maxX, minX, maxX, minX, maxX};
        double[] ys = {minY, minY, maxY, maxY, minY, minY, maxY, maxY};
        double[] zs = {minZ, minZ, minZ, minZ, maxZ, maxZ, maxZ, maxZ};
        double[][] p = new double[8][3];
        GJKEPA.Vec3 v = new GJKEPA.Vec3();
        for (int i = 0; i < 8; i++) {
            v.set((float) xs[i], (float) ys[i], (float) zs[i]);
            transformQuat(q, v);
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

    /** 显示附近所有自由方块的碰撞箱线框（红色）。
     *  非旋转方块用 VoxelShape（AABB），旋转方块用 OBB（旋转后的 8 角连线）。 */
    private static void renderAllHitboxes(WorldRenderContext context, MinecraftClient client) {
        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());

        Box queryRange = new Box(
                cam.x - 32, cam.y - 32, cam.z - 32,
                cam.x + 32, cam.y + 32, cam.z + 32);

        var pose = matrices.peek().getPositionMatrix();

        // 非旋转方块：用 collectCollisionShapes 获取 AABB
        List<VoxelShape> collisionShapes = FreeBlocks.collectCollisionShapes(client.world, queryRange);
        for (VoxelShape vs : collisionShapes) {
            Box b = vs.getBoundingBox();
            if (b == null) continue;
            drawBoxLines(lines, pose,
                    b.minX - cam.x, b.minY - cam.y, b.minZ - cam.z,
                    b.maxX - cam.x, b.maxY - cam.y, b.maxZ - cam.z,
                    1.0f, 0.2f, 0.2f, 0.5f);
        }

        // 旋转方块：用 forEachPlaced 获取，画 OBB
        FreeBlocks.forEachPlaced(client.world, queryRange, fb -> {
            boolean hasRotation = fb.qx() != 0f || fb.qy() != 0f || fb.qz() != 0f || fb.qw() != 1f;
            if (!hasRotation) return;
            VoxelShape shape = fb.state().getCollisionShape(client.world, fb.pos().toBlockPos());
            if (shape.isEmpty()) shape = VoxelShapes.fullCube();
            Box localBox = shape.getBoundingBox();
            if (localBox == null) return;
            // 画旋转后的 OBB
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

    /** 画 AABB 的 12 条边线。 */
    private static void drawBoxLines(VertexConsumer lines, Matrix4f pose,
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
