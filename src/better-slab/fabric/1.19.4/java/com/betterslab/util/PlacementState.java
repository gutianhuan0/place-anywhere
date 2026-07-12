package com.betterslab.util;

import net.minecraft.entity.player.PlayerEntity;
import java.util.WeakHashMap;

/**
 * 玩家放置状态：生效模式 + 锁定模式 + 默认方向。
 *
 * <p>生效模式优先级：Alt 临时选择 > 锁定模式 > 默认（AUTO_H/AUTO_V）。</p>
 */
public class PlacementState {
    public enum DefaultOrientation { HORIZONTAL, VERTICAL }

    private static final WeakHashMap<PlayerEntity, PlacementMode> playerModes = new WeakHashMap<>();
    private static final WeakHashMap<PlayerEntity, PlacementMode> lockedModes = new WeakHashMap<>();
    private static final WeakHashMap<PlayerEntity, DefaultOrientation> defaults = new WeakHashMap<>();

    public static PlacementMode getMode(PlayerEntity player) {
        if (player == null) return PlacementMode.AUTO_H;
        return playerModes.getOrDefault(player, PlacementMode.AUTO_H);
    }

    public static void setMode(PlayerEntity player, PlacementMode mode) {
        if (player != null) playerModes.put(player, mode);
    }

    public static PlacementMode getLocked(PlayerEntity player) {
        if (player == null) return null;
        return lockedModes.get(player);
    }

    public static void setLocked(PlayerEntity player, PlacementMode mode) {
        if (player == null) return;
        if (mode == null) lockedModes.remove(player);
        else lockedModes.put(player, mode);
    }

    public static DefaultOrientation getDefault(PlayerEntity player) {
        if (player == null) return DefaultOrientation.HORIZONTAL;
        return defaults.getOrDefault(player, DefaultOrientation.HORIZONTAL);
    }

    public static void setDefault(PlayerEntity player, DefaultOrientation o) {
        if (player != null) defaults.put(player, o);
    }

    public static DefaultOrientation toggleDefault(PlayerEntity player) {
        DefaultOrientation cur = getDefault(player);
        DefaultOrientation next = cur == DefaultOrientation.HORIZONTAL
                ? DefaultOrientation.VERTICAL : DefaultOrientation.HORIZONTAL;
        setDefault(player, next);
        return next;
    }
}
