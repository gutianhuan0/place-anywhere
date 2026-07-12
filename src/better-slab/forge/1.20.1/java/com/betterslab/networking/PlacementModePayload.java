package com.betterslab.networking;

import com.betterslab.util.PlacementMode;
import com.betterslab.util.PlacementState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 网络数据包：用于客户端向服务器同步玩家当前选择的放置模式。
 *
 * <p>Forge SimpleChannel 消息类，通过 {@link FriendlyByteBuf} 手动写入/读取 modeId。</p>
 */
public class PlacementModePayload {
    public static final ResourceLocation ID = new ResourceLocation("betterslab", "placement_mode");

    private final int modeId;

    public PlacementModePayload(int modeId) {
        this.modeId = modeId;
    }

    public int modeId() {
        return modeId;
    }

    public static void encode(PlacementModePayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.modeId);
    }

    public static PlacementModePayload decode(FriendlyByteBuf buf) {
        return new PlacementModePayload(buf.readVarInt());
    }

    public static void handle(PlacementModePayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            PlacementMode mode = PlacementMode.fromId(msg.modeId);
            PlacementState.setMode(player, mode);
        });
        ctx.setPacketHandled(true);
    }
}
