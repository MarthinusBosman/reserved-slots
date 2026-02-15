# Script to build the mod with the correct Java version
# This downloads Java 21 if needed and uses it for building

$ErrorActionPreference = "Stop"

$javaVersion = "21"
$javaDir = "$PSScriptRoot\.java"

Write-Host "Checking for Java 21..." -ForegroundColor Cyan

# Check if we already have a Java 21 directory
$existingJava = Get-ChildItem -Path $javaDir -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "jdk-21*" } | Select-Object -First 1

if (-not $existingJava) {
    Write-Host "Java 21 not found. Downloading..." -ForegroundColor Yellow
    
    # Create directory
    New-Item -ItemType Directory -Force -Path $javaDir | Out-Null
    
    # Download Adoptium JDK 21 (Windows x64)
    $downloadUrl = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
    $zipFile = "$javaDir\jdk21.zip"
    
    Write-Host "Downloading from Adoptium..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $downloadUrl -OutFile $zipFile -UseBasicParsing
    
    Write-Host "Extracting..." -ForegroundColor Yellow
    Expand-Archive -Path $zipFile -DestinationPath $javaDir -Force
    
    # Clean up zip
    Remove-Item $zipFile -Force -ErrorAction SilentlyContinue
    
    # Find the extracted directory
    $existingJava = Get-ChildItem -Path $javaDir -Directory | Where-Object { $_.Name -like "jdk-21*" } | Select-Object -First 1
    
    if ($existingJava) {
        Write-Host "Java 21 installed successfully!" -ForegroundColor Green
    } else {
        Write-Host "Failed to extract Java 21" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "Java 21 found at $($existingJava.FullName)" -ForegroundColor Green
}

# Verify Java 21
$javaExe = "$($existingJava.FullName)\bin\java.exe"
Write-Host "Using Java from: $javaExe" -ForegroundColor Green

# Set JAVA_HOME and run Gradle
$env:JAVA_HOME = $existingJava.FullName
$env:PATH = "$($existingJava.FullName)\bin;$env:PATH"

Write-Host "`nBuilding mod with Java 21..." -ForegroundColor Cyan
& "$PSScriptRoot\gradlew.bat" build

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nBuild successful!" -ForegroundColor Green
    Write-Host "Mod JAR location: build\libs\reserved-slots-1.0.0.jar" -ForegroundColor Cyan
} else {
    Write-Host "`nBuild failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}
