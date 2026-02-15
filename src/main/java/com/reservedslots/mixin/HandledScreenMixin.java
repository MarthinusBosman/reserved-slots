package com.reservedslots.mixin;

import com.reservedslots.client.HandledScreenAccessor;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Mixin to expose private fields from HandledScreen for client rendering.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin implements HandledScreenAccessor {
    
    @Shadow
    protected Slot focusedSlot;
    
    @Shadow
    protected int x;
    
    @Shadow
    protected int y;
    
    @Override
    public Slot getHoveredSlot() {
        return focusedSlot;
    }
    
    @Override
    public int getX() {
        return x;
    }
    
    @Override
    public int getY() {
        return y;
    }
}
