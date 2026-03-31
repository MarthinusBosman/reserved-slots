package com.reservedslots.mixin;

import com.reservedslots.ReservedSlotsMod;
import com.reservedslots.common.ReservedSlotData;
import com.reservedslots.common.SlotState;
import com.reservedslots.server.ReservedSlotManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * Mixin to enforce reserved slot rules when shift-clicking items from containers
 * (furnaces, workbenches, etc.) into the player inventory.
 *
 * Two-part approach:
 * 1. HEAD inject on insertItem: pre-fills matching reserved/locked slots FIRST,
 *    before vanilla iterates sequentially (which would otherwise fill a normal
 *    slot at index 0 before reaching the reserved slot at a higher index).
 * 2. Redirect on canInsert: blocks non-matching items from going into
 *    reserved/locked slots during vanilla's sequential pass.
 */
@Mixin(ScreenHandler.class)
public class ScreenHandlerMixin {

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void prioritizeReservedSlots(ItemStack stack, int startIndex, int endIndex,
                                         boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        // Access slots via cast — avoids @Shadow type-mismatch (actual field type is DefaultedList<Slot>)
        List<Slot> handlerSlots = ((ScreenHandler) (Object) this).slots;

        // Find the ServerPlayerEntity behind any player-inventory slot in the range
        ServerPlayerEntity serverPlayer = null;
        for (int i = startIndex; i < endIndex && i < handlerSlots.size(); i++) {
            Slot s = handlerSlots.get(i);
            if (s.inventory instanceof PlayerInventory pi
                    && pi.player instanceof ServerPlayerEntity sp) {
                serverPlayer = sp;
                break;
            }
        }
        if (serverPlayer == null) {
            return; // No player inventory slots in range — nothing to do
        }

        ReservedSlotsMod.LOGGER.info("[ReservedSlots] insertItem called: stack={} start={} end={} fromLast={}",
                stack.getItem(), startIndex, endIndex, fromLast);

        Map<Integer, ReservedSlotData> playerSlotData =
                ReservedSlotManager.getAllSlotData(serverPlayer.getUuid());

        boolean changedSomething = false;

        // Pass A: stack into existing items in locked then reserved matching slots
        for (SlotState targetState : new SlotState[]{SlotState.LOCKED, SlotState.RESERVED}) {
            for (int i = startIndex; i < endIndex && i < handlerSlots.size(); i++) {
                int idx = fromLast ? (endIndex - 1 - (i - startIndex)) : i;
                Slot slot = handlerSlots.get(idx);
                if (!(slot.inventory instanceof PlayerInventory)) continue;
                int invIndex = slot.getIndex();
                ReservedSlotData data = playerSlotData.get(invIndex);
                if (data == null || data.getState() != targetState || !data.matches(stack)) continue;
                ItemStack current = slot.getStack();
                if (current.isEmpty() || !ItemStack.areItemsAndComponentsEqual(current, stack)) continue;
                if (current.getCount() >= current.getMaxCount()) continue;

                int toAdd = Math.min(stack.getCount(), current.getMaxCount() - current.getCount());
                current.increment(toAdd);
                stack.decrement(toAdd);
                slot.markDirty();
                changedSomething = true;
                ReservedSlotsMod.LOGGER.info("[ReservedSlots] Stacked {} into {} slot invIndex={} remaining={}",
                        toAdd, targetState, invIndex, stack.getCount());
                if (stack.isEmpty()) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        // Pass B: fill empty locked then reserved matching slots
        for (SlotState targetState : new SlotState[]{SlotState.LOCKED, SlotState.RESERVED}) {
            for (int i = startIndex; i < endIndex && i < handlerSlots.size(); i++) {
                int idx = fromLast ? (endIndex - 1 - (i - startIndex)) : i;
                Slot slot = handlerSlots.get(idx);
                if (!(slot.inventory instanceof PlayerInventory)) continue;
                int invIndex = slot.getIndex();
                ReservedSlotData data = playerSlotData.get(invIndex);
                if (data == null || data.getState() != targetState || !data.matches(stack)) continue;
                if (!slot.getStack().isEmpty()) continue;
                if (!slot.canInsert(stack)) continue;

                int maxCount = Math.min(slot.getMaxItemCount(stack), stack.getMaxCount());
                int toAdd = Math.min(stack.getCount(), maxCount);
                slot.setStack(stack.copyWithCount(toAdd));
                slot.markDirty();
                stack.decrement(toAdd);
                changedSomething = true;
                ReservedSlotsMod.LOGGER.info("[ReservedSlots] Inserted {} into empty {} slot invIndex={} remaining={}",
                        toAdd, targetState, invIndex, stack.getCount());
                if (stack.isEmpty()) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        // Items remain — let vanilla insertItem handle the rest (normal slots).
        // The canInsert redirect below prevents remaining items from entering reserved/locked slots.
        if (changedSomething) {
            ReservedSlotsMod.LOGGER.info("[ReservedSlots] Partial fill done, falling through to vanilla insertItem");
        } else {
            ReservedSlotsMod.LOGGER.info("[ReservedSlots] No matching reserved slots found, falling through to vanilla insertItem");
        }
    }

    /**
     * During vanilla's sequential pass in insertItem, block non-matching items
     * from going into reserved or locked player inventory slots.
     */
    @Redirect(
        method = "insertItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;canInsert(Lnet/minecraft/item/ItemStack;)Z")
    )
    private boolean onCanInsertIntoSlot(Slot slot, ItemStack stack) {
        if (!slot.canInsert(stack)) {
            return false;
        }
        if (slot.inventory instanceof PlayerInventory playerInventory
                && playerInventory.player instanceof ServerPlayerEntity serverPlayer) {
            boolean allowed = ReservedSlotManager.canSlotAcceptItem(serverPlayer, slot.getIndex(), stack);
            if (!allowed) {
                ReservedSlotsMod.LOGGER.info("[ReservedSlots] Blocking item {} from reserved/locked slot invIndex={}",
                        stack.getItem(), slot.getIndex());
            }
            return allowed;
        }
        return true;
    }
}
