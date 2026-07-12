package com.betterslab.util;

/**
 * 半砖放置模式。
 *
 * <p>AUTO_H / AUTO_V 是“默认行为”，由 R 键切换；LEFT/RIGHT/FRONT/BACK/TOP/BOTTOM
 * 是 Alt+方向键选择的“具体方向”。服务器端只关心当前生效的模式。</p>
 */
public enum PlacementMode {
    /** 横向默认：点击侧面→竖半砖，点击顶/底→横半砖（原版交互）。 */
    AUTO_H,
    /** 竖向默认：点击顶/底面按象限放竖半砖，点击侧面→竖半砖。 */
    AUTO_V,
    /** Alt+A 玩家左侧竖半砖。 */
    LEFT,
    /** Alt+D 玩家右侧竖半砖。 */
    RIGHT,
    /** Alt+W 玩家前方竖半砖。 */
    FRONT,
    /** Alt+S 玩家后方竖半砖。 */
    BACK,
    /** Alt+Space 上半砖。 */
    TOP,
    /** Alt+Shift 下半砖。 */
    BOTTOM;

    public static PlacementMode fromId(int id) {
        if (id < 0 || id >= values().length) return AUTO_H;
        return values()[id];
    }

    public int getId() {
        return ordinal();
    }

    /** 是否是“具体方向”模式（非 AUTO）。 */
    public boolean isSpecific() {
        return this != AUTO_H && this != AUTO_V;
    }

    /** 是否对应竖直半砖。 */
    public boolean isVertical() {
        return this == LEFT || this == RIGHT || this == FRONT || this == BACK;
    }
}
