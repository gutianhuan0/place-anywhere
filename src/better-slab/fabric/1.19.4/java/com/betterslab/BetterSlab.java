package com.betterslab;

import com.betterslab.block.ModBlocks;
import com.betterslab.block.entity.ModBlockEntities;
import com.betterslab.config.BetterSlabConfig;
import com.betterslab.handler.BreakBlockHandler;
import com.betterslab.networking.ModNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterSlab implements ModInitializer {
    public static final String MOD_ID = "betterslab";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BetterSlabConfig.load();
        ModBlocks.registerModBlocks();
        ModBlockEntities.register();
        ModNetworking.registerServerListeners();
        BreakBlockHandler.register();
        LOGGER.info("Better Slab mod initialized!");
    }
}
