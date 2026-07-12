package com.placeanywhere.fp;

import com.placeanywhere.PlaceAnywhereMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;

/**
 * 自由放置模组 NeoForge 客户端入口。
 * 依赖 Place Anywhere Core 提供的网络协议（FreeBlockInteractPayload / FreeBlockNetworking）。
 */
@Mod("freeplacement")
public class FreePlacementClient {
    public static final String MOD_ID = "freeplacement";

    public FreePlacementClient(IEventBus modBus) {
        PlaceAnywhereMod.LOGGER.info("[Free Placement] 客户端初始化：自由放置模式");
        modBus.addListener(FreePlacementMode::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.register(FreePlacementMode.class);
    }
}
