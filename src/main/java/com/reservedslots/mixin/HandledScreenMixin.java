package com.reservedslots.mixin;

import com.reservedslots.client.HandledScreenAccessor;
import com.reservedslots.network.ClientSlotDataCache;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
    
    /**
     * Draw dark background for reserved/locked slots BEFORE items are rendered.
     */
    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void onDrawSlotStart(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ClientSlotDataCache.CachedSlotData data = ClientSlotDataCache.getSlotData(slot.getIndex());
        if (data != null && data.state != com.reservedslots.common.SlotState.NORMAL) {
            // Only draw background if there's an actual item (not for ghost items)
            if (!slot.getStack().isEmpty()) {
                // Use slot's position, not mouse coordinates
                int slotX = slot.x;
                int slotY = slot.y;
                // Draw dark overlay behind the item
                context.fill(slotX, slotY, slotX + 16, slotY + 16, 0x99323232);
            }
        }
    }
}
