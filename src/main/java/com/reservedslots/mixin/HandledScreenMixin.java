package com.reservedslots.mixin;

import com.reservedslots.client.HandledScreenAccessor;
import com.reservedslots.network.ClientSlotDataCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to expose private fields from HandledScreen for client rendering.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin implements HandledScreenAccessor {
    
    @Shadow
    protected Slot hoveredSlot;
    
    @Shadow
    protected int leftPos;
    
    @Shadow
    protected int topPos;
    
    @Override
    public Slot getHoveredSlot() {
        return hoveredSlot;
    }
    
    @Override
    public int getX() {
        return leftPos;
    }
    
    @Override
    public int getY() {
        return topPos;
    }
    
    /**
     * Draw dark background for reserved/locked slots BEFORE items are rendered.
     */
    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void onDrawSlotStart(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || slot.container != client.player.getInventory()) {
            return;
        }
        ClientSlotDataCache.CachedSlotData data = ClientSlotDataCache.getSlotData(slot.getContainerSlot());
        if (data != null && data.state != com.reservedslots.common.SlotState.NORMAL) {
            // Only draw background if there's an actual item (not for ghost items)
            if (!slot.getItem().isEmpty()) {
                // Use slot's position, not mouse coordinates
                int slotX = slot.x;
                int slotY = slot.y;
                // Draw dark overlay behind the item
                context.fill(slotX, slotY, slotX + 16, slotY + 16, 0x99323232);
            }
        }
    }
}
