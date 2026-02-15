# Reserved Slots - Minecraft Mod

I want to develop a minecraft (java edition) mod that allows users to reserve slots for items in their inventory and hotbar. (inventory and hotbar will be used interchangeably)

## Features

- With an item in an in inventory slot and hovering over said item with the mouse, hitting [CTRL]+[L] will reserve the slot for that item.
- The reserved slot will be visually distinct from other slots, with a slightly darkened background, and if the item is manually removed from the slot, a transparent version of the item will remain in the slot indicating what type of item it's reserved for.
- The reserved slot will be saved in the player's data, so it will persist across sessions.
- The reserved slot will function by automatically causing any item of that type that gets added to the inventory (either by picking up or being dropped from some output) to go into that slot instead. If the inventory is otherwise full, and the reserved slot empty, other items will still be allowed to be placed in the reserved slot.
- Hitting [CTRL]+[L] again will lock the slot, preventing any item from being placed in it even if there is no other space. This will be indicated by a semi-transparent lock icon at the top right corner of the inventory slot.
- Hitting [CTRL]+[L] again will unlock the slot, returning it to a normal inventory slot.
- The keybinding should be configurable in the game's controls menu (or if that's not possible, a mod config menu)

## Technical Implementation

- The mod should be developed for Minecraft 1.21.11.
- The mod should use the Fabric mod loader.
- The mod should use the Fabric API.