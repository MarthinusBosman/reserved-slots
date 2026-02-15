package com.reservedslots.server;

import com.reservedslots.ReservedSlotsMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;

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
    
    private final Map<String, NbtCompound> playerData = new HashMap<>();
    private final File saveFile;

    private ReservedSlotsPersistentState(MinecraftServer server) {
        // Get the world save directory - using session to get the correct path
        File worldDir = server.getRunDirectory().resolve("saves").resolve(server.getSaveProperties().getLevelName()).toFile();
        this.saveFile = new File(worldDir, FILE_NAME);
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
            NbtCompound nbt = NbtIo.readCompressed(saveFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
            
            var playersNbtOpt = nbt.getCompound("players");
            if (playersNbtOpt.isEmpty()) {
                return;
            }
            
            NbtCompound playersNbt = playersNbtOpt.get();
            for (String playerName : playersNbt.getKeys()) {
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
            
            NbtCompound nbt = new NbtCompound();
            NbtCompound playersNbt = new NbtCompound();
            
            for (Map.Entry<String, NbtCompound> entry : playerData.entrySet()) {
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

    public NbtCompound getPlayerData(String playerName) {
        return playerData.get(playerName);
    }

    public void setPlayerData(String playerName, NbtCompound data) {
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
