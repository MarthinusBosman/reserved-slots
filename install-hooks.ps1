# Run this once after cloning to install the post-commit git hook.

$hookPath = Join-Path $PSScriptRoot ".git\hooks\post-commit"
$hookContent = "#!/bin/sh`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"`$(git rev-parse --show-toplevel)/scripts/auto-tag.ps1`"`n"

Set-Content -Path $hookPath -Value $hookContent -NoNewline -Encoding ascii
Write-Host "Installed post-commit hook at: $hookPath" -ForegroundColor Green
