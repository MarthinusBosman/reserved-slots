package com.reservedslots.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.reservedslots.ReservedSlotsMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores and persists mod settings as a JSON file in the config directory.
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("reservedslots.json");

    private static ModConfig instance;

    // Settings
    private boolean pickupToInventory = false;

    public static ModConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public boolean isPickupToInventory() {
        return pickupToInventory;
    }

    public void setPickupToInventory(boolean value) {
        this.pickupToInventory = value;
    }

    private static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (IOException e) {
                ReservedSlotsMod.LOGGER.error("Failed to load config", e);
            }
        }
        return new ModConfig();
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            ReservedSlotsMod.LOGGER.error("Failed to save config", e);
        }
    }
}
