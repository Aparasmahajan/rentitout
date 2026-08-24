<#
.SYNOPSIS
  The Docker-free local stack for Radius: PostgreSQL + PostGIS, Redis, Kafka and MinIO,
  all portable, none of them installed system-wide and none of them needing admin.

.DESCRIPTION
  Everything lands in one directory (default %LOCALAPPDATA%\radius-dev), which you can
  delete to start over. Nothing is written into Program Files, no services are registered,
  and the ports are deliberately off the defaults so an existing Postgres or Redis on this
  machine is left alone.

    .\scripts\devstack.ps1 setup    # download and unpack everything (once, ~260 MB)
    .\scripts\devstack.ps1 up       # start all four
    .\scripts\devstack.ps1 status   # what is listening
    .\scripts\devstack.ps1 down     # stop all four
    .\scripts\devstack.ps1 reset    # stop, then wipe the data (keeps the binaries)

  Ports: Postgres 5433 | Redis 6380 | Kafka 9092 | MinIO 9010/9011

.PARAMETER Command
  setup | up | down | status | reset

.PARAMETER DevHome
  Where the stack lives. Defaults to $env:RADIUS_DEV_HOME, then %LOCALAPPDATA%\radius-dev.
  Keep it off OneDrive - a synced Postgres data directory corrupts.

.PARAMETER PgSource
  A directory holding PostgreSQL 16 Windows binaries (containing bin\initdb.exe). Only
  needed for `setup`, and only if the script cannot find one itself.
#>

[CmdletBinding()]
param(
  [Parameter(Position = 0)]
  [ValidateSet('setup', 'up', 'down', 'status', 'reset')]
  [string]$Command = 'status',

  [string]$DevHome = $null,
  [string]$PgSource = $null
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------- settings ---

$Root = if ($DevHome) { $DevHome }
        elseif ($env:RADIUS_DEV_HOME) { $env:RADIUS_DEV_HOME }
        else { Join-Path $env:LOCALAPPDATA 'radius-dev' }

$Cache    = Join-Path $Root 'cache'
$PgDir    = Join-Path $Root 'pgsql'
$PgData   = Join-Path $Root 'pgdata'
$RedisDir = Join-Path $Root 'redis'
$KafkaDir = Join-Path $Root 'kafka'
$MinioDir = Join-Path $Root 'minio'
$LogDir   = Join-Path $Root 'logs'
$JdkDir   = Join-Path $Root 'jdk'
$MavenDir = Join-Path $Root 'maven'

$PgPort    = 5433
$RedisPort = 6380
$KafkaPort = 9092
$MinioPort = 9010
$MinioConsolePort = 9011
$DbUser    = 'radius'
$DbPass    = 'radius'

$Urls = @{
  postgis = 'https://download.osgeo.org/postgis/windows/pg16/postgis-bundle-pg16-3.6.2x64.zip'
  kafka   = 'https://archive.apache.org/dist/kafka/3.8.1/kafka_2.13-3.8.1.tgz'
  redis   = 'https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.zip'
  minio   = 'https://dl.min.io/server/minio/release/windows-amd64/minio.exe'
  postgres = 'https://get.enterprisedb.com/postgresql/postgresql-16.4-1-windows-x64-binaries.zip'
  jdk     = 'https://corretto.aws/downloads/latest/amazon-corretto-21-x64-windows-jdk.zip'
  maven   = 'https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip'
}

# ------------------------------------------------------------------- utils ---

function Say([string]$message, [string]$colour = 'Gray') { Write-Host $message -ForegroundColor $colour }
function Ok([string]$message)   { Say "  ok    $message" 'Green' }
function Step([string]$message) { Say "`n== $message" 'Cyan' }
function Warn([string]$message) { Say "  warn  $message" 'Yellow' }

function Fetch([string]$name, [string]$url, [string]$file) {
  if (Test-Path $file) { Ok "$name already downloaded"; return }
  Say "  ...   downloading $name"
  # curl.exe ships with Windows 10+ and copes with these CDNs better than
  # Invoke-WebRequest does behind a corporate proxy.
  & curl.exe -sSL --fail -o $file $url
  if ($LASTEXITCODE -ne 0) { throw "could not download $name from $url" }
  Ok "$name downloaded"
}


function Unzip([string]$zip, [string]$dest) {
  New-Item -ItemType Directory -Force -Path $dest | Out-Null
  # tar.exe (bsdtar) ships with Windows 10+ and reads zips. Measured here it is
  # about seventy times faster than Expand-Archive, which writes file by file
  # and so gets scanned file by file by on-access antivirus: 24 seconds against
  # 28 minutes for the 120 MB PostGIS bundle.
  if (Get-Command tar.exe -ErrorAction SilentlyContinue) {
    Push-Location $dest
    try {
      & tar.exe -xf $zip
      if ($LASTEXITCODE -eq 0) { return }
    } finally { Pop-Location }
    Warn 'tar could not read that archive - falling back to Expand-Archive (slow)'
  }
  Expand-Archive -Path $zip -DestinationPath $dest -Force
}

function PortBusy([int]$port) {
  $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
  return $null -ne $c
}

function JavaHome {
  if ($env:RADIUS_JAVA_HOME -and (Test-Path "$env:RADIUS_JAVA_HOME\bin\java.exe")) { return $env:RADIUS_JAVA_HOME }
  $bundled = Get-ChildItem $JdkDir -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($bundled -and (Test-Path "$($bundled.FullName)\bin\java.exe")) { return $bundled.FullName }
  if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { return $env:JAVA_HOME }
  throw "no JDK 21 found - run setup, or set RADIUS_JAVA_HOME"
}

# ------------------------------------------------------------------- setup ---

function Install-Postgres {
  # A local copy wins if it is a complete one. Server-only builds are common and
  # useless here: the setup needs psql to create the six databases.
  $candidates = @()
  if ($PgSource) { $candidates += $PgSource }
  $candidates += @(
    "$env:LOCALAPPDATA\Programs\PostgreSQL\16",
    "C:\Program Files\PostgreSQL\16"
  )
  foreach ($c in $candidates) {
    if ((Test-Path (Join-Path $c 'bin\initdb.exe')) -and (Test-Path (Join-Path $c 'bin\psql.exe'))) {
      Say "  ...   copying PostgreSQL binaries from $c"
      New-Item -ItemType Directory -Force -Path $PgDir | Out-Null
      Copy-Item -Path (Join-Path $c '*') -Destination $PgDir -Recurse -Force
      Ok 'postgres binaries in place'
      return
    }
  }

  $zip = Join-Path $Cache 'postgresql.zip'
  Fetch 'postgresql 16 (about 330 MB)' $Urls.postgres $zip
  Say '  ...   unpacking postgresql (this one takes a minute)'
  $tmp = Join-Path $Cache 'pg-unpacked'
  if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
  Unzip $zip $tmp
  $inner = Join-Path $tmp 'pgsql'
  if (-not (Test-Path $inner)) { $inner = (Get-ChildItem $tmp -Directory | Select-Object -First 1).FullName }
  if (Test-Path $PgDir) { Remove-Item $PgDir -Recurse -Force }
  Move-Item $inner $PgDir
  Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
  Ok 'postgres binaries in place'
}

function Invoke-Setup {
  Step "setting up in $Root"
  New-Item -ItemType Directory -Force -Path $Root, $Cache, $LogDir | Out-Null

  # --- JDK 21 (Kafka needs a modern JVM, and so do the services) ------------
  if (-not (Test-Path $JdkDir)) {
    $zip = Join-Path $Cache 'jdk21.zip'
    Fetch 'jdk 21' $Urls.jdk $zip
    Unzip $zip $JdkDir
  }
  Ok "jdk at $(JavaHome)"

  # --- Maven ----------------------------------------------------------------
  # Bundled deliberately. A machine can have an ancient Maven on PATH that cannot
  # build these POMs at all, and silently using it produces a baffling failure.
  if (-not (Get-ChildItem $MavenDir -Directory -ErrorAction SilentlyContinue)) {
    $zip = Join-Path $Cache 'maven.zip'
    Fetch 'maven 3.9' $Urls.maven $zip
    Unzip $zip $MavenDir
  }
  $mvn = Get-ChildItem $MavenDir -Directory | Select-Object -First 1
  Ok "maven at $($mvn.FullName)"

  # --- PostgreSQL binaries --------------------------------------------------
  if (-not (Test-Path (Join-Path $PgDir 'bin\psql.exe'))) {
    Install-Postgres
  } else {
    Ok 'postgres binaries already in place'
  }

  # --- PostGIS, overlaid onto those binaries --------------------------------
  if (-not (Test-Path (Join-Path $PgDir 'share\extension\postgis.control'))) {
    $zip = Join-Path $Cache 'postgis.zip'
    Fetch 'postgis' $Urls.postgis $zip
    $tmp = Join-Path $Cache 'postgis-unpacked'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Unzip $zip $tmp
    $bundle = Get-ChildItem $tmp -Directory | Select-Object -First 1
    foreach ($sub in @('bin', 'lib', 'share')) {
      $from = Join-Path $bundle.FullName $sub
      if (Test-Path $from) {
        # robocopy, not Copy-Item: overlaying a tree onto an existing one is
        # exactly the case where Copy-Item -Recurse refuses ("Container cannot
        # be copied onto existing leaf item").
        $null = robocopy $from (Join-Path $PgDir $sub) /E /NFL /NDL /NJH /NJS /NP
        if ($LASTEXITCODE -ge 8) { throw "postgis overlay of $sub failed (robocopy $LASTEXITCODE)" }
      }
    }
    Ok 'postgis overlaid'
  } else {
    Ok 'postgis already overlaid'
  }

  # --- the cluster ----------------------------------------------------------
  if (-not (Test-Path (Join-Path $PgData 'PG_VERSION'))) {
    Say '  ...   initialising the database cluster'
    $pwFile = Join-Path $Cache 'pgpass.txt'
    Set-Content -Path $pwFile -Value $DbPass -NoNewline -Encoding ascii
    & (Join-Path $PgDir 'bin\initdb.exe') -D $PgData -U $DbUser --pwfile=$pwFile -E UTF8 --locale=C |
      Out-File (Join-Path $LogDir 'initdb.log') -Encoding utf8
    Remove-Item $pwFile -Force
    if (-not (Test-Path (Join-Path $PgData 'PG_VERSION'))) { throw 'initdb failed - see logs\initdb.log' }
    Ok 'cluster initialised'
  } else {
    Ok 'cluster already initialised'
  }

  # --- Redis ----------------------------------------------------------------
  if (-not (Test-Path (Join-Path $RedisDir 'redis-server.exe'))) {
    $zip = Join-Path $Cache 'redis.zip'
    Fetch 'redis' $Urls.redis $zip
    Unzip $zip $RedisDir
    Ok 'redis unpacked'
  } else {
    Ok 'redis already unpacked'
  }

  # --- Kafka ----------------------------------------------------------------
  if (-not (Test-Path (Join-Path $KafkaDir 'bin\windows\kafka-server-start.bat'))) {
    $tgz = Join-Path $Cache 'kafka.tgz'
    Fetch 'kafka' $Urls.kafka $tgz
    $tmp = Join-Path $Cache 'kafka-unpacked'
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    & tar.exe -xzf $tgz -C $tmp
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    if (Test-Path $KafkaDir) { Remove-Item $KafkaDir -Recurse -Force }
    Move-Item $inner.FullName $KafkaDir
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    Ok 'kafka unpacked'
  } else {
    Ok 'kafka already unpacked'
  }
  Write-KafkaConfig

  # --- MinIO ----------------------------------------------------------------
  New-Item -ItemType Directory -Force -Path $MinioDir, (Join-Path $MinioDir 'data') | Out-Null
  $minioExe = Join-Path $MinioDir 'minio.exe'
  if (-not (Test-Path $minioExe)) {
    try { Fetch 'minio' $Urls.minio $minioExe; Ok 'minio downloaded' }
    catch { Warn 'minio could not be downloaded - photo uploads will not work, everything else will' }
  } else {
    Ok 'minio already downloaded'
  }

  Say "`nSetup done. Next: .\scripts\devstack.ps1 up" 'Green'
}

function Write-KafkaConfig {
  $cfgDir = Join-Path $KafkaDir 'config\kraft'
  New-Item -ItemType Directory -Force -Path $cfgDir | Out-Null
  $logs = (Join-Path $Root 'kafka-logs') -replace '\\', '/'
  @"
# Single-node KRaft, written by devstack.ps1. Not a production configuration.
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@localhost:9093
listeners=PLAINTEXT://:$KafkaPort,CONTROLLER://:9093
advertised.listeners=PLAINTEXT://localhost:$KafkaPort
controller.listener.names=CONTROLLER
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
inter.broker.listener.name=PLAINTEXT
log.dirs=$logs
num.partitions=3
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
group.initial.rebalance.delay.ms=0
auto.create.topics.enable=true
"@ | Set-Content -Path (Join-Path $cfgDir 'radius.properties') -Encoding ascii
}

# ---------------------------------------------------------------------- up ---

function Start-Postgres {
  if (PortBusy $PgPort) { Ok "postgres already listening on $PgPort"; return }
  Say '  ...   starting postgres'
  # Start-Process, not `& pg_ctl | Out-Null`: the server pg_ctl spawns inherits
  # the pipe handle, so a PowerShell pipeline never sees end-of-stream and the
  # script hangs for as long as the database is up.
  Start-Process -FilePath (Join-Path $PgDir 'bin\pg_ctl.exe') `
    -ArgumentList @('-D', $PgData, '-o', "-p $PgPort", '-l', (Join-Path $LogDir 'postgres.log'), 'start') `
    -NoNewWindow -Wait
  for ($i = 0; $i -lt 40 -and -not (PortBusy $PgPort); $i++) { Start-Sleep -Milliseconds 500 }
  if (-not (PortBusy $PgPort)) { throw 'postgres did not start - see logs\postgres.log' }
  Ok "postgres on $PgPort"
}

function Initialize-Databases {
  $psql = Join-Path $PgDir 'bin\psql.exe'
  if (-not (Test-Path $psql)) {
    throw "psql is missing from $PgDir - the PostgreSQL copy in use is server-only. Delete $PgDir and run setup again."
  }
  $env:PGPASSWORD = $DbPass
  $databases = @{
    'radius_user'         = $true
    'radius_listing'      = $true
    'radius_search'       = $true
    'radius_booking'      = $true
    'radius_payment'      = $false
    'radius_notification' = $false
  }
  foreach ($db in $databases.Keys) {
    $exists = & $psql -h localhost -p $PgPort -U $DbUser -d postgres -tAc `
      "SELECT 1 FROM pg_database WHERE datname='$db'"
    if ($exists -ne '1') {
      & $psql -h localhost -p $PgPort -U $DbUser -d postgres -q -c "CREATE DATABASE $db" | Out-Null
      Ok "created $db"
    }
    if ($databases[$db]) {
      & $psql -h localhost -p $PgPort -U $DbUser -d $db -q `
        -c 'CREATE EXTENSION IF NOT EXISTS postgis' `
        -c 'CREATE EXTENSION IF NOT EXISTS pg_trgm' | Out-Null
    }
  }
  Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
  Ok 'databases and extensions ready'
}

function Start-Redis {
  if (PortBusy $RedisPort) { Ok "redis already listening on $RedisPort"; return }
  Say '  ...   starting redis'
  Start-Process -FilePath (Join-Path $RedisDir 'redis-server.exe') `
    -ArgumentList '--port', $RedisPort, '--save', '""' `
    -WorkingDirectory $RedisDir -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $LogDir 'redis.log') `
    -RedirectStandardError (Join-Path $LogDir 'redis.err.log')
  for ($i = 0; $i -lt 20 -and -not (PortBusy $RedisPort); $i++) { Start-Sleep -Milliseconds 300 }
  if (PortBusy $RedisPort) { Ok "redis on $RedisPort" } else { Warn 'redis did not start - see logs\redis.err.log' }
}

function Start-Kafka {
  if (PortBusy $KafkaPort) { Ok "kafka already listening on $KafkaPort"; return }

  $config = Join-Path $KafkaDir 'config\kraft\radius.properties'
  $kafkaLogs = Join-Path $Root 'kafka-logs'
  $java = Join-Path (JavaHome) 'bin\java.exe'

  # Kafka's Windows .bat launchers do not work on a current Windows: they build
  # the classpath jar by jar and blow past cmd's 8191-character command line,
  # and they shell out to wmic, which Microsoft removed. Calling the JVM
  # directly with a wildcard classpath sidesteps both.
  $classpath = Join-Path $KafkaDir 'libs\*'
  $log4j = "-Dlog4j.configuration=file:$((Join-Path $KafkaDir 'config\log4j.properties') -replace '\\', '/')"

  if (-not (Test-Path (Join-Path $kafkaLogs 'meta.properties'))) {
    Say '  ...   formatting kafka storage'
    Start-Process -FilePath $java `
      -ArgumentList @('-cp', "`"$classpath`"", 'kafka.tools.StorageTool', 'format',
                      '-t', 'radius0localcluster001', '-c', $config, '--ignore-formatted') `
      -WorkingDirectory $KafkaDir -NoNewWindow -Wait `
      -RedirectStandardOutput (Join-Path $LogDir 'kafka-format.log') `
      -RedirectStandardError (Join-Path $LogDir 'kafka-format.err.log')
  }

  Say '  ...   starting kafka'
  Start-Process -FilePath $java `
    -ArgumentList @('-Xmx768M', '-Xms256M', '-XX:+UseG1GC',
                    "-Dkafka.logs.dir=$LogDir", $log4j,
                    '-cp', "`"$classpath`"", 'kafka.Kafka', $config) `
    -WorkingDirectory $KafkaDir -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $LogDir 'kafka.log') `
    -RedirectStandardError (Join-Path $LogDir 'kafka.err.log')

  for ($i = 0; $i -lt 90 -and -not (PortBusy $KafkaPort); $i++) { Start-Sleep -Milliseconds 500 }
  if (PortBusy $KafkaPort) { Ok "kafka on $KafkaPort" } else { Warn 'kafka did not start - see logs\kafka.err.log' }
}

function Start-Minio {
  $exe = Join-Path $MinioDir 'minio.exe'
  if (-not (Test-Path $exe)) { Warn 'minio not installed - skipping (photo uploads will fail)'; return }
  if (PortBusy $MinioPort) { Ok "minio already listening on $MinioPort"; return }
  Say '  ...   starting minio'
  $env:MINIO_ROOT_USER = 'radius'
  $env:MINIO_ROOT_PASSWORD = 'radius-secret'
  Start-Process -FilePath $exe `
    -ArgumentList 'server', (Join-Path $MinioDir 'data'), '--console-address', ":$MinioConsolePort" `
    -WorkingDirectory $MinioDir -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $LogDir 'minio.log') `
    -RedirectStandardError (Join-Path $LogDir 'minio.err.log')
  for ($i = 0; $i -lt 20 -and -not (PortBusy $MinioPort); $i++) { Start-Sleep -Milliseconds 300 }
  if (PortBusy $MinioPort) { Ok "minio on $MinioPort (console $MinioConsolePort)" } else { Warn 'minio did not start - see logsminio.err.log' }
}

function Invoke-Up {
  Step "starting the stack in $Root"
  if (-not (Test-Path (Join-Path $PgData 'PG_VERSION'))) { throw 'not set up yet - run: .\scripts\devstack.ps1 setup' }
  Start-Postgres
  Initialize-Databases
  Start-Redis
  Start-Kafka
  Start-Minio
  Invoke-Status
  Say @"

Next:
  .\scripts\services.ps1 up      # build and run the seven Spring Boot services
  cd apps\web; npm install; npm run dev
"@ 'Green'
}

# -------------------------------------------------------------------- down ---

function Invoke-Down {
  Step 'stopping the stack'

  if (Test-Path (Join-Path $PgDir 'bin\pg_ctl.exe')) {
    Start-Process -FilePath (Join-Path $PgDir 'bin\pg_ctl.exe') `
      -ArgumentList @('-D', $PgData, '-m', 'fast', 'stop') -NoNewWindow -Wait -ErrorAction SilentlyContinue
    Ok 'postgres stopped'
  }

  foreach ($name in @('redis-server', 'minio')) {
    Get-Process -Name $name -ErrorAction SilentlyContinue | Stop-Process -Force
    Ok "$name stopped"
  }

  # Kafka is a JVM started from a .bat, so it has to be found by its command line.
  Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*kafka*' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
  Ok 'kafka stopped'
}

function Invoke-Reset {
  Invoke-Down
  Step 'wiping data (binaries are kept)'
  foreach ($d in @($PgData, (Join-Path $Root 'kafka-logs'), (Join-Path $MinioDir 'data'))) {
    if (Test-Path $d) { Remove-Item $d -Recurse -Force; Ok "removed $d" }
  }
  Say "`nRun setup again to re-create the cluster." 'Green'
}

# ------------------------------------------------------------------ status ---

function Invoke-Status {
  Step 'status'
  $checks = @(
    @{ name = 'postgres'; port = $PgPort },
    @{ name = 'redis   '; port = $RedisPort },
    @{ name = 'kafka   '; port = $KafkaPort },
    @{ name = 'minio   '; port = $MinioPort }
  )
  foreach ($c in $checks) {
    if (PortBusy $c.port) {
      Say ("  up      {0}  localhost:{1}" -f $c.name, $c.port) 'Green'
    } else {
      Say ("  down    {0}  localhost:{1}" -f $c.name, $c.port) 'DarkGray'
    }
  }
  Say "`n  home    $Root"
  Say "  logs    $LogDir"
}

# ------------------------------------------------------------------- go -----

switch ($Command) {
  'setup'  { Invoke-Setup }
  'up'     { Invoke-Up }
  'down'   { Invoke-Down }
  'reset'  { Invoke-Reset }
  'status' { Invoke-Status }
}
