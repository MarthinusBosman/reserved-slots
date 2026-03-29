package com.reservedslots.mixin;

import com.reservedslots.server.ReservedSlotManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept item insertion into player inventory to enforce reserved slot behavior.
 */
@Mixin(Inventory.class)
public abstract class PlayerInventoryMixin {
    @Shadow
    public Player player;

    @Shadow
    public abstract ItemStack getItem(int slot);

    @Shadow
    public abstract void setItem(int slot, ItemStack stack);

    /**
     * Intercepts insertStack(ItemStack) - the main method called when picking up items.
     * This is the single-parameter version that automatically finds a slot.
     * 
     * Uses findBestSlotForItem which handles all priority logic:
     * - Locked slots get items first (if matching)
     * - Reserved slots get items second (if matching)
     * - Normal slots get items third
     * - Reserved slots used as fallback (if no normal slots available)
     */
    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void onInsertStackAuto(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || player.isSpectator()) {
            return;
        }

        // Find the best slot using our custom logic
        int targetSlot = ReservedSlotManager.findBestSlotForItem(player, stack);
        
        if (targetSlot >= 0) {
            ItemStack currentStack = getItem(targetSlot);
            
            if (currentStack.isEmpty()) {
                // Empty slot - insert the item
                setItem(targetSlot, stack.copy());
                stack.setCount(0);
                cir.setReturnValue(true);
                return;
            } else if (ItemStack.isSameItemSameComponents(currentStack, stack) && 
                       currentStack.getCount() < currentStack.getMaxStackSize()) {
                // Can stack with existing item
                int toAdd = Math.min(stack.getCount(), currentStack.getMaxStackSize() - currentStack.getCount());
                currentStack.grow(toAdd);
                stack.shrink(toAdd);
                cir.setReturnValue(true); // Items were successfully added; triggers pickup sound/animation
                return;
            }
        }
        
        // No slot available - inventory is full
        cir.setReturnValue(false);
    }

    /**
     * Prevents items from being placed in locked slots during normal operations.
     */
    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void onGetOccupiedSlot(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        // This helps prevent locked slots from being used for stacking
        for (int i = 0; i < 36; i++) {
            ItemStack currentStack = getItem(i);
            
            if (!currentStack.isEmpty() && 
                ItemStack.isSameItemSameComponents(currentStack, stack) && 
                currentStack.getCount() < currentStack.getMaxStackSize()) {
                
                // Check if this slot can accept the item
                if (!ReservedSlotManager.canSlotAcceptItem(player, i, stack)) {
                    continue; // Skip locked slots
                }
                
                cir.setReturnValue(i);
                return;
            }
        }
    }

    /**
     * Intercepts getEmptySlot to skip locked/reserved slots unless appropriate.
     * This prevents default Minecraft code from using locked/reserved slots.
     */
    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void onGetEmptySlot(CallbackInfoReturnable<Integer> cir) {
        // Find first empty slot that isn't locked or inappropriately reserved
        for (int i = 0; i < 36; i++) {
            ItemStack currentStack = getItem(i);
            if (currentStack.isEmpty()) {
                // Check if this is a normal slot (not reserved or locked)
                if (ReservedSlotManager.isNormalSlot(player, i)) {
                    cir.setReturnValue(i);
                    return;
                }
            }
        }
        
        // No normal empty slots found, return -1
        cir.setReturnValue(-1);
    }
}
