package com.reservedslots.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reservedslots.ReservedSlotsMod;
import com.reservedslots.network.ClientSlotDataCache;
import com.reservedslots.network.ReservedSlotPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side initialization and rendering for reserved slots.
 */
public class ClientInitializer implements ClientModInitializer {
    private static KeyBinding toggleSlotKey;

    @Override
    public void onInitializeClient() {
        ReservedSlotsMod.LOGGER.info("Initializing Reserved Slots Client");
        
        // Register network packets
        ReservedSlotPackets.registerClient();
        
        // Clear client cache when disconnecting from a world
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ReservedSlotsMod.LOGGER.info("Disconnecting from world, clearing client slot cache");
            ClientSlotDataCache.clear();
        });
        
        // Create category during initialization, not as static field
        KeyBinding.Category category = new KeyBinding.Category(Identifier.of("reservedslots", "reserved_slots"));
        
        // Register keybinding
        toggleSlotKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.reservedslots.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            category
        ));
        
        ReservedSlotsMod.LOGGER.info("Keybinding registered successfully");
        
        // Register screen opening event to add keyboard handler
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?> handledScreen) {
                ReservedSlotsMod.LOGGER.info("Registering keyboard handler for screen: {}", screen.getClass().getSimpleName());
                ScreenKeyboardEvents.afterKeyPress(screen).register((scr, keyInput) -> {
                    // Check if the pressed key matches the bound keybinding
                    if (toggleSlotKey.matchesKey(keyInput)) {
                        ReservedSlotsMod.LOGGER.info("=== TOGGLE KEY PRESSED IN SCREEN ===");
                        handleToggleKeyInScreen(handledScreen);
                    }
                });
                
                // Register rendering for this screen
                ScreenEvents.afterRender(screen).register((scr, drawContext, mouseX, mouseY, delta) -> {
                    renderSlotOverlays(drawContext, handledScreen);
                });
            }
        });
        
        // Register tick event for keybinding handling (when not in a screen)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleSlotKey.wasPressed()) {
                ReservedSlotsMod.LOGGER.info("=== TOGGLE KEY PRESSED ===");
                handleToggleKey(client);
            }
        });
        
        // Register HUD rendering for hotbar overlays (ghost items and lock icons)
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.currentScreen == null) {
                renderHotbarOverlays(drawContext, client);
            }
        });
    }

    /**
     * Handles the toggle keybinding press.
     */
    private void handleToggleKey(MinecraftClient client) {
        if (client.player == null || client.currentScreen == null) {
            ReservedSlotsMod.LOGGER.info("Toggle key pressed but player or screen is null");
            return;
        }
        
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) {
            ReservedSlotsMod.LOGGER.info("Toggle key pressed but not in HandledScreen");
            return;
        }
        
        handleToggleKeyInScreen(screen);
    }

    /**
     * Handles the toggle key when pressed in a HandledScreen (inventory).
     */
    private void handleToggleKeyInScreen(HandledScreen<?> screen) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            ReservedSlotsMod.LOGGER.info("Player is null in handleToggleKeyInScreen");
            return;
        }
        
        Slot hoveredSlot = ((HandledScreenAccessor) screen).getHoveredSlot();
        ReservedSlotsMod.LOGGER.info("Hovered slot: {}", hoveredSlot);
        
        if (hoveredSlot == null) {
            ReservedSlotsMod.LOGGER.info("No slot is hovered");
            return;
        }
        
        if (hoveredSlot.inventory != client.player.getInventory()) {
            ReservedSlotsMod.LOGGER.info("Hovered slot is not player inventory: {}", hoveredSlot.inventory.getClass().getSimpleName());
            return;
        }
        
        int slotIndex = hoveredSlot.getIndex();
        ReservedSlotsMod.LOGGER.info("Sending toggle request for slot index {}", slotIndex);
        
        // Send toggle request to server
        ReservedSlotPackets.sendToggleRequest(slotIndex);
    }

    /**
     * Renders overlays for reserved and locked slots.
     */
    private void renderSlotOverlays(DrawContext drawContext, HandledScreen<?> screen) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        
        int screenX = ((HandledScreenAccessor) screen).getX();
        int screenY = ((HandledScreenAccessor) screen).getY();
        
        int overlaysRendered = 0;
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory != client.player.getInventory()) {
                continue;
            }
            
            int slotIndex = slot.getIndex();
            ClientSlotDataCache.CachedSlotData data = ClientSlotDataCache.getSlotData(slotIndex);
            
            if (data != null && data.state != com.reservedslots.common.SlotState.NORMAL) {
                overlaysRendered++;
                int x = screenX + slot.x;
                int y = screenY + slot.y;
                
                // If slot is empty, draw ghost item with transparency
                if (slot.getStack().isEmpty() && data.item != null) {
                    ItemStack ghostStack = new ItemStack(data.item);
                    // Draw the item at full opacity first
                    drawContext.drawItem(ghostStack, x, y);
                    
                    // Draw single black overlay to fade the item (no background darkening)
                    drawContext.fill(x, y, x + 16, y + 16, 0x99323232); // Single layer for ghost
                }
                // Don't draw any overlay when there's an actual item - let it show normally
                
                // Always draw lock icon for locked slots (must be above everything including ghost overlay)
                if (data.state == com.reservedslots.common.SlotState.LOCKED) {
                    // Draw a simple padlock shape
                    int lockX = x + 10;
                    int lockY = y + 1;
                    
                    // Lock body (small rectangle)
                    drawContext.fill(lockX, lockY + 2, lockX + 5, lockY + 6, 0xFF2A2A2A);
                    drawContext.fill(lockX + 1, lockY + 3, lockX + 4, lockY + 5, 0xFF9E9E9E);
                    
                    // Lock shackle (U shape)
                    drawContext.fill(lockX + 1, lockY, lockX + 2, lockY + 3, 0xFF2A2A2A);
                    drawContext.fill(lockX + 3, lockY, lockX + 4, lockY + 3, 0xFF2A2A2A);
                    drawContext.fill(lockX + 1, lockY, lockX + 4, lockY + 1, 0xFF2A2A2A);
                }
            }
        }
    }

    /**
     * Renders overlays for reserved and locked hotbar slots.
     */
    private void renderHotbarOverlays(DrawContext drawContext, MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        // Calculate hotbar position (same as vanilla Minecraft)
        int x = screenWidth / 2 - 91; // Hotbar starts 91 pixels left of center
        int y = screenHeight - 22; // 22 pixels from bottom
        
        // Render overlays for first 9 slots (hotbar = inventory slots 0-8)
        for (int i = 0; i < 9; i++) {
            ClientSlotDataCache.CachedSlotData data = ClientSlotDataCache.getSlotData(i);
            
            if (data != null && data.state != com.reservedslots.common.SlotState.NORMAL) {
                int slotX = x + i * 20 + 3; // Each slot is 20 pixels wide, with 3 pixel padding
                int slotY = y + 3; // 3 pixel padding from top
                
                // If slot is empty, draw ghost item
                ItemStack currentStack = client.player.getInventory().getStack(i);
                if (currentStack.isEmpty() && data.item != null) {
                    ItemStack ghostStack = new ItemStack(data.item);
                    
                    // Draw the item
                    drawContext.drawItem(ghostStack, slotX, slotY);
                    
                    // Draw single black overlay to fade the item (no background darkening)
                    drawContext.fill(slotX, slotY, slotX + 16, slotY + 16, 0x99161616); // Single layer for ghost
                }
                // Don't draw any overlay when there's an actual item - let it show normally
                
                // Draw lock icon for locked slots (must be above ghost overlay)
                if (data.state == com.reservedslots.common.SlotState.LOCKED) {
                    // Draw white square for lock with black center
                    drawContext.fill(slotX + 11, slotY + 1, slotX + 15, slotY + 5, 0xFF2A2A2A);
                    drawContext.fill(slotX + 12, slotY + 2, slotX + 14, slotY + 4, 0xFF9E9E9E);
                }
            }
        }
    }
}
