package com.reservedslots.mixin;

import com.reservedslots.network.ClientSlotDataCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to draw dark backgrounds for reserved/locked hotbar slots BEFORE items are rendered.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    
    private boolean hasDrawnBackgrounds = false;
    
    /**
     * Inject at the start of renderHotbar to draw backgrounds at the right time.
     * We set a flag and draw during the first pass.
     */
    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void onRenderHotbarStart(DrawContext drawContext, RenderTickCounter tickCounter, CallbackInfo ci) {
        hasDrawnBackgrounds = false;
    }
    
    /**
     * Inject right before the FIRST hotbar item is drawn to render backgrounds.
     * This ensures backgrounds are drawn after the hotbar texture but before items.
     */
    @Inject(method = "renderHotbar", at = @At(value = "INVOKE", 
            target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHotbarItem(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;I)V",
            ordinal = 0))
    private void onBeforeFirstHotbarItem(DrawContext drawContext, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (hasDrawnBackgrounds) {
            return;
        }
        hasDrawnBackgrounds = true;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        // Calculate hotbar position (same as vanilla Minecraft)
        int x = screenWidth / 2 - 91; // Hotbar starts 91 pixels left of center
        int y = screenHeight - 22; // 22 pixels from bottom
        
        // Draw backgrounds for first 9 slots (hotbar = inventory slots 0-8)
        for (int i = 0; i < 9; i++) {
            ClientSlotDataCache.CachedSlotData data = ClientSlotDataCache.getSlotData(i);
            
            if (data != null && data.state != com.reservedslots.common.SlotState.NORMAL) {
                ItemStack currentStack = client.player.getInventory().getStack(i);
                // Only draw background if there's an actual item
                if (!currentStack.isEmpty()) {
                    int slotX = x + i * 20 + 3; // Each slot is 20 pixels wide, with 3 pixel padding
                    int slotY = y + 3; // 3 pixel padding from top
                    // Draw semi-transparent dark background behind the item (lighter for hotbar)
                    drawContext.fill(slotX, slotY, slotX + 16, slotY + 16, 0x99323232);
                }
            }
        }
    }
}
