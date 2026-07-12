package com.betterslab.networking;

import com.betterslab.BetterSlab;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = BetterSlab.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetworking {
    private ModNetworking() {}

    @SubscribeEvent
    public static void onRegisterPayload(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(PlacementModePayload.TYPE, PlacementModePayload.STREAM_CODEC,
                        PlacementModePayload::handle);
    }
}
