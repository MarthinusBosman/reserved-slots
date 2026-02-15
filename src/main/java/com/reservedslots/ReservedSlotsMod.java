package com.reservedslots;

import com.reservedslots.network.ReservedSlotPackets;
import com.reservedslots.server.ReservedSlotManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReservedSlotsMod implements ModInitializer {
    public static final String MOD_ID = "reservedslots";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Reserved Slots Mod");
        
        // Register network packets
        ReservedSlotPackets.register();
        
        // Register player join event to sync reserved slots
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ReservedSlotManager.syncToPlayer(handler.getPlayer());
        });
        
        // Register server events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Reserved Slots Mod ready on server");
        });
        
        // Clear all server-side cache when server stops (world change in single-player)
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Server stopping, clearing all reserved slot data");
            ReservedSlotManager.clearAllPlayerData();
        });
    }
}
