# Automatically increments the patch version tag and pushes it after each commit.

$latestTag = git describe --tags --abbrev=0 2>$null
if (-not $latestTag) {
    $latestTag = "v0.0.0"
}

if ($latestTag -match '^v(\d+)\.(\d+)\.(\d+)$') {
    $major = [int]$Matches[1]
    $minor = [int]$Matches[2]
    $patch = [int]$Matches[3] + 1
    $newTag = "v$major.$minor.$patch"

    git tag $newTag
    git push origin $newTag
    Write-Host "Auto-tagged and pushed: $newTag" -ForegroundColor Green
} else {
    Write-Warning "Could not parse latest tag '$latestTag', skipping auto-tag."
}
