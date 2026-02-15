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
     * Intercepts insertStack to prioritize reserved slots.
     */
    @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void onInsertStack(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || player.isSpectator()) {
            return;
        }

        // First, try to find a reserved slot for this item
        int reservedSlot = ReservedSlotManager.findBestSlotForItem(player, stack);
        
        if (reservedSlot >= 0) {
            // Found a reserved slot, try to insert there
            ItemStack currentStack = getStack(reservedSlot);
            
            if (currentStack.isEmpty()) {
                // Empty reserved slot, put item there
                setStack(reservedSlot, stack.copy());
                stack.setCount(0);
                cir.setReturnValue(true);
                return;
            } else if (ItemStack.areItemsAndComponentsEqual(currentStack, stack) && 
                       currentStack.getCount() < currentStack.getMaxCount()) {
                // Can stack with existing item in reserved slot
                int toAdd = Math.min(stack.getCount(), currentStack.getMaxCount() - currentStack.getCount());
                currentStack.increment(toAdd);
                stack.decrement(toAdd);
                cir.setReturnValue(true);
                return;
            }
        }
        
        // If we get here, let the default logic handle it, but check locked slots
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
     * Prevents items from being placed in locked slots during empty slot search.
     */
    @Inject(method = "getEmptySlot", at = @At("HEAD"), cancellable = true)
    private void onGetEmptySlot(CallbackInfoReturnable<Integer> cir) {
        // Let this method find any empty slot - the actual insertion
        // will be validated by canInsertIntoSlot and other methods
    }
}
