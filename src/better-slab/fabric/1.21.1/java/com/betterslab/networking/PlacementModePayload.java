package com.betterslab.networking;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 自定义网络数据包，用于客户端向服务器同步玩家当前选择的放置模式。
 *
 * <p>该数据包注册到 PLAY_C2S（客户端 -> 服务器）通道。服务器端接收后会根据
 * {@code modeId} 调用 {@code PlacementState.setMode} 设置玩家的放置模式。</p>
 */
public record PlacementModePayload(int modeId) implements CustomPayload {
    public static final CustomPayload.Id<PlacementModePayload> ID =
            new CustomPayload.Id<>(Identifier.of("betterslab", "placement_mode"));

    /**
     * 数据包编解码器。
     *
     * <p>1.21.1 中 {@code CustomPayload} 使用 {@link PacketCodec}（而非 DFU 的
     * {@code RecordCodecBuilder}）。这里通过 {@link PacketCodec#tuple} 将单个
     * {@code VAR_INT} 字段编码为完整的数据包。</p>
     */
    public static final PacketCodec<ByteBuf, PlacementModePayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT,
                    PlacementModePayload::modeId,
                    PlacementModePayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
