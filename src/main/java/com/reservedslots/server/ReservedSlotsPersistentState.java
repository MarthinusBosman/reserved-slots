package com.reservedslots.server;

import com.reservedslots.ReservedSlotsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Handles persistent storage of reserved slot data across server restarts.
 */
public class ReservedSlotsPersistentState {
    private static final String FILE_NAME = "reservedslots_data.dat";
    private static final Map<MinecraftServer, ReservedSlotsPersistentState> instances = new WeakHashMap<>();
    
    private final Map<String, CompoundTag> playerData = new HashMap<>();
    private final File saveFile;

    private ReservedSlotsPersistentState(MinecraftServer server) {
        // Get the world save directory - using session to get the correct path
        this.saveFile = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME).toFile();
        load();
    }

    public static ReservedSlotsPersistentState get(MinecraftServer server) {
        return instances.computeIfAbsent(server, ReservedSlotsPersistentState::new);
    }
    
    public static void clearCache() {
        instances.clear();
    }

    private void load() {
        ReservedSlotsMod.LOGGER.info("Attempting to load data from: {}", saveFile.getAbsolutePath());
        
        if (!saveFile.exists()) {
            ReservedSlotsMod.LOGGER.info("No saved data found, starting fresh");
            return;
        }

        try {
            ReservedSlotsMod.LOGGER.info("Reading saved data from file...");
            CompoundTag nbt = NbtIo.readCompressed(saveFile.toPath(), NbtAccounter.unlimitedHeap());
            
            var playersNbtOpt = nbt.getCompound("players");
            if (playersNbtOpt.isEmpty()) {
                return;
            }
            
            CompoundTag playersNbt = playersNbtOpt.get();
            for (String playerName : playersNbt.keySet()) {
                var playerNbtOpt = playersNbt.getCompound(playerName);
                if (playerNbtOpt.isPresent()) {
                    playerData.put(playerName, playerNbtOpt.get());
                }
            }
            
            ReservedSlotsMod.LOGGER.info("Loaded reserved slot data for {} players", playerData.size());
        } catch (IOException e) {
            ReservedSlotsMod.LOGGER.error("Failed to load reserved slot data", e);
        }
    }

    public void save() {
        try {
            ReservedSlotsMod.LOGGER.info("Saving data to: {}", saveFile.getAbsolutePath());
            
            CompoundTag nbt = new CompoundTag();
            CompoundTag playersNbt = new CompoundTag();
            
            for (Map.Entry<String, CompoundTag> entry : playerData.entrySet()) {
                playersNbt.put(entry.getKey(), entry.getValue());
            }
            
            nbt.put("players", playersNbt);
            
            // Ensure parent directory exists
            if (!saveFile.getParentFile().exists()) {
                saveFile.getParentFile().mkdirs();
            }
            
            NbtIo.writeCompressed(nbt, saveFile.toPath());
            ReservedSlotsMod.LOGGER.info("Saved reserved slot data for {} players", playerData.size());
        } catch (IOException e) {
            ReservedSlotsMod.LOGGER.error("Failed to save reserved slot data", e);
        }
    }

    public CompoundTag getPlayerData(String playerName) {
        return playerData.get(playerName);
    }

    public void setPlayerData(String playerName, CompoundTag data) {
        if (data == null || data.isEmpty()) {
            playerData.remove(playerName);
        } else {
            playerData.put(playerName, data);
        }
        save();
    }

    public void removePlayerData(String playerName) {
        playerData.remove(playerName);
        save();
    }
}
