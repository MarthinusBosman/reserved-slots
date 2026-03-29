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

import java.util.List;

/**
 * Mixin to intercept shift-click item transfer (moveItemStackTo) so it respects
 * reserved/locked slot assignments, just like ground pickup does.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuMixin {

    /**
     * Intercepts moveItemStackTo when the destination range contains player inventory
     * slots. Instead of vanilla's sequential slot iteration, we route the item through
     * ReservedSlotManager.findBestSlotForItem so the same priority logic applies as
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
        boolean changed = false;

        while (!stack.isEmpty()) {
            int targetInvSlot = ReservedSlotManager.findBestSlotForItem(player, stack);
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
                changed = true;
            } else if (ItemStack.isSameItemSameComponents(current, stack)
                    && current.getCount() < current.getMaxStackSize()) {
                int toAdd = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
                current.grow(toAdd);
                stack.shrink(toAdd);
                targetSlot.setChanged();
                changed = true;
            } else {
                // findBestSlotForItem returned a slot we can't actually use — stop.
                break;
            }
        }

        cir.setReturnValue(changed);
    }
}
