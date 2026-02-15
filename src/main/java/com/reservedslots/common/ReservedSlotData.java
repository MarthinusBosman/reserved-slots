package com.reservedslots.common;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Holds the state and reserved item for a single inventory slot.
 */
public class ReservedSlotData {
    private SlotState state;
    private Item reservedItem; // null if not reserved

    public ReservedSlotData() {
        this(SlotState.NORMAL, null);
    }

    public ReservedSlotData(SlotState state, Item reservedItem) {
        this.state = state;
        this.reservedItem = reservedItem;
    }

    public SlotState getState() {
        return state;
    }

    public void setState(SlotState state) {
        this.state = state;
    }

    public Item getReservedItem() {
        return reservedItem;
    }

    public void setReservedItem(Item item) {
        this.reservedItem = item;
    }

    /**
     * Checks if the given item matches this slot's reservation.
     */
    public boolean matches(ItemStack stack) {
        if (reservedItem == null || stack.isEmpty()) {
            return false;
        }
        return stack.getItem() == reservedItem;
    }

    /**
     * Cycles to the next state: NORMAL -> RESERVED -> LOCKED -> NORMAL
     */
    public void cycleState(Item currentItem) {
        switch (state) {
            case NORMAL:
                state = SlotState.RESERVED;
                reservedItem = currentItem;
                break;
            case RESERVED:
                state = SlotState.LOCKED;
                // Keep the reserved item
                break;
            case LOCKED:
                state = SlotState.NORMAL;
                reservedItem = null;
                break;
        }
    }

    /**
     * Serializes this slot data to NBT.
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("state", state.name());
        if (reservedItem != null) {
            Identifier id = Registries.ITEM.getId(reservedItem);
            nbt.putString("item", id.toString());
        }
        return nbt;
    }

    /**
     * Deserializes slot data from NBT.
     */
    public static ReservedSlotData fromNbt(NbtCompound nbt) {
        SlotState state = SlotState.valueOf(nbt.getString("state").orElse("UNLOCKED"));
        Item item = null;
        if (nbt.contains("item")) {
            String itemString = nbt.getString("item").orElse(null);
            if (itemString != null) {
                Identifier id = Identifier.tryParse(itemString);
                if (id != null) {
                    item = Registries.ITEM.get(id);
                }
            }
        }
        return new ReservedSlotData(state, item);
    }

    public ReservedSlotData copy() {
        return new ReservedSlotData(this.state, this.reservedItem);
    }
}
