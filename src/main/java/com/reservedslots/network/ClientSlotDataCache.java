package com.reservedslots.network;

import com.reservedslots.ReservedSlotsMod;
import com.reservedslots.common.SlotState;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache of reserved slot data received from the server.
 */
public class ClientSlotDataCache {
    private static final Map<Integer, CachedSlotData> slotData = new HashMap<>();

    public static class CachedSlotData {
        public final SlotState state;
        public final Item item;

        public CachedSlotData(SlotState state, Item item) {
            this.state = state;
            this.item = item;
        }
    }

    public static void setSlotData(int slotIndex, SlotState state, Identifier itemId) {
        Item item = Registries.ITEM.get(itemId);
        slotData.put(slotIndex, new CachedSlotData(state, item));
        ReservedSlotsMod.LOGGER.info("ClientSlotDataCache: Set slot {} to state {} with item {}", slotIndex, state, itemId);
    }

    public static CachedSlotData getSlotData(int slotIndex) {
        return slotData.get(slotIndex);
    }

    public static void clear() {
        slotData.clear();
    }

    public static boolean isReserved(int slotIndex) {
        CachedSlotData data = slotData.get(slotIndex);
        return data != null && data.state == SlotState.RESERVED;
    }

    public static boolean isLocked(int slotIndex) {
        CachedSlotData data = slotData.get(slotIndex);
        return data != null && data.state == SlotState.LOCKED;
    }
}
