# Building the Reserved Slots Mod

## Quick Build (Recommended)

The easiest way to build the mod is to use the provided build script:

```powershell
.\build-mod.ps1
```

This script will:
- Automatically download Java 21 if needed (saved to `.java/` folder)
- Build the mod using the correct Java version
- Create the mod JAR at `build/libs/reserved-slots-1.0.0.jar`

## Manual Build

If you prefer to build manually and already have Java 21 installed:

### Windows:
```powershell
.\gradlew.bat build
```

### Linux/Mac:
```bash
./gradlew build
```

## Java Version Requirements

**Important**: This mod requires **Java 21** to build, even though you may have Java 25 installed.

- Minecraft 1.21.1 and Fabric Loom 1.8.11 require Java 21
- The `build-mod.ps1` script handles this automatically
- Manual builds require setting `JAVA_HOME` to a Java 21 installation

## Build Output

After a successful build, you'll find:
- **Mod JAR**: `build/libs/reserved-slots-1.0.0.jar`
- **Sources JAR**: `build/libs/reserved-slots-1.0.0-sources.jar`

## Troubleshooting

### "Error: java.lang.IllegalArgumentException: 25.0.1"

This means Gradle is trying to use Java 25 instead of Java 21. Solutions:
1. Use `build-mod.ps1` which automatically uses the correct Java version
2. Set JAVA_HOME to Java 21 before running gradlew:
   ```powershell
   $env:JAVA_HOME = "C:\Path\To\Java21"
   .\gradlew.bat build
   ```

### Build Takes a Long Time

The first build will take 5-10 minutes because Gradle needs to:
- Download Minecraft 1.21.1
- Download Fabric Loader and Fabric API
- Set up the development workspace
- Remap all the dependencies

Subsequent builds will be much faster (30-60 seconds).

### Clean Build

If you encounter issues, try cleaning first:
```powershell
.\gradlew.bat clean build
```

## Next Steps

Once built, see [TESTING.md](TESTING.md) for instructions on how to test the mod.
