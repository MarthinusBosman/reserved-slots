package com.reservedslots.mixin;

import com.reservedslots.config.ModConfig;
import com.reservedslots.server.ReservedSlotManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin to intercept item insertion into player inventory to enforce reserved slot behavior.
 */
@Mixin(Inventory.class)
public abstract class PlayerInventoryMixin {
    @Unique
    private boolean reservedAddedAny = false;
    @Shadow
    public Player player;

    @Shadow
    public abstract ItemStack getItem(int slot);

    @Shadow
    public abstract void setItem(int slot, ItemStack stack);

    @Unique
    private boolean handleReservedInsertion(ItemStack stack) {
        boolean addedAny = false;
        while (!stack.isEmpty()) {
            int targetSlot = ReservedSlotManager.findMatchingReservedOrLockedSlot(player, stack);
            if (targetSlot < 0) {
                break; // No more matching reserved/locked slots available
            }

            ItemStack currentStack = getItem(targetSlot);
            if (currentStack.isEmpty()) {
                setItem(targetSlot, stack.copy());
                stack.setCount(0);
                addedAny = true;
            } else if (ItemStack.isSameItemSameComponents(currentStack, stack) && 
                       currentStack.getCount() < currentStack.getMaxStackSize()) {
                int toAdd = Math.min(stack.getCount(), currentStack.getMaxStackSize() - currentStack.getCount());
                currentStack.grow(toAdd);
                stack.shrink(toAdd);
                addedAny = true;
            } else {
                break;
            }
        }
        return addedAny;
    }

    @Unique
    private boolean handleFallbackInsertion(ItemStack stack) {
        boolean fallbackAdded = false;
        while (!stack.isEmpty()) {
            int targetSlot = ReservedSlotManager.findEmptyReservedSlot(player);
            if (targetSlot < 0) {
                break;
            }

            setItem(targetSlot, stack.copy());
            stack.setCount(0);
            fallbackAdded = true;
        }
        return fallbackAdded;
    }

    /**
     * Intercepts insertStack(ItemStack) - the main method called when picking up items.
     * This is the single-parameter version that automatically finds a slot.
     * 
     * Uses findMatchingReservedOrLockedSlot which handles priority logic for reserved/locked slots.
     * Normal slots are ignored and left for the original method to handle.
     */
    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void onInsertStackAuto(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || player.isSpectator()) {
            return;
        }

        reservedAddedAny = handleReservedInsertion(stack);

        if (stack.isEmpty()) {
            cir.setReturnValue(true);
        }
        // If stack is not empty, do not cancel. Let vanilla `add` run to handle normal slots.
    }

    /**
     * Fallback for when vanilla `add` finishes. If there are still items left,
     * we try to put them in empty reserved slots. Also ensures the correct boolean return value.
     */
    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
    private void onInsertStackAutoReturn(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) {
            if (reservedAddedAny && !cir.getReturnValueZ()) {
                cir.setReturnValue(true);
            }
            return;
        }

        boolean fallbackAdded = handleFallbackInsertion(stack);

        if (fallbackAdded || reservedAddedAny) {
            if (!cir.getReturnValueZ()) {
                cir.setReturnValue(true);
            }
        }
    }

    /**
     * Intercepts placeItemBackInInventory which is called when a UI closes (e.g. Crafting Table).
     */
    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void onPlaceItemBackInInventory1(ItemStack stack, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (stack.isEmpty() || player.isSpectator()) return;
        handleReservedInsertion(stack);
        if (stack.isEmpty()) ci.cancel();
    }

    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void onPlaceItemBackInInventory2(ItemStack stack, boolean notify, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (stack.isEmpty() || player.isSpectator()) return;
        handleReservedInsertion(stack);
        if (stack.isEmpty()) ci.cancel();
    }

    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"), require = 0)
    private void onPlaceItemBackInInventoryReturn1(ItemStack stack, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!stack.isEmpty() && !player.isSpectator()) {
            handleFallbackInsertion(stack);
        }
    }

    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V", at = @At("RETURN"), require = 0)
    private void onPlaceItemBackInInventoryReturn2(ItemStack stack, boolean notify, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!stack.isEmpty() && !player.isSpectator()) {
            handleFallbackInsertion(stack);
        }
    }

    /**
     * Intercepts getEmptySlot to skip locked/reserved slots unless appropriate.
     * This prevents default Minecraft code from using locked/reserved slots.
     * When "Pickup to Inventory" is enabled, main inventory slots (9-35) are preferred
     * over hotbar slots (0-8) for new item pickups.
     */
    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void onGetEmptySlot(CallbackInfoReturnable<Integer> cir) {
        if (ModConfig.getInstance().isPickupToInventory()) {
            // Prefer main inventory (slots 9-35) over hotbar (slots 0-8)
            for (int i = 9; i < 36; i++) {
                ItemStack currentStack = getItem(i);
                if (currentStack.isEmpty() && ReservedSlotManager.isNormalSlot(player, i)) {
                    cir.setReturnValue(i);
                    return;
                }
            }
            for (int i = 0; i < 9; i++) {
                ItemStack currentStack = getItem(i);
                if (currentStack.isEmpty() && ReservedSlotManager.isNormalSlot(player, i)) {
                    cir.setReturnValue(i);
                    return;
                }
            }
        } else {
            // Default: scan slots 0-35 (hotbar first, then inventory)
            for (int i = 0; i < 36; i++) {
                ItemStack currentStack = getItem(i);
                if (currentStack.isEmpty()) {
                    if (ReservedSlotManager.isNormalSlot(player, i)) {
                        cir.setReturnValue(i);
                        return;
                    }
                }
            }
        }

        // No normal empty slots found, return -1
        cir.setReturnValue(-1);
    }
}
