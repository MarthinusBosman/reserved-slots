package com.reservedslots.client;

import com.reservedslots.config.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Config screen for the Reserved Slots mod, accessible via the settings keybinding.
 */
public class ReservedSlotsConfigScreen extends Screen {
    private final Screen parent;

    public ReservedSlotsConfigScreen(Screen parent) {
        super(Component.translatable("screen.reservedslots.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(
            Button.builder(getPickupToInventoryLabel(), button -> {
                ModConfig config = ModConfig.getInstance();
                config.setPickupToInventory(!config.isPickupToInventory());
                config.save();
                button.setMessage(getPickupToInventoryLabel());
            })
            .bounds(centerX - 100, centerY - 20, 200, 20)
            .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.done"), button -> {
                this.minecraft.setScreen(parent);
            })
            .bounds(centerX - 75, centerY + 10, 150, 20)
            .build()
        );
    }

    private Component getPickupToInventoryLabel() {
        boolean enabled = ModConfig.getInstance().isPickupToInventory();
        return Component.translatable(
            "option.reservedslots.pickupToInventory",
            Component.translatable(enabled ? "options.on" : "options.off")
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
