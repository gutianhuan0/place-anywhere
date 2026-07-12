package com.betterslab.networking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 网络数据包辅助类：用于客户端向服务器同步玩家当前选择的放置模式。
 *
 * <p>1.19.2 使用旧式 buffer 网络通信，不使用 CustomPayload。
 * 通过 {@link PacketByteBuf} 手动写入/读取 modeId。</p>
 */
public class PlacementModePayload {
    public static final Identifier ID = new Identifier("betterslab", "placement_mode");

    private final int modeId;

    public PlacementModePayload(int modeId) {
        this.modeId = modeId;
    }

    public int modeId() {
        return modeId;
    }

    /** 将 modeId 写入 buffer。 */
    public static void write(PacketByteBuf buf, int modeId) {
        buf.writeVarInt(modeId);
    }

    /** 从 buffer 读取 modeId。 */
    public static int read(PacketByteBuf buf) {
        return buf.readVarInt();
    }
}
