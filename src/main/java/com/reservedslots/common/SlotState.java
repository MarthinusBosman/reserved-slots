package com.reservedslots.common;

/**
 * Represents the state of a slot in the inventory.
 */
public enum SlotState {
    NORMAL,      // Regular inventory slot
    RESERVED,    // Slot is reserved for a specific item type
    LOCKED       // Slot is locked and cannot accept any items
}
