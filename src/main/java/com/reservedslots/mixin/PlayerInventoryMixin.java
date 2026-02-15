package com.reservedslots.mixin;

import com.reservedslots.server.ReservedSlotManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept item insertion into player inventory to enforce reserved slot behavior.
 */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
    @Shadow
    public PlayerEntity player;

    @Shadow
    public abstract ItemStack getStack(int slot);

    @Shadow
    public abstract void setStack(int slot, ItemStack stack);

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
    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void onInsertStackAuto(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || player.isSpectator()) {
            return;
        }

        // Find the best slot using our custom logic
        int targetSlot = ReservedSlotManager.findBestSlotForItem(player, stack);
        
        if (targetSlot >= 0) {
            ItemStack currentStack = getStack(targetSlot);
            
            if (currentStack.isEmpty()) {
                // Empty slot - insert the item
                setStack(targetSlot, stack.copy());
                stack.setCount(0);
                cir.setReturnValue(true);
                return;
            } else if (ItemStack.areItemsAndComponentsEqual(currentStack, stack) && 
                       currentStack.getCount() < currentStack.getMaxCount()) {
                // Can stack with existing item
                int toAdd = Math.min(stack.getCount(), currentStack.getMaxCount() - currentStack.getCount());
                currentStack.increment(toAdd);
                stack.decrement(toAdd);
                cir.setReturnValue(!stack.isEmpty()); // Return false if more items remain
                return;
            }
        }
        
        // No slot available - inventory is full
        cir.setReturnValue(false);
    }

    /**
     * Prevents items from being placed in locked slots during normal operations.
     */
    @Inject(method = "getOccupiedSlotWithRoomForStack", at = @At("HEAD"), cancellable = true)
    private void onGetOccupiedSlot(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        // This helps prevent locked slots from being used for stacking
        for (int i = 0; i < 36; i++) {
            ItemStack currentStack = getStack(i);
            
            if (!currentStack.isEmpty() && 
                ItemStack.areItemsAndComponentsEqual(currentStack, stack) && 
                currentStack.getCount() < currentStack.getMaxCount()) {
                
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
    @Inject(method = "getEmptySlot", at = @At("HEAD"), cancellable = true)
    private void onGetEmptySlot(CallbackInfoReturnable<Integer> cir) {
        // Find first empty slot that isn't locked or inappropriately reserved
        for (int i = 0; i < 36; i++) {
            ItemStack currentStack = getStack(i);
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
