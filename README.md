# Reserved Slots

**Keep your inventory organized! Reserve and lock slots for specific items.**

Reserve Slots is a client-side Fabric mod that lets you designate inventory slots for specific items, ensuring they always go to the same place. Perfect for keeping your tools, food, and building blocks organized!

---

## ✨ Features

### 🎯 Reserve Slots for Specific Items
Reserve a slot for a specific item type - when you pick up that item, it automatically goes to its reserved slot. If all other slots are full, other items can still use reserved slots as overflow. Great for keeping your pickaxe, sword, and torches in the same spots!

### 🔒 Lock Slots for Exclusive Items
Lock a slot to make it exclusively for a specific item type - no other items can ever enter that slot, even if your inventory is full. Perfect for ensuring critical items like your pickaxe or food always have their dedicated space.

### 👁️ Visual Indicators
- **Darkened backgrounds** on reserved/locked slots
- **Ghost item preview** shows what belongs in empty reserved slots
- **Padlock icon** clearly marks locked slots
- **Works in both inventory and hotbar**

### 🎮 Smart Item Routing
Items automatically route to their reserved slots when picked up. No more manually organizing your inventory after every mining trip!

### 💾 Per-World Persistence
Your reserved slot configurations are saved with each world - different worlds can have different setups.

---

## 📖 How to Use

### Reserving a Slot
1. Place an item in the slot you want to reserve
2. Hover over the slot (in your inventory or hotbar)
3. Press **R** (or your configured keybind)
4. The slot is now reserved for that item type with a darkened background

### Locking a Slot
1. Reserve a slot first (follow steps above)
2. Press **R** again while hovering over the reserved slot
3. The slot locks and shows a padlock icon - only the reserved item type can enter, no other items allowed

### Removing Reservation
- Press **R** a third time to cycle back to normal

### Keybinding
- Default: **R** key
- Customize in: **Options → Controls → Reserved Slots**

---

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/)
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download Reserved Slots from [Releases](../../releases)
4. Place the `.jar` file in your `mods` folder
5. Launch Minecraft 1.21.1!

---

## 🔧 Technical Information

- **Minecraft Version:** 1.21.1
- **Mod Loader:** Fabric
- **Required Dependencies:** Fabric API 0.105.0+
- **Side:** Client & Server (works in single-player and multiplayer)

---

## 🛠️ Building from Source

```bash
git clone https://github.com/yourusername/reserved-slots.git
cd reserved-slots
./gradlew build
```

The compiled `.jar` will be in `build/libs/`

---

## 📝 License

This mod is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## ❓ FAQ

**Q: Does this work in multiplayer?**  
A: Yes! The mod needs to be installed on the server for multiplayer support.

**Q: Can I change the keybinding?**  
A: Yes! Go to Options → Controls → Reserved Slots to change the keybind.

**Q: Will my reserved slots carry over between worlds?**  
A: No, each world has its own independent slot configuration.

**Q: Can I reserve armor slots or the offhand?**  
A: Currently, only the main 36 inventory slots (including hotbar) can be reserved.

---

**Enjoy organized inventory management! 🎒**
