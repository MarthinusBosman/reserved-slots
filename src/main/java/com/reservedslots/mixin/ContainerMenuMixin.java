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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Mixin to intercept shift-click item transfer (moveItemStackTo) so it respects
 * reserved/locked slot assignments, just like ground pickup does.
 *
 * Completely replaces the vanilla method for player-inventory destinations with
 * vanilla-identical logic, adding a canSlotAcceptItem check in the fill pass so
 * locked/reserved slots that don't match the item being moved are skipped.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuMixin {

    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void onMoveItemStackTo(ItemStack stack, int startIndex, int endIndex,
                                   boolean reverseDirection,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) return;

        List<Slot> slots = ((AbstractContainerMenu)(Object)this).slots;

        // Only intercept when the destination range starts with player inventory slots.
        // If the first slot is not a player Inventory, fall through to vanilla (e.g. furnace input).
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

        // Build lookup structures for the destination range.
        // TreeSet gives ascending iteration order, matching the original 0..40 scan order used
        // by findMatchingReservedOrLockedSlotInRange for deterministic priority behaviour.
        Set<Integer> allowedInvSlots = new TreeSet<>();
        Map<Integer, Slot> invToSlot = new HashMap<>();
        for (int i = startIndex; i < endIndex && i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (s.container == playerInventory) {
                int invIdx = s.getContainerSlot();
                allowedInvSlots.add(invIdx);
                invToSlot.put(invIdx, s);
            }
        }

        boolean changed = false;

        // ── Phase 1: Priority routing ──────────────────────────────────────────────
        // Move items into reserved/locked slots that explicitly match the item first,
        // using the same priority order as ground pickup.
        while (!stack.isEmpty()) {
            int targetInvSlot = ReservedSlotManager.findMatchingReservedOrLockedSlotInRange(player, stack, allowedInvSlots);
            if (targetInvSlot < 0) break;

            Slot targetSlot = invToSlot.get(targetInvSlot);
            if (targetSlot == null) break;

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
                break;
            }
        }

        if (stack.isEmpty()) {
            cir.setReturnValue(true);
            return;
        }

        // ── Phase 2: Vanilla-matching logic for remaining items ────────────────────
        // MERGE PASS: top up existing matching stacks (vanilla order).
        // No extra slot-acceptance check needed here: isSameItemSameComponents already
        // ensures we only merge identical items, which any reserved/locked slot would accept.
        if (stack.isStackable()) {
            int i = reverseDirection ? endIndex - 1 : startIndex;
            while (!stack.isEmpty() && (reverseDirection ? i >= startIndex : i < endIndex) && i < slots.size()) {
                Slot slot = slots.get(i);
                ItemStack slotItem = slot.getItem();
                if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(stack, slotItem)) {
                    int maxSize = Math.min(slot.getMaxStackSize(), slotItem.getMaxStackSize());
                    int total = slotItem.getCount() + stack.getCount();
                    if (total <= maxSize) {
                        stack.setCount(0);
                        slotItem.setCount(total);
                        slot.setChanged();
                        changed = true;
                    } else if (slotItem.getCount() < maxSize) {
                        stack.shrink(maxSize - slotItem.getCount());
                        slotItem.setCount(maxSize);
                        slot.setChanged();
                        changed = true;
                    }
                }
                i += reverseDirection ? -1 : 1;
            }
        }

        // FILL PASS: fill the first available empty slot (vanilla order).
        // Reserved/locked slots that don't match the item are skipped so they remain
        // available only for their designated item.
        if (!stack.isEmpty()) {
            int i = reverseDirection ? endIndex - 1 : startIndex;
            while ((reverseDirection ? i >= startIndex : i < endIndex) && i < slots.size()) {
                Slot slot = slots.get(i);
                if (slot.getItem().isEmpty() && slot.mayPlace(stack)) {
                    if (slot.container == playerInventory) {
                        int invSlot = slot.getContainerSlot();
                        if (!ReservedSlotManager.canSlotAcceptItem(player, invSlot, stack)) {
                            i += reverseDirection ? -1 : 1;
                            continue;
                        }
                    }
                    int take = Math.min(stack.getCount(), slot.getMaxStackSize());
                    slot.set(stack.split(take));
                    slot.setChanged();
                    changed = true;
                    break;
                }
                i += reverseDirection ? -1 : 1;
            }
        }

        cir.setReturnValue(changed);
    }
}
