package com.betterslab.networking;

import com.betterslab.config.BetterSlabConfig;
import com.betterslab.util.PlacementMode;
import com.betterslab.util.PlacementState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ModNetworking {

    public static void registerServerListeners() {
        // 客户端 -> 服务器：同步当前生效的放置模式
        PayloadTypeRegistry.playC2S().register(
                PlacementModePayload.ID,
                PlacementModePayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                PlacementModePayload.ID,
                (payload, context) -> {
                    PlacementMode mode = PlacementMode.fromId(payload.modeId());
                    PlacementState.setMode(context.player(), mode);
                }
        );
    }
}
