# Full, reliable rebuild of the whole stack, in the correct order, with gates.
#
# Use this when several services changed, migrations were added, or the stack
# got into a bad state - it removes guesswork about "did the jar rebuild?".
#
#   .\scripts\rebuild-all.ps1            # rebuild everything, keep the database
#   .\scripts\rebuild-all.ps1 -Fresh     # ALSO wipe the DB volume (fresh schema + reseed)
#
# -Fresh is the one to use after adding/altering migrations or when a service
# will not start due to schema drift: it drops the Postgres volume so every
# database is recreated and every migration runs from scratch.

param(
    [switch]$Fresh
)

$root = Split-Path -Parent $PSScriptRoot
$sw = [System.Diagnostics.Stopwatch]::StartNew()

function Section($text) { Write-Host "`n=== $text" -ForegroundColor Cyan }
function Fail($text)    { Write-Host "`nFAILED: $text" -ForegroundColor Red; exit 1 }

# 1. Backend jars - the runtime-only Docker images COPY these, so they MUST
#    exist and be current before we build images. 'clean' clears stale jars
#    that would otherwise break the wildcard COPY.
Section "Building backend jars (mvn clean package)"
Push-Location "$root\backend"
mvn -q clean package -DskipTests
$mvnExit = $LASTEXITCODE
Pop-Location
if ($mvnExit -ne 0) { Fail "Maven build failed - fix the compile error shown above, then rerun." }

# 2. Frontend bundle - also COPY-ed into an nginx image.
Section "Building frontend bundle (npm run build)"
Push-Location "$root\frontend"
npm run build
$npmExit = $LASTEXITCODE
Pop-Location
if ($npmExit -ne 0) { Fail "Frontend build failed - see the error above." }

# 3. Reset containers (and optionally the DB volume).
Push-Location $root
if ($Fresh) {
    Section "Removing containers AND database volume (-Fresh)"
    docker compose down -v
} else {
    Section "Removing containers (keeping database volume)"
    docker compose down
}
Pop-Location

# 4. Build images from the fresh artifacts and start the whole stack + seed.
Section "Building images and starting the stack"
Push-Location $root
docker compose --profile platform --profile demo up -d --build
$upExit = $LASTEXITCODE
Pop-Location
if ($upExit -ne 0) { Fail "docker compose up failed - see the error above." }

# 5. Wait for health, then report. Surface the logs of anything that exited so
#    you do not have to go hunting.
Section "Waiting for services to settle (up to 5 minutes)"
$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 15
    $status = docker compose ps -a --format "{{.Service}} {{.Status}}"
    $starting = $status | Where-Object { $_ -match "starting" }
    Write-Host "." -NoNewline
} while ($starting -and (Get-Date) -lt $deadline)

Write-Host ""
docker compose ps -a

$exited = (docker compose ps -a --format "{{.Service}} {{.Status}}") |
          Where-Object { $_ -match "Exited" -and $_ -notmatch "seeder" }

if ($exited) {
    Write-Host "`nThese services exited - showing the tail of each so you can see why:" -ForegroundColor Yellow
    foreach ($line in $exited) {
        $svc = ($line -split " ")[0]
        Write-Host "`n----- $svc -----" -ForegroundColor Yellow
        docker compose logs $svc --tail=25
    }
    Write-Host "`nPaste the errors above if you need help." -ForegroundColor Yellow
} else {
    Write-Host "`nAll services are up." -ForegroundColor Green
    Write-Host "Open http://localhost:4300 and sign in with a demo account." -ForegroundColor Green
}

$sw.Stop()
Write-Host "`nTotal time: $([math]::Round($sw.Elapsed.TotalMinutes,1)) min." -ForegroundColor Green
