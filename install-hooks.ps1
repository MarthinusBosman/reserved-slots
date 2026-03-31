# Run this once after cloning to install the pre-push git hook.

$hookPath = Join-Path $PSScriptRoot ".git\hooks\pre-push"
$hookContent = "#!/bin/sh`n# Only run auto-tag when pushing branches, not when pushing the tag itself.`nwhile read local_ref local_sha remote_ref remote_sha; do`n  case `"`$remote_ref`" in`n    refs/tags/*) continue ;;`n  esac`n  powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"`$(git rev-parse --show-toplevel)/scripts/auto-tag.ps1`"`n  exit 0`ndone`nexit 0`n"

Set-Content -Path $hookPath -Value $hookContent -NoNewline -Encoding ascii
Write-Host "Installed pre-push hook at: $hookPath" -ForegroundColor Green
