package com.betterslab.networking;

import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetworking {

    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel INSTANCE;

    public static void register() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                PlacementModePayload.ID,
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );
        // C2S: 客户端 -> 服务器 同步当前生效的放置模式
        INSTANCE.registerMessage(0, PlacementModePayload.class,
                PlacementModePayload::encode, PlacementModePayload::decode,
                PlacementModePayload::handle);
    }
}
