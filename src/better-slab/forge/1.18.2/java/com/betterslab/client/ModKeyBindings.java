package com.betterslab.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 模组按键绑定。
 *
 * <p>只注册 Alt/C/R 为 KeyMapping。W/A/S/D/Shift/Space 不注册为 KeyMapping
 * （避免与原版移动键冲突导致潜行失效），通过 GLFW 原始键状态读取。</p>
 */
public class ModKeyBindings {

    public static KeyMapping triggerKey;   // Alt
    public static KeyMapping lockKey;      // C
    public static KeyMapping modeToggleKey;// R

    // GLFW 键码常量
    public static final int KEY_W = GLFW.GLFW_KEY_W;
    public static final int KEY_A = GLFW.GLFW_KEY_A;
    public static final int KEY_S = GLFW.GLFW_KEY_S;
    public static final int KEY_D = GLFW.GLFW_KEY_D;
    public static final int KEY_SPACE = GLFW.GLFW_KEY_SPACE;
    public static final int KEY_LEFT_SHIFT = GLFW.GLFW_KEY_LEFT_SHIFT;

    private static final String CATEGORY = "category.betterslab";

    public static void register() {
        triggerKey = new KeyMapping(
                "key.betterslab.trigger", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

        lockKey = new KeyMapping(
                "key.betterslab.lock", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_C, CATEGORY);

        modeToggleKey = new KeyMapping(
                "key.betterslab.mode_toggle", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R, CATEGORY);
    }

    /** 直接读取 GLFW 原始键状态。 */
    public static boolean isMovementKeyPressed(int glfwKeyCode) {
        Minecraft client = Minecraft.getInstance();
        if (client.getWindow() == null) return false;
        long handle = client.getWindow().getWindow();
        return InputConstants.isKeyDown(handle, glfwKeyCode);
    }

    public static boolean isFrontPressed() { return isMovementKeyPressed(KEY_W); }
    public static boolean isBackPressed() { return isMovementKeyPressed(KEY_S); }
    public static boolean isLeftPressed() { return isMovementKeyPressed(KEY_A); }
    public static boolean isRightPressed() { return isMovementKeyPressed(KEY_D); }
    public static boolean isTopPressed() { return isMovementKeyPressed(KEY_SPACE); }
    public static boolean isBottomPressed() { return isMovementKeyPressed(KEY_LEFT_SHIFT); }
}
