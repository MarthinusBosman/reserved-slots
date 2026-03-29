package com.reservedslots.client;

import net.minecraft.world.inventory.Slot;

/**
 * Accessor interface for HandledScreen to access private fields.
 */
public interface HandledScreenAccessor {
    Slot getHoveredSlot();
    int getX();
    int getY();
}
