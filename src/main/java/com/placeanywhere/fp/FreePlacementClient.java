package com.placeanywhere.fp;

import net.fabricmc.api.ClientModInitializer;





public class FreePlacementClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        com.placeanywhere.PlaceAnywhereMod.LOGGER.info("[Free Placement] 客户端初始化：自由放置模式");
        FreePlacementMode.register();
    }
}
