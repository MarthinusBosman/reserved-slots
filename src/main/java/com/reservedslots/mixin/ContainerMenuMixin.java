package com.reservedslots.mixin;

import com.reservedslots.server.ReservedSlotManager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.spongepowered.asm.mixin.Unique;

import java.util.List;

/**
 * Mixin to intercept shift-click item transfer (moveItemStackTo) so it respects
 * reserved/locked slot assignments, just like ground pickup does.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuMixin {
    @Unique
    private boolean reservedMovedAny = false;

    /**
     * Intercepts moveItemStackTo when the destination range contains player inventory
     * slots. Instead of vanilla's sequential slot iteration, we route the item through
     * ReservedSlotManager.findMatchingReservedOrLockedSlot so the same priority logic applies as
     * when picking items up from the ground.
     *
     * We only take over when ALL destination slots belong to a player inventory (i.e.
     * the player is shift-clicking something OUT of a container INTO their inventory).
     * Other directions (inventory → container, hotbar ↔ main inventory swaps) are left
     * to vanilla so they work normally.
     */
    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void onMoveItemStackTo(ItemStack stack, int startIndex, int endIndex,
                                   boolean reverseDirection,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) return;

        reservedMovedAny = false;

        // Access slots via cast to avoid @Shadow refMap dependency.
        List<Slot> slots = ((AbstractContainerMenu)(Object)this).slots;

        // Identify whether the destination range is entirely player inventory slots.
        Inventory playerInventory = null;
        for (int i = startIndex; i < endIndex && i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof Inventory inv) {
                playerInventory = inv;
                break;
            } else {
                // At least one destination slot is NOT a player inventory slot —
                // let vanilla handle the whole call (e.g. inventory → furnace direction).
                return;
            }
        }

        if (playerInventory == null) return;

        Player player = playerInventory.player;

        while (!stack.isEmpty()) {
            int targetInvSlot = ReservedSlotManager.findMatchingReservedOrLockedSlot(player, stack);
            if (targetInvSlot < 0) break;

            // Find the matching container slot so we can use slot.set() for proper sync.
            // Use getContainerSlot() (backing inventory index), NOT slot.index (container position).
            Slot targetSlot = null;
            for (int i = startIndex; i < endIndex && i < slots.size(); i++) {
                Slot s = slots.get(i);
                if (s.container == playerInventory && s.getContainerSlot() == targetInvSlot) {
                    targetSlot = s;
                    break;
                }
            }

            if (targetSlot == null) {
                // Best slot is outside the allowed range — stop here.
                break;
            }

            ItemStack current = targetSlot.getItem();
            if (current.isEmpty()) {
                int take = Math.min(stack.getCount(), targetSlot.getMaxStackSize());
                targetSlot.set(stack.split(take));
                reservedMovedAny = true;
            } else if (ItemStack.isSameItemSameComponents(current, stack)
                    && current.getCount() < current.getMaxStackSize()) {
                int toAdd = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
                current.grow(toAdd);
                stack.shrink(toAdd);
                targetSlot.setChanged();
                reservedMovedAny = true;
            } else {
                // findMatchingReservedOrLockedSlot returned a slot we can't actually use — stop.
                break;
            }
        }

        if (stack.isEmpty()) {
            cir.setReturnValue(true);
        }
        // If stack is not empty, do not cancel. Let vanilla `moveItemStackTo` run to handle normal slots.
    }

    /**
     * Fallback for when vanilla `moveItemStackTo` finishes. If there are still items left,
     * we try to put them in empty reserved slots. Also ensures the correct boolean return value.
     */
    @Inject(method = "moveItemStackTo", at = @At("RETURN"), cancellable = true)
    private void onMoveItemStackToReturn(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) {
            if (reservedMovedAny && !cir.getReturnValueZ()) {
                cir.setReturnValue(true);
            }
            return;
        }

        // Access slots via cast to avoid @Shadow refMap dependency.
        List<Slot> slots = ((AbstractContainerMenu)(Object)this).slots;

        // Identify whether the destination range is entirely player inventory slots.
        Inventory playerInventory = null;
        for (int i = startIndex; i < endIndex && i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof Inventory inv) {
                playerInventory = inv;
                break;
            } else {
                return;
            }
        }

        if (playerInventory == null) return;
        Player player = playerInventory.player;
        boolean fallbackMoved = false;

        while (!stack.isEmpty()) {
            int targetInvSlot = ReservedSlotManager.findEmptyReservedSlot(player);
            if (targetInvSlot < 0) break;

            Slot targetSlot = null;
            for (int i = startIndex; i < endIndex && i < slots.size(); i++) {
                Slot s = slots.get(i);
                if (s.container == playerInventory && s.getContainerSlot() == targetInvSlot) {
                    targetSlot = s;
                    break;
                }
            }

            if (targetSlot == null) break;

            ItemStack current = targetSlot.getItem();
            if (current.isEmpty()) {
                int take = Math.min(stack.getCount(), targetSlot.getMaxStackSize());
                targetSlot.set(stack.split(take));
                fallbackMoved = true;
            } else {
                break;
            }
        }

        if (fallbackMoved || reservedMovedAny) {
            if (!cir.getReturnValueZ()) {
                cir.setReturnValue(true);
            }
        }
    }
}
