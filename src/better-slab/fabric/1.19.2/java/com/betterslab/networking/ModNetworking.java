package com.betterslab.networking;

import com.betterslab.util.PlacementMode;
import com.betterslab.util.PlacementState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ModNetworking {

    public static void registerServerListeners() {
        // 客户端 -> 服务器：同步当前生效的放置模式（旧式 buffer 网络通信）
        ServerPlayNetworking.registerGlobalReceiver(
                PlacementModePayload.ID,
                (server, player, handler, buf, responseSender) -> {
                    int modeId = PlacementModePayload.read(buf);
                    PlacementMode mode = PlacementMode.fromId(modeId);
                    server.execute(() -> PlacementState.setMode(player, mode));
                }
        );
    }
}
