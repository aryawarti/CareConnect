# Rebuild and redeploy only what changed — the normal dev loop.
#
#   .\scripts\redeploy.ps1 queue-service                    # one service
#   .\scripts\redeploy.ps1 queue-service,identity-service   # several
#   .\scripts\redeploy.ps1 -Frontend                        # UI only
#   .\scripts\redeploy.ps1 -All                             # everything
#
# For day-to-day UI work you don't need this at all:
#   cd frontend; npm start   ->   http://localhost:4200 with hot reload.

param(
    [Parameter(Position = 0)]
    [string[]]$Services = @(),

    [switch]$Frontend,
    [switch]$All
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

function Section($text) {
    Write-Host "`n=== $text" -ForegroundColor Cyan
}

if ($All) {
    Section "Building all backend modules"
    Push-Location "$root\backend"; mvn -q package -DskipTests; Pop-Location

    Section "Building frontend"
    Push-Location "$root\frontend"; npm run build; Pop-Location

    Section "Rebuilding every container"
    Push-Location $root; docker compose --profile platform up -d --build; Pop-Location
}
else {
    if ($Services.Count -gt 0) {
        # -am also builds the parent pom and platform-starter, which services
        # depend on but which aren't installed into the local repository.
        Section "Building $($Services -join ', ')"
        Push-Location "$root\backend"
        mvn -q package -DskipTests -pl ($Services -join ',') -am
        Pop-Location
    }

    if ($Frontend) {
        Section "Building frontend"
        Push-Location "$root\frontend"; npm run build; Pop-Location
    }

    $containers = @()
    $containers += $Services
    if ($Frontend) { $containers += "frontend" }

    if ($containers.Count -eq 0) {
        Write-Host "Nothing to do. Pass service names, -Frontend, or -All." -ForegroundColor Yellow
        exit 0
    }

    Section "Redeploying $($containers -join ', ')"
    Push-Location $root
    docker compose --profile platform up -d --build @containers
    Pop-Location
}

$stopwatch.Stop()
Write-Host "`nDone in $([math]::Round($stopwatch.Elapsed.TotalSeconds))s." -ForegroundColor Green
Write-Host "Status : docker compose ps"
Write-Host "Logs   : docker logs -f careconnect-<service>"
