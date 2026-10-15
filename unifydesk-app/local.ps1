param(
  [ValidateSet('infra','backend','frontend','up','down')][string]$Action = 'infra',
  [ValidateSet('local','tunnel')][string]$Mode = 'local'
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
# Refresh PATH for tools installed after this terminal was opened.
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
if (!$env:JAVA_HOME) {
  $jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue | Where-Object Name -Like 'jdk-25*' | Select-Object -First 1
  if ($jdk) { $env:JAVA_HOME = $jdk.FullName; $env:Path = "$($jdk.FullName)\bin;$env:Path" }
}
$modeEnvFile = ".env.$Mode"
$composeArgs = @('--env-file', $modeEnvFile, '-f', 'docker-compose.yml', '-f', "compose.$Mode.yaml")
if (!(Test-Path $modeEnvFile)) { throw "Missing $modeEnvFile" }
switch ($Action) {
  'infra' { docker compose @composeArgs up -d --wait postgres redis kafka }
  'up' { docker compose @composeArgs up -d --build }
  'down' { docker compose @composeArgs down }
  'frontend' { Set-Location frontend; npm.cmd run dev -- --host 127.0.0.1 --mode $Mode }
  'backend' {
    foreach ($line in Get-Content $modeEnvFile) {
      if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
      }
    }
    Set-Location backend
    & .\mvnw.cmd -B -ntp spring-boot:run
  }
}
if ($LASTEXITCODE) { exit $LASTEXITCODE }
