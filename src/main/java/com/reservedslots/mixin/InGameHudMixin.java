package com.reservedslots.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Hotbar slot background rendering is handled via HudElementRegistry in ClientInitializer.
 */
@Mixin(Gui.class)
public class InGameHudMixin {
}
