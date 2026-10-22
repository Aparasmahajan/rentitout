<#
.SYNOPSIS
  Run Maven over services/ with a host Maven install and a JDK 21 from Docker.

.DESCRIPTION
  For machines where Docker supplies Java and the databases, and the only local
  JDK is too old for these POMs (release 21). The host's Maven is mounted
  read-only into an eclipse-temurin:21-jdk container, and so is ~/.m2/repository,
  so dependencies download once and are shared with the IDE.

    .\scripts\mvn-docker.ps1                          # test (the default)
    .\scripts\mvn-docker.ps1 -DskipTests package
    .\scripts\mvn-docker.ps1 -pl booking-service -am test

  Maven comes from RADIUS_MAVEN_HOME (for example C:\...\apache-maven-3.9.9).
  settings-central.xml is always passed, as in services.ps1.

  No [CmdletBinding()] or param block on purpose: Maven flags such as -DskipTests
  must pass through $args untouched, not be parsed as PowerShell parameters.
#>

$ErrorActionPreference = 'Stop'

$RepoRoot  = Split-Path -Parent $PSScriptRoot
$MavenHome = $env:RADIUS_MAVEN_HOME
if (-not $MavenHome -or -not (Test-Path (Join-Path $MavenHome 'bin\mvn'))) {
  throw 'set RADIUS_MAVEN_HOME to a Maven 3.9.x install (the folder containing bin\mvn)'
}

# Under 'Stop', Windows PowerShell turns a native command's redirected stderr
# into a terminating error, so the probe runs under 'Continue' and is judged by
# its exit code alone.
$ErrorActionPreference = 'Continue'
docker info --format '{{.ServerVersion}}' 2>&1 | Out-Null
$dockerUp = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = 'Stop'
if (-not $dockerUp) { throw 'Docker is not running - start Docker Desktop first' }

# Pull the JDK image explicitly the first time. Left to 'docker run', the pull
# writes its progress to stderr, which 'Stop' turns into a terminating error
# with nothing but "Unable to find image ... locally" to show for it.
$Image = 'eclipse-temurin:21-jdk'
$ErrorActionPreference = 'Continue'
docker image inspect $Image 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
  Write-Host "pulling $Image (first run only)"
  docker pull $Image 2>&1 | Out-Null
  $pulled = ($LASTEXITCODE -eq 0)
  $ErrorActionPreference = 'Stop'
  if (-not $pulled) { throw "could not pull $Image - check the network or proxy" }
}
$ErrorActionPreference = 'Stop'

$M2 = Join-Path $env:USERPROFILE '.m2\repository'
New-Item -ItemType Directory -Force $M2 | Out-Null

# @( ) keeps this an array. A one-element result of an 'if' is unrolled to a
# plain string, and splatting a string hands Maven one letter per argument
# ("Unknown lifecycle phase t") - which is what the bare default did.
$goals = @(if ($args.Count -gt 0) { $args } else { 'test' })

# Maven and the JVM write warnings to stderr (Mockito's "Java agent loaded
# dynamically", for one). When this script's output is redirected, 'Stop'
# turns the first such line into a terminating error and abandons the run, so
# the build runs under 'Continue' and is judged by Maven's exit code alone.
$ErrorActionPreference = 'Continue'
docker run --rm `
  -v "${MavenHome}:/opt/maven:ro" `
  -v "$(Join-Path $RepoRoot 'services'):/build" `
  -v "${M2}:/root/.m2/repository" `
  -w /build `
  $Image `
  /opt/maven/bin/mvn -B -s settings-central.xml @goals
$mavenExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'

if ($mavenExit -ne 0) { throw "maven failed ($mavenExit)" }
