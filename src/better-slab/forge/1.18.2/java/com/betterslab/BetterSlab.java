package com.betterslab;

import com.betterslab.block.ModBlocks;
import com.betterslab.block.entity.ModBlockEntities;
import com.betterslab.client.BetterSlabClient;
import com.betterslab.config.BetterSlabConfig;
import com.betterslab.handler.BreakBlockHandler;
import com.betterslab.networking.ModNetworking;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BetterSlab.MOD_ID)
public class BetterSlab {
    public static final String MOD_ID = "betterslab";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public BetterSlab() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BetterSlabConfig.load();
        ModBlocks.register(bus);
        ModBlockEntities.register(bus);
        ModNetworking.register();
        MinecraftForge.EVENT_BUS.register(BreakBlockHandler.class);
        BetterSlabClient.init(bus);
        LOGGER.info("Better Slab mod initialized!");
    }
}
