package com.reservedslots;

import com.reservedslots.network.ReservedSlotPackets;
import com.reservedslots.server.ReservedSlotManager;
import com.reservedslots.server.ReservedSlotsPersistentState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
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
        
        // Register player join event to load and sync reserved slots
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            String playerName = player.getName().getString();
            
            // For offline/single-player mode, use a constant key since player names/UUIDs are random
            String storageKey = server.isSingleplayer() ? "singleplayer" : playerName;
            
            LOGGER.info("Player {} joining with UUID: {} (storage key: {})", playerName, player.getUuid(), storageKey);
            
            // Load player data from persistent storage
            ReservedSlotsPersistentState state = ReservedSlotsPersistentState.get(server);
            NbtCompound playerNbt = state.getPlayerData(storageKey);
            if (playerNbt != null) {
                LOGGER.info("Found saved data for storage key: {}", storageKey);
                ReservedSlotManager.loadPlayerData(player, playerNbt);
            } else {
                LOGGER.info("No saved data found for storage key: {}", storageKey);
            }
            
            // Sync to client
            ReservedSlotManager.syncToPlayer(player);
        });
        
        // Register player disconnect event to save reserved slots
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            String playerName = player.getName().getString();
            
            // For offline/single-player mode, use a constant key since player names/UUIDs are random
            String storageKey = server.isSingleplayer() ? "singleplayer" : playerName;
            
            LOGGER.info("Player {} disconnecting with UUID: {} (storage key: {})", playerName, player.getUuid(), storageKey);
            
            // Save player data to persistent storage
            NbtCompound playerNbt = new NbtCompound();
            ReservedSlotManager.savePlayerData(player, playerNbt);
            
            LOGGER.info("Saving data for storage key: {}", storageKey);
            
            ReservedSlotsPersistentState state = ReservedSlotsPersistentState.get(server);
            state.setPlayerData(storageKey, playerNbt);
        });
        
        // Register server events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Reserved Slots Mod ready on server");
        });
        
        // Clear all server-side cache when server stops (world change in single-player)
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Server stopping, saving all player data");
            
            // Save all active players before clearing
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                String playerName = player.getName().getString();
                
                // For offline/single-player mode, use a constant key
                String storageKey = server.isSingleplayer() ? "singleplayer" : playerName;
                
                NbtCompound playerNbt = new NbtCompound();
                ReservedSlotManager.savePlayerData(player, playerNbt);
                
                ReservedSlotsPersistentState state = ReservedSlotsPersistentState.get(server);
                state.setPlayerData(storageKey, playerNbt);
            }
            
            ReservedSlotManager.clearAllPlayerData();
            ReservedSlotsPersistentState.clearCache();
        });
    }
}
