package com.reservedslots.client;

import net.minecraft.screen.slot.Slot;

/**
 * Accessor interface for HandledScreen to access private fields.
 */
public interface HandledScreenAccessor {
    Slot getHoveredSlot();
    int getX();
    int getY();
}
