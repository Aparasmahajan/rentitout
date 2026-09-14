<#
.SYNOPSIS
  Build and run the seven Spring Boot services against the local dev stack, without Docker.

.DESCRIPTION
  Each service runs as a plain `java -jar` process, tagged with -Dradius.service=<name> so
  this script can find it again to stop it. Logs go to the dev-stack log directory.

    .\scripts\services.ps1 build            # mvn package (skips tests)
    .\scripts\services.ps1 test             # mvn test
    .\scripts\services.ps1 up               # start all seven
    .\scripts\services.ps1 up -Only user    # start one (repeatable: -Only user,listing)
    .\scripts\services.ps1 status
    .\scripts\services.ps1 down
    .\scripts\services.ps1 logs -Only booking

  Start the backing services first:  .\scripts\devstack.ps1 up

.PARAMETER Command
  build | test | up | down | status | logs

.PARAMETER Only
  Limit to these services: gateway, user, listing, search, booking, payment, notification.
#>

[CmdletBinding()]
param(
  [Parameter(Position = 0)]
  [ValidateSet('build', 'test', 'up', 'down', 'status', 'logs')]
  [string]$Command = 'status',

  [string[]]$Only = @()
)

$ErrorActionPreference = 'Stop'

$RepoRoot    = Split-Path -Parent $PSScriptRoot
$ServicesDir = Join-Path $RepoRoot 'services'
$DevRoot     = if ($env:RADIUS_DEV_HOME) { $env:RADIUS_DEV_HOME } else { Join-Path $env:LOCALAPPDATA 'radius-dev' }
$LogDir      = Join-Path $DevRoot 'logs'

# Ports here must match devstack.ps1.
$PgPort    = 5433
$RedisPort = 6380
$Kafka     = 'localhost:9092'
$MinioPort = 9010

$Services = @(
  @{ key = 'user';         module = 'user-service';         port = 8081; db = 'radius_user' }
  @{ key = 'listing';      module = 'listing-service';      port = 8082; db = 'radius_listing' }
  @{ key = 'search';       module = 'search-service';       port = 8083; db = 'radius_search' }
  @{ key = 'booking';      module = 'booking-service';      port = 8084; db = 'radius_booking' }
  @{ key = 'payment';      module = 'payment-service';      port = 8085; db = 'radius_payment' }
  @{ key = 'notification'; module = 'notification-service'; port = 8086; db = 'radius_notification' }
  # The gateway comes last so it never routes to a service that is not up yet.
  @{ key = 'gateway';      module = 'api-gateway';          port = 8080; db = $null }
)

function Say([string]$m, [string]$c = 'Gray') { Write-Host $m -ForegroundColor $c }
function Ok([string]$m)   { Say "  ok    $m" 'Green' }
function Step([string]$m) { Say "`n== $m" 'Cyan' }
function Warn([string]$m) { Say "  warn  $m" 'Yellow' }

function Selected {
  if ($Only.Count -eq 0) { return $Services }
  return $Services | Where-Object { $Only -contains $_.key }
}

function JavaHome {
  if ($env:RADIUS_JAVA_HOME -and (Test-Path "$env:RADIUS_JAVA_HOME\bin\java.exe")) { return $env:RADIUS_JAVA_HOME }
  $bundled = Get-ChildItem (Join-Path $DevRoot 'jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($bundled -and (Test-Path "$($bundled.FullName)\bin\java.exe")) { return $bundled.FullName }
  if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { return $env:JAVA_HOME }
  throw 'no JDK 21 found - run .\scripts\devstack.ps1 setup, or set RADIUS_JAVA_HOME'
}

function MavenExe {
  if ($env:RADIUS_MAVEN_HOME -and (Test-Path "$env:RADIUS_MAVEN_HOME\bin\mvn.cmd")) {
    return "$env:RADIUS_MAVEN_HOME\bin\mvn.cmd"
  }
  # The copy devstack.ps1 unpacked wins over whatever is on PATH: a machine can
  # carry a Maven old enough that it cannot read these POMs at all, and picking
  # it up silently produces a very confusing failure.
  $bundled = Get-ChildItem (Join-Path $DevRoot 'maven') -Directory -ErrorAction SilentlyContinue |
             Select-Object -First 1
  if ($bundled -and (Test-Path "$($bundled.FullName)\bin\mvn.cmd")) {
    return "$($bundled.FullName)\bin\mvn.cmd"
  }
  $onPath = Get-Command mvn.cmd -ErrorAction SilentlyContinue
  if ($onPath) { return $onPath.Source }
  throw 'no Maven found - run .\scripts\devstack.ps1 setup, or set RADIUS_MAVEN_HOME'
}

function PortBusy([int]$port) {
  return $null -ne (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}

function Invoke-Maven([string[]]$goals) {
  $env:JAVA_HOME = JavaHome
  $mvn = MavenExe
  # settings-central.xml is there for machines whose global settings point at an
  # internal mirror that does not carry these artifacts.
  $args = @('-B', '-s', (Join-Path $ServicesDir 'settings-central.xml')) + $goals
  Push-Location $ServicesDir
  try {
    & $mvn @args
    if ($LASTEXITCODE -ne 0) { throw "maven failed ($LASTEXITCODE)" }
  } finally { Pop-Location }
}

function Invoke-Build { Step 'building'; Invoke-Maven @('-DskipTests', 'package'); Ok 'jars built' }
function Invoke-Test  { Step 'testing';  Invoke-Maven @('test');                   Ok 'tests passed' }

function Get-ServiceProcesses {
  Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -like '*-Dradius.service=*' }
}

function Invoke-Up {
  Step 'starting services'
  New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

  if (-not (PortBusy $PgPort)) { throw "nothing on postgres port $PgPort - run .\scripts\devstack.ps1 up first" }

  $java = Join-Path (JavaHome) 'bin\java.exe'

  # Shared environment. Everything the services read has an env override, so no
  # profile juggling is needed to point them at the local stack.
  $shared = @{
    SPRING_PROFILES_ACTIVE = 'dev'
    DB_HOST                = 'localhost'
    DB_PORT                = "$PgPort"
    DB_USER                = 'radius'
    DB_PASSWORD            = 'radius'
    # The JDBC driver opens with an SSL negotiation by default. Endpoint security
    # software on a corporate Windows build intercepts loopback traffic and aborts
    # that handshake ("failed to send SSL negotiation response" in the server log),
    # and the driver then times out. The database is on loopback, so ask for a
    # plain connection and skip the negotiation entirely.
    DB_OPTIONS             = '?sslmode=disable'
    REDIS_HOST             = 'localhost'
    REDIS_PORT             = "$RedisPort"
    KAFKA_BOOTSTRAP        = $Kafka
    JWT_SECRET             = 'dev-only-secret-change-me-at-least-32-bytes-long'
    OTP_EXPOSE_CODE        = 'true'
    # Whoever holds this number becomes the reviewer on the next service start.
    RADIUS_ADMIN_PHONE     = if ($env:RADIUS_ADMIN_PHONE) { $env:RADIUS_ADMIN_PHONE } else { '+491700000001' }
    S3_ENDPOINT            = "http://localhost:$MinioPort"
    S3_PUBLIC_ENDPOINT     = "http://localhost:$MinioPort"
    S3_BUCKET              = 'radius-photos'
    S3_ACCESS_KEY          = 'radius'
    S3_SECRET_KEY          = 'radius-secret'
    USER_SERVICE_URL       = 'http://localhost:8081'
    LISTING_SERVICE_URL    = 'http://localhost:8082'
    SEARCH_SERVICE_URL     = 'http://localhost:8083'
    BOOKING_SERVICE_URL    = 'http://localhost:8084'
    BOOKING_SERVICE_WS_URL = 'ws://localhost:8084'
    PAYMENT_SERVICE_URL    = 'http://localhost:8085'
    NOTIFICATION_SERVICE_URL = 'http://localhost:8086'
    # Tracing is on in the compose stack; locally there is no collector to send to.
    MANAGEMENT_TRACING_ENABLED = 'false'
    # Local runs want to see *which* component is unhealthy, not just that one is.
    MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS = 'always'
    # The Windows build of Redis reports its own path in INFO ("executable:C:\...").
    # Spring's health indicator parses INFO with java.util.Properties, which reads
    # "\U" as a broken unicode escape and marks Redis down while Redis is working
    # perfectly. The indicator is the problem, so the indicator is what goes off.
    # In Docker (Linux Redis) it stays on.
    MANAGEMENT_HEALTH_REDIS_ENABLED = 'false'
  }
  foreach ($kv in $shared.GetEnumerator()) { Set-Item -Path "Env:$($kv.Key)" -Value $kv.Value }

  foreach ($svc in Selected) {
    if (PortBusy $svc.port) { Ok "$($svc.key) already on $($svc.port)"; continue }

    $jar = Get-ChildItem (Join-Path $ServicesDir "$($svc.module)\target") -Filter '*.jar' -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*.original' } |
           Select-Object -First 1
    if (-not $jar) { throw "no jar for $($svc.module) - run: .\scripts\services.ps1 build" }

    if ($svc.db) { $env:DB_NAME = $svc.db } else { Remove-Item Env:DB_NAME -ErrorAction SilentlyContinue }

    # The jar path is quoted: this repository can live under "Documents\New folder",
    # and an unquoted argument would be split at the first space.
    Start-Process -FilePath $java `
      -ArgumentList @("-Dradius.service=$($svc.key)", '-XX:MaxRAMPercentage=25', '-jar', "`"$($jar.FullName)`"") `
      -WorkingDirectory $ServicesDir -WindowStyle Hidden `
      -RedirectStandardOutput (Join-Path $LogDir "$($svc.key).log") `
      -RedirectStandardError (Join-Path $LogDir "$($svc.key).err.log")

    Say "  ...   $($svc.key) starting on $($svc.port)"
  }

  Step 'waiting for health'
  # Seven JVMs starting at once on a laptop with on-access antivirus can take
  # three or four minutes between them. This waits rather than declaring failure.
  foreach ($svc in Selected) {
    $healthy = $false
    for ($i = 0; $i -lt 300; $i++) {
      try {
        $r = Invoke-RestMethod "http://localhost:$($svc.port)/actuator/health" -TimeoutSec 2 -ErrorAction Stop
        if ($r.status -eq 'UP') { $healthy = $true; break }
      } catch { Start-Sleep -Seconds 1 }
    }
    if ($healthy) { Ok "$($svc.key) up on $($svc.port)" }
    else { Warn "$($svc.key) did not report healthy - see $LogDir\$($svc.key).log" }
  }

  Say @"

Gateway:  http://localhost:8080
Seed it:  node infra\seed\seed.mjs
Web:      cd apps\web; npm install; npm run dev
"@ 'Green'
}

function Invoke-Down {
  Step 'stopping services'
  $procs = Get-ServiceProcesses
  if (-not $procs) { Ok 'nothing running'; return }
  foreach ($p in $procs) {
    $name = if ($p.CommandLine -match '-Dradius\.service=(\S+)') { $Matches[1] } else { 'service' }
    if ($Only.Count -gt 0 -and $Only -notcontains $name) { continue }
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    Ok "$name stopped"
  }
}

function Invoke-Status {
  Step 'services'
  foreach ($svc in $Services) {
    $state = 'down'
    $colour = 'DarkGray'
    if (PortBusy $svc.port) {
      $state = 'up'
      $colour = 'Green'
      try {
        $h = Invoke-RestMethod "http://localhost:$($svc.port)/actuator/health" -TimeoutSec 2 -ErrorAction Stop
        $state = $h.status.ToLower()
      } catch { $state = 'starting' }
    }
    Say ("  {0,-8} {1,-14} localhost:{2}" -f $state, $svc.key, $svc.port) $colour
  }
  Say "`n  logs    $LogDir"
}

function Invoke-Logs {
  $target = if ($Only.Count -gt 0) { $Only[0] } else { 'gateway' }
  $file = Join-Path $LogDir "$target.log"
  if (-not (Test-Path $file)) { throw "no log at $file" }
  Get-Content $file -Tail 60 -Wait
}

switch ($Command) {
  'build'  { Invoke-Build }
  'test'   { Invoke-Test }
  'up'     { Invoke-Up }
  'down'   { Invoke-Down }
  'status' { Invoke-Status }
  'logs'   { Invoke-Logs }
}
