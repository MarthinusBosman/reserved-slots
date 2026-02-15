package com.reservedslots.server;

import com.reservedslots.ReservedSlotsMod;
import com.reservedslots.common.ReservedSlotData;
import com.reservedslots.common.SlotState;
import com.reservedslots.network.ReservedSlotPackets;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages reserved slot data for all players on the server.
 * Handles persistence, synchronization, and slot operations.
 */
public class ReservedSlotManager {
    private static final String NBT_KEY = "ReservedSlots";
    private static final int PLAYER_INVENTORY_SIZE = 41; // 36 inventory + 4 armor + 1 offhand

    // Per-player slot data (UUID -> slot index -> ReservedSlotData)
    // This is per-world automatically since each world loads from separate player NBT files
    private static final Map<UUID, Map<Integer, ReservedSlotData>> playerData = new HashMap<>();

    /**
     * Gets the slot data for a specific player and slot index.
     */
    public static ReservedSlotData getSlotData(UUID playerId, int slotIndex) {
        return playerData
                .computeIfAbsent(playerId, k -> new HashMap<>())
                .computeIfAbsent(slotIndex, k -> new ReservedSlotData());
    }
    
    /**
     * Gets the slot data for a specific player and slot index (with player context).
     */
    public static ReservedSlotData getSlotData(PlayerEntity player, int slotIndex) {
        return getSlotData(player.getUuid(), slotIndex);
    }

    /**
     * Sets the slot data for a specific player and slot index.
     */
    public static void setSlotData(UUID playerId, int slotIndex, ReservedSlotData data) {
        playerData
                .computeIfAbsent(playerId, k -> new HashMap<>())
                .put(slotIndex, data);
    }

    /**
     * Toggles the state of a slot (NORMAL -> RESERVED -> LOCKED -> NORMAL).
     */
    public static void toggleSlot(ServerPlayerEntity player, int slotIndex) {
        ReservedSlotsMod.LOGGER.info("toggleSlot called for player {} slot {}", player.getName().getString(), slotIndex);
        
        ReservedSlotData slotData = getSlotData(player.getUuid(), slotIndex);
        
        ItemStack stack = player.getInventory().getStack(slotIndex);
        ReservedSlotsMod.LOGGER.info("Current slot state: {}, has item: {}", slotData.getState(), !stack.isEmpty());
        
        // Only allow toggling if there's an item in the slot (for NORMAL->RESERVED transition)
        // or if already in RESERVED/LOCKED state
        if (slotData.getState() == SlotState.NORMAL && stack.isEmpty()) {
            ReservedSlotsMod.LOGGER.info("Cannot reserve empty slot");
            return; // Can't reserve an empty slot
        }
        
        slotData.cycleState(stack.getItem());
        setSlotData(player.getUuid(), slotIndex, slotData);
        
        ReservedSlotsMod.LOGGER.info("New slot state: {}", slotData.getState());
        
        // Sync to client
        ReservedSlotPackets.sendSyncPacket(player, slotIndex, slotData);
        
        ReservedSlotsMod.LOGGER.info("Player {} toggled slot {} to state {} with item {}", 
            player.getName().getString(), slotIndex, slotData.getState(), 
            slotData.getReservedItem() != null ? slotData.getReservedItem() : "none");
    }

    /**
     * Finds the best slot for inserting an item, considering reserved/locked slots.
     * Returns -1 if no suitable slot is found.
     * 
     * Priority order:
     * 1. Stack with existing items in locked slots (if matching)
     * 2. Stack with existing items in reserved slots (if matching)
     * 3. Stack with existing items in normal slots
     * 4. Empty locked slot (if matching)
     * 5. Empty reserved slot (if matching)
     * 6. Empty normal slot
     * 7. Empty reserved slot (fallback for non-matching items when no normal slots)
     */
    public static int findBestSlotForItem(PlayerEntity player, ItemStack stack) {
        UUID playerId = player.getUuid();
        Map<Integer, ReservedSlotData> slots = playerData.get(playerId);
        
        // Phase 1: Try to stack with existing items
        // Priority: locked (matching) > reserved (matching) > normal
        
        // 1a. Stack into locked slots with matching items
        if (slots != null) {
            for (int i = 0; i < 36; i++) {
                ReservedSlotData data = slots.get(i);
                if (data != null && data.getState() == SlotState.LOCKED && data.matches(stack)) {
                    ItemStack currentStack = player.getInventory().getStack(i);
                    if (!currentStack.isEmpty() &&
                        ItemStack.areItemsAndComponentsEqual(currentStack, stack) &&
                        currentStack.getCount() < currentStack.getMaxCount()) {
                        return i;
                    }
                }
            }
        }
        
        // 1b. Stack into reserved slots with matching items
        if (slots != null) {
            for (int i = 0; i < 36; i++) {
                ReservedSlotData data = slots.get(i);
                if (data != null && data.getState() == SlotState.RESERVED && data.matches(stack)) {
                    ItemStack currentStack = player.getInventory().getStack(i);
                    if (!currentStack.isEmpty() &&
                        ItemStack.areItemsAndComponentsEqual(currentStack, stack) &&
                        currentStack.getCount() < currentStack.getMaxCount()) {
                        return i;
                    }
                }
            }
        }
        
        // 1c. Stack into normal slots
        for (int i = 0; i < 36; i++) {
            ReservedSlotData data = slots != null ? slots.get(i) : null;
            if (data == null || data.getState() == SlotState.NORMAL) {
                ItemStack currentStack = player.getInventory().getStack(i);
                if (!currentStack.isEmpty() &&
                    ItemStack.areItemsAndComponentsEqual(currentStack, stack) &&
                    currentStack.getCount() < currentStack.getMaxCount()) {
                    return i;
                }
            }
        }
        
        // Phase 2: Try to find empty slots
        // Priority: locked (matching) > reserved (matching) > normal > reserved (fallback)
        
        // 2a. Empty locked slot for matching item
        if (slots != null) {
            for (int i = 0; i < 36; i++) {
                ReservedSlotData data = slots.get(i);
                if (data != null && data.getState() == SlotState.LOCKED && data.matches(stack)) {
                    ItemStack currentStack = player.getInventory().getStack(i);
                    if (currentStack.isEmpty()) {
                        return i;
                    }
                }
            }
        }
        
        // 2b. Empty reserved slot for matching item
        if (slots != null) {
            for (int i = 0; i < 36; i++) {
                ReservedSlotData data = slots.get(i);
                if (data != null && data.getState() == SlotState.RESERVED && data.matches(stack)) {
                    ItemStack currentStack = player.getInventory().getStack(i);
                    if (currentStack.isEmpty()) {
                        return i;
                    }
                }
            }
        }
        
        // 2c. Empty normal slot
        for (int i = 0; i < 36; i++) {
            ReservedSlotData data = slots != null ? slots.get(i) : null;
            if (data == null || data.getState() == SlotState.NORMAL) {
                ItemStack currentStack = player.getInventory().getStack(i);
                if (currentStack.isEmpty()) {
                    return i;
                }
            }
        }
        
        // 2d. Empty reserved slot as fallback (non-matching item, no normal slots available)
        if (slots != null) {
            for (int i = 0; i < 36; i++) {
                ReservedSlotData data = slots.get(i);
                if (data != null && data.getState() == SlotState.RESERVED) {
                    ItemStack currentStack = player.getInventory().getStack(i);
                    if (currentStack.isEmpty()) {
                        return i; // Fallback: use reserved slot for non-matching item
                    }
                }
            }
        }
        
        return -1; // No slot available
    }

    /**
     * Checks if a slot can accept an item.
     * 
     * Logic:
     * - NORMAL: accepts any item
     * - RESERVED: accepts matching items, or any item if no unreserved slots available
     * - LOCKED: ONLY accepts matching items (strict enforcement)
     */
    public static boolean canSlotAcceptItem(PlayerEntity player, int slotIndex, ItemStack stack) {
        ReservedSlotData data = getSlotData(player.getUuid(), slotIndex);
        
        switch (data.getState()) {
            case NORMAL:
                return true; // Normal slots accept anything
            case RESERVED:
                // Reserved slots accept matching items
                // OR any item if there are no unreserved empty slots available
                if (data.matches(stack)) {
                    return true;
                }
                // Check if inventory is full (excluding reserved/locked slots)
                return isInventoryFullExcludingReserved(player);
            case LOCKED:
                // Locked slots ONLY accept their specific item, no exceptions
                return data.matches(stack);
            default:
                return true;
        }
    }

    /**
     * Checks if a slot is in NORMAL state (not reserved or locked).
     */
    public static boolean isNormalSlot(PlayerEntity player, int slotIndex) {
        ReservedSlotData data = getSlotData(player.getUuid(), slotIndex);
        return data.getState() == SlotState.NORMAL;
    }

    /**
     * Checks if inventory is full (excluding reserved/locked slots).
     */
    public static boolean isInventoryFullExcludingReserved(PlayerEntity player) {
        UUID playerId = player.getUuid();
        Map<Integer, ReservedSlotData> slots = playerData.get(playerId);
        
        int availableSlots = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                // Check if this slot is locked
                ReservedSlotData data = slots != null ? slots.get(i) : null;
                if (data == null || data.getState() != SlotState.LOCKED) {
                    availableSlots++;
                }
            }
        }
        
        return availableSlots == 0;
    }

    /**
     * Loads player data from NBT.
     */
    public static void loadPlayerData(ServerPlayerEntity player, NbtCompound nbt) {
        UUID playerId = player.getUuid();
        
        // Always clear existing data first to ensure we start fresh for this world
        playerData.remove(playerId);
        
        if (nbt.contains(NBT_KEY, NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList(NBT_KEY, NbtElement.COMPOUND_TYPE);
            Map<Integer, ReservedSlotData> slots = new HashMap<>();
            
            for (int i = 0; i < list.size(); i++) {
                NbtCompound slotNbt = list.getCompound(i);
                int index = slotNbt.getInt("index");
                ReservedSlotData data = ReservedSlotData.fromNbt(slotNbt.getCompound("data"));
                slots.put(index, data);
            }
            
            playerData.put(playerId, slots);
            ReservedSlotsMod.LOGGER.info("Loaded {} reserved slots for player {}", 
                slots.size(), player.getName().getString());
        } else {
            ReservedSlotsMod.LOGGER.info("No reserved slots found for player {} (new world or no saved data)", 
                player.getName().getString());
        }
        
        // Note: Don't sync here - player's network handler isn't ready yet
        // Sync will happen in ServerPlayConnectionEvents.JOIN
    }

    /**
     * Saves player data to NBT.
     */
    public static void savePlayerData(ServerPlayerEntity player, NbtCompound nbt) {
        Map<Integer, ReservedSlotData> slots = playerData.get(player.getUuid());
        
        if (slots == null || slots.isEmpty()) {
            return;
        }
        
        NbtList list = new NbtList();
        
        for (Map.Entry<Integer, ReservedSlotData> entry : slots.entrySet()) {
            // Only save non-normal slots
            if (entry.getValue().getState() != SlotState.NORMAL) {
                NbtCompound slotNbt = new NbtCompound();
                slotNbt.putInt("index", entry.getKey());
                slotNbt.put("data", entry.getValue().toNbt());
                list.add(slotNbt);
            }
        }
        
        nbt.put(NBT_KEY, list);
    }

    /**
     * Clears all data for a player (when they disconnect).
     */
    public static void clearPlayerData(UUID playerId) {
        playerData.remove(playerId);
    }
    
    /**
     * Clears all player data (when server stops/world changes).
     */
    public static void clearAllPlayerData() {
        playerData.clear();
        ReservedSlotsMod.LOGGER.info("Cleared all reserved slot data from server memory");
    }

    /**
     * Gets all slot data for a player (for syncing).
     */
    public static Map<Integer, ReservedSlotData> getAllSlotData(UUID playerId) {
        return playerData.getOrDefault(playerId, new HashMap<>());
    }

    /**
     * Syncs all reserved slots to a player after they join.
     */
    public static void syncToPlayer(ServerPlayerEntity player) {
        Map<Integer, ReservedSlotData> slots = playerData.get(player.getUuid());
        if (slots == null) {
            slots = new HashMap<>();
        }
        ReservedSlotsMod.LOGGER.info("Syncing {} reserved slots to player {}", 
            slots.size(), player.getName().getString());
        ReservedSlotPackets.sendFullSyncPacket(player, slots);
    }
}
