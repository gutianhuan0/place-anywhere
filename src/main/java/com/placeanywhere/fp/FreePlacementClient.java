package com.placeanywhere.fp;

import net.fabricmc.api.ClientModInitializer;

/**
 * 自由放置模组客户端入口。
 * 依赖 Place Anywhere Core 提供的网络协议（FreeBlockInteractPayload）。
 */
public class FreePlacementClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        com.placeanywhere.PlaceAnywhereMod.LOGGER.info("[Free Placement] 客户端初始化：自由放置模式");
        FreePlacementMode.register();
    }
}
