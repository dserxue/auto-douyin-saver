param(
    [string]$Version = "",
    [string]$Notes   = "",
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Write-Step { param($msg) Write-Host "`n[STEP] $msg" -ForegroundColor Cyan }
function Write-Ok   { param($msg) Write-Host "  OK  $msg"   -ForegroundColor Green }
function Write-Fail { param($msg) Write-Host "  ERR $msg"   -ForegroundColor Red; exit 1 }
function Write-Warn { param($msg) Write-Host "  !!  $msg"   -ForegroundColor Yellow }

$RepoRoot = $PSScriptRoot
Set-Location $RepoRoot

# ── 1. check tools ──────────────────────────────────────────
Write-Step "Checking tools..."

$env:PATH = "$env:USERPROFILE\tools\gh;$env:PATH"

if (-not (Get-Command "gh"  -ErrorAction SilentlyContinue)) { Write-Fail "gh not found. Run: choco install gh" }
if (-not (Get-Command "git" -ErrorAction SilentlyContinue)) { Write-Fail "git not found." }
Write-Ok "gh   : $(gh --version | Select-Object -First 1)"
Write-Ok "git  : $(git --version)"

# ── 2. version ──────────────────────────────────────────────
Write-Step "Resolving version..."

if ($Version -eq "") {
    $gradle = Get-Content (Join-Path $RepoRoot "app\build.gradle.kts") -Raw
    if ($gradle -match 'versionName\s*=\s*"([^"]+)"') {
        $Version = $Matches[1]
        Write-Ok "Read from build.gradle.kts: $Version"
    } else {
        $Version = Read-Host "Enter version (e.g. 1.0.0)"
    }
}
$TagName = "v$Version"
Write-Ok "Tag: $TagName"

# ── 3. check tag collision ───────────────────────────────────
Write-Step "Checking tag conflict..."
$existingTag = git tag -l $TagName
if ($existingTag -eq $TagName) {
    Write-Warn "Tag $TagName already exists!"
    $choice = Read-Host "Delete old tag and re-release? (y/N)"
    if ($choice -eq "y" -or $choice -eq "Y") {
        git tag -d $TagName
        git push origin ":refs/tags/$TagName" 2>$null
        Write-Ok "Old tag deleted"
    } else {
        Write-Fail "Aborted"
    }
}

# ── 4. release notes ────────────────────────────────────────
Write-Step "Release notes..."
if ($Notes -eq "") {
    Write-Host "  Enter release notes (press Enter twice to finish):" -ForegroundColor Yellow
    $lines = @()
    while ($true) {
        $line = Read-Host "  "
        if ($line -eq "") { break }
        $lines += $line
    }
    $Notes = if ($lines.Count -gt 0) { $lines -join "`n" } else { "Release $TagName" }
}
Write-Ok "Notes confirmed"

# ── 5. build APK ────────────────────────────────────────────
Write-Step "Building Release APK (this takes 2-5 min)..."

$GradlewBat = Join-Path $RepoRoot "gradlew.bat"
if (-not (Test-Path $GradlewBat)) { Write-Fail "gradlew.bat not found" }

& $GradlewBat assembleRelease --no-daemon --quiet
if ($LASTEXITCODE -ne 0) { Write-Fail "Build failed! Check Android Studio for errors." }
Write-Ok "Build succeeded!"

# ── 6. locate APK ───────────────────────────────────────────
Write-Step "Locating APK..."

$ApkPath = $null
$candidates = @(
    "app\build\outputs\apk\release\app-release.apk",
    "app\build\outputs\apk\release\app-release-unsigned.apk"
)
foreach ($c in $candidates) {
    $full = Join-Path $RepoRoot $c
    if (Test-Path $full) { $ApkPath = $full; break }
}
if ($null -eq $ApkPath) {
    $found = Get-ChildItem (Join-Path $RepoRoot "app\build\outputs\apk\release") -Filter "*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { $ApkPath = $found.FullName }
}
if ($null -eq $ApkPath) { Write-Fail "APK not found after build" }

$ApkSize    = [math]::Round((Get-Item $ApkPath).Length / 1MB, 1)
$ApkName    = "AutoDouyinSaver-${TagName}.apk"
$ApkDest    = Join-Path (Split-Path $ApkPath) $ApkName
Copy-Item $ApkPath $ApkDest -Force
Write-Ok "APK : $ApkDest  (${ApkSize} MB)"

# ── 7. dry-run exit ─────────────────────────────────────────
if ($DryRun) {
    Write-Host "`n[DRY-RUN] Build done. Skipping push and release." -ForegroundColor Magenta
    Write-Host "  APK: $ApkDest" -ForegroundColor Magenta
    exit 0
}

# ── 8. git push ─────────────────────────────────────────────
Write-Step "Checking git status..."
$status = git status --porcelain
if ($status) {
    Write-Warn "Uncommitted changes detected:"
    git status --short
    $choice = Read-Host "  Auto-commit all changes? (y/N)"
    if ($choice -eq "y" -or $choice -eq "Y") {
        git add -A
        git commit -m "chore: release $TagName"
        Write-Ok "Committed"
    } else {
        Write-Warn "Skipping commit, proceeding to tag..."
    }
} else {
    Write-Ok "Working tree clean"
}

Write-Host "  Pushing to remote..." -ForegroundColor Gray
git push origin HEAD
if ($LASTEXITCODE -ne 0) { Write-Fail "git push failed" }
Write-Ok "Pushed"

# ── 9. create GitHub release ────────────────────────────────
Write-Step "Creating GitHub Release..."

$FullNotes = @"
$Notes

---
**Install**
1. Download ``$ApkName``
2. Allow "Install unknown apps" on your Android device
3. Follow in-app instructions to grant Overlay and Accessibility permissions

**Requirements**: Android 9.0+ (API 28+)
"@

gh release create $TagName $ApkDest `
    --title "AutoDouyinSaver $TagName" `
    --notes $FullNotes `
    --latest

if ($LASTEXITCODE -ne 0) { Write-Fail "gh release create failed. Run: gh auth status" }

# ── 10. done ────────────────────────────────────────────────
$RepoUrl = (gh repo view --json url -q ".url") 2>$null
Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Released: $TagName  (${ApkSize} MB)"       -ForegroundColor Green
if ($RepoUrl) {
    Write-Host "  $RepoUrl/releases/tag/$TagName"         -ForegroundColor Green
}
Write-Host "============================================" -ForegroundColor Green
