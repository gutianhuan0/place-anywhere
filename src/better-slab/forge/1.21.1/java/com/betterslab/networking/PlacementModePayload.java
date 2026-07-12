package com.betterslab.networking;

import com.betterslab.util.PlacementMode;
import com.betterslab.util.PlacementState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 网络数据包：用于客户端向服务器同步玩家当前选择的放置模式。
 *
 * <p>NeoForge CustomPacketPayload 版本，通过 {@link FriendlyByteBuf} 手动写入/读取 modeId。</p>
 */
public class PlacementModePayload implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlacementModePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("betterslab", "placement_mode"));

    public static final StreamCodec<FriendlyByteBuf, PlacementModePayload> STREAM_CODEC =
            StreamCodec.of(PlacementModePayload::encode, PlacementModePayload::decode);

    private final int modeId;

    public PlacementModePayload(int modeId) {
        this.modeId = modeId;
    }

    public int modeId() {
        return modeId;
    }

    public static void encode(FriendlyByteBuf buf, PlacementModePayload msg) {
        buf.writeVarInt(msg.modeId);
    }

    public static PlacementModePayload decode(FriendlyByteBuf buf) {
        return new PlacementModePayload(buf.readVarInt());
    }

    public static void handle(PlacementModePayload msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PlacementMode mode = PlacementMode.fromId(msg.modeId);
                PlacementState.setMode(player, mode);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
