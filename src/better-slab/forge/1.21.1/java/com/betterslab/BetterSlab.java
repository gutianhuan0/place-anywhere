package com.betterslab;

import com.betterslab.block.ModBlocks;
import com.betterslab.block.entity.ModBlockEntities;
import com.betterslab.client.BetterSlabClient;
import com.betterslab.config.BetterSlabConfig;
import com.betterslab.handler.BreakBlockHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BetterSlab.MOD_ID)
public class BetterSlab {
    public static final String MOD_ID = "betterslab";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public BetterSlab(IEventBus modBus) {
        BetterSlabConfig.load();
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        NeoForge.EVENT_BUS.register(BreakBlockHandler.class);
        BetterSlabClient.init(modBus);
        LOGGER.info("Better Slab mod initialized!");
    }
}
