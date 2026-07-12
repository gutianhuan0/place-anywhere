package com.placeanywhere.fp;

import com.placeanywhere.PlaceAnywhereMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 自由放置模组 Forge 客户端入口。
 * 依赖 Place Anywhere Core 提供的网络协议（FreeBlockInteractPayload / FreeBlockNetworking）。
 */
@Mod("freeplacement")
public class FreePlacementClient {
    public static final String MOD_ID = "freeplacement";

    public FreePlacementClient() {
        PlaceAnywhereMod.LOGGER.info("[Free Placement] 客户端初始化：自由放置模式");
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(FreePlacementMode::onRegisterKeyMappings);
        MinecraftForge.EVENT_BUS.register(FreePlacementMode.class);
    }
}
