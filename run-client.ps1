# Script to run Minecraft client with the mod using Java 21

$ErrorActionPreference = "Stop"

$javaDir = "$PSScriptRoot\.java"

Write-Host "Checking for Java 21..." -ForegroundColor Cyan

# Check if we already have a Java 21 directory
$existingJava = Get-ChildItem -Path $javaDir -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "jdk-21*" } | Select-Object -First 1

if (-not $existingJava) {
    Write-Host "Java 21 not found. Please run build-mod.ps1 first to download Java 21." -ForegroundColor Red
    exit 1
}

Write-Host "Java 21 found at $($existingJava.FullName)" -ForegroundColor Green
Write-Host "Using Java from: $($existingJava.FullName)\bin\java.exe" -ForegroundColor Green

# Set JAVA_HOME and run Gradle
$env:JAVA_HOME = $existingJava.FullName
$env:PATH = "$($existingJava.FullName)\bin;$env:PATH"

Write-Host "`nStarting Minecraft client with mod..." -ForegroundColor Cyan

# Create logs directory if it doesn't exist
$logsDir = "$PSScriptRoot\logs"
if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir | Out-Null
}

# Generate log filename with timestamp
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$logFile = "$logsDir\client_$timestamp.log"

Write-Host "Logging to: $logFile" -ForegroundColor Yellow

# Run Gradle and save output to log file
& "$PSScriptRoot\gradlew.bat" runClient 2>&1 | Tee-Object -FilePath $logFile

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nClient closed successfully!" -ForegroundColor Green
} else {
    Write-Host "`nClient exited with error!" -ForegroundColor Red
    exit $LASTEXITCODE
}
