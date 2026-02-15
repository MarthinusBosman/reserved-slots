# Testing the Reserved Slots Mod

This guide will walk you through building and testing the Reserved Slots mod for Minecraft 1.21.1.

## Prerequisites

- Java Development Kit (JDK) 21 or higher
- Minecraft Java Edition (will be downloaded automatically by Gradle)
- Internet connection (for downloading dependencies)

## Building the Mod

### Step 1: Open a Terminal

Open PowerShell in the mod directory:
```powershell
cd c:\Repos\personal\reserved-slots
```

### Step 2: Build the Mod

**Recommended**: Use the build script (automatically handles Java 21):
```powershell
.\build-mod.ps1
```

**Alternative**: If you have Java 21 installed and configured:
```powershell
.\gradlew build
```

**Note**: The mod requires Java 21 to build. If you encounter Java version errors, use `build-mod.ps1` which automatically downloads and uses Java 21.

This will:
- Download all dependencies (Minecraft, Fabric Loader, Fabric API)
- Compile the mod
- Run any configured tests
- Create a JAR file in `build/libs/`

The build process may take 5-10 minutes the first time as it downloads Minecraft and sets up the workspace.

### Step 3: Locate the Built Mod

After a successful build, you'll find the mod JAR at:
```
build/libs/reserved-slots-1.0.0.jar
```

## Testing in Development Environment

### Option A: Run in Development (Recommended for Testing)

Run the Minecraft client with the mod loaded:
```powershell
.\gradlew runClient
```

This launches Minecraft with your mod pre-installed. Any changes you make to the code will require rebuilding.

### Option B: Run Dedicated Server

To test in a server environment:
```powershell
.\gradlew runServer
```

## Testing in Production Minecraft

### Step 1: Install Fabric Loader

1. Download Fabric Installer from: https://fabricmc.net/use/installer/
2. Run the installer
3. Select Minecraft version **1.21.1**
4. Click "Install"

### Step 2: Install Fabric API

1. Download Fabric API from: https://modrinth.com/mod/fabric-api
2. Make sure to get version **0.105.0+1.21.1** or compatible
3. Place the JAR in your `.minecraft/mods` folder

### Step 3: Install the Reserved Slots Mod

1. Copy `build/libs/reserved-slots-1.0.0.jar` to your `.minecraft/mods` folder
2. Launch Minecraft using the Fabric profile

## Testing Checklist

### Basic Functionality

1. **Launch the game**
   - Verify no crashes on startup
   - Check logs for "Initializing Reserved Slots Mod" message

2. **Test Keybinding**
   - Go to Options → Controls
   - Scroll to "Reserved Slots" category
   - Verify "Toggle Reserved Slot" keybinding is present
   - Change it if desired (default: L)

3. **Reserve a Slot**
   - Open your inventory (E)
   - Pick up or place an item (e.g., dirt block)
   - Hover over the slot
   - Press CTRL + L
   - **Expected**: Slot background darkens slightly

4. **Test Ghost Item**
   - With a reserved slot, remove the item from it
   - **Expected**: A semi-transparent version of the item remains visible

5. **Lock a Slot**
   - Hover over the reserved slot
   - Press CTRL + L again
   - **Expected**: Lock icon appears in top-right corner of slot

6. **Unlock a Slot**
   - Hover over the locked slot
   - Press CTRL + L again
   - **Expected**: Slot returns to normal appearance

### Item Routing Tests

7. **Reserved Slot Priority**
   - Reserve a slot for cobblestone
   - Mine cobblestone or pick it up
   - **Expected**: Cobblestone goes to the reserved slot first

8. **Multiple Reserved Slots**
   - Reserve two different slots for two different items (e.g., dirt and stone)
   - Pick up both item types
   - **Expected**: Each item goes to its respective reserved slot

9. **Locked Slot Behavior**
   - Lock a slot
   - Try to place items in it manually
   - Try to pick up items with inventory full except for locked slot
   - **Expected**: Items cannot enter locked slot under any circumstance

10. **Inventory Full with Reserved Slot**
    - Fill inventory completely except for an empty reserved slot
    - Pick up a different item type
    - **Expected**: Item still drops (doesn't go into reserved slot)

### Persistence Tests

11. **Save and Reload**
    - Reserve/lock several slots
    - Exit and rejoin the world
    - **Expected**: Reserved/locked slots are maintained

12. **Server Sync (if testing on server)**
    - Reserve slots on a server
    - Disconnect and reconnect
    - **Expected**: Slots remain reserved

### Edge Cases

13. **Stack Behavior**
    - Reserve a slot with a stackable item
    - Fill it to max stack size
    - Pick up more of that item
    - **Expected**: Overflow goes to other slots normally

14. **Different NBT Data**
    - Reserve a slot for an enchanted item
    - Pick up the same item without enchantments
    - **Expected**: Both items go to the reserved slot (item-type matching only)

15. **Hopper/Dispenser Transfer**
    - Set up a hopper transferring items into a chest
    - Lock some slots in your inventory
    - Stand near the hopper
    - **Expected**: Items don't forcefully enter locked slots

## Troubleshooting

### Build Fails

- **Error: Java version mismatch**
  - Ensure you have JDK 21 installed: `java -version`
  - Set JAVA_HOME if needed

- **Error: Cannot resolve dependencies**
  - Check your internet connection
  - Try: `.\gradlew clean build --refresh-dependencies`

### Mod Doesn't Load

- Verify Fabric Loader is installed for Minecraft 1.21.1
- Verify Fabric API is installed and compatible
- Check `logs/latest.log` for error messages

### Keybinding Doesn't Work

- Check if another mod is using the same keybinding
- Verify CTRL is being held down
- Try changing the keybinding in game settings

### Visual Overlays Not Showing

- This indicates a rendering issue
- Check logs for errors related to texture loading
- Verify the lock icon texture exists at `assets/reservedslots/textures/gui/lock_icon.png`

## Development Tips

### Quick Rebuild and Test

After making code changes:
```powershell
.\gradlew build runClient
```

### View Logs

Development logs are in:
```
run/logs/latest.log
```

Production logs are in:
```
.minecraft/logs/latest.log
```

### Enable Debug Logging

Edit `run/config/fabric.properties` (or `.minecraft/config/fabric.properties`) and add:
```properties
fabric.log.level=debug
```

## Known Limitations

1. **Item matching is by type only** - Enchantments, NBT data, and durability are ignored
2. **Armor and offhand slots** - Currently only main inventory (36 slots) is supported
3. **Shulker boxes** - Items inside shulker boxes don't trigger reserved slot behavior
4. **Creative mode** - Some behaviors may differ in creative mode

## Reporting Issues

If you encounter bugs:
1. Check the logs for error messages
2. Note the exact steps to reproduce
3. Record your Minecraft version, Fabric version, and Fabric API version
4. Test with only this mod installed to rule out conflicts

## Next Steps

- Test multiplayer functionality on a dedicated server
- Try integration with popular inventory management mods
- Experiment with different item types (tools, food, blocks)
- Test performance with many reserved slots

Happy testing! 🎮
