param(
    [string]$BaseUrl = "http://host.docker.internal:8000",
    [int]$VUs = 20,
    [string]$Duration = "60s",
    [string]$UserEmail = "admin@example.com",
    [string]$UserPassword = "Pass123!"
)

$ErrorActionPreference = "Stop"

function Clean-Arg {
    param([string]$Value)
    if ($null -eq $Value) { return $Value }
    $clean = $Value.Trim()
    $clean = $clean.Trim("`"")
    $clean = $clean.Trim("'")
    $clean = $clean.Trim()
    return $clean
}

# Defensive cleanup for copy/paste artifacts from markdown (e.g. trailing `)
$BaseUrl = Clean-Arg $BaseUrl
$Duration = Clean-Arg $Duration
$UserEmail = Clean-Arg $UserEmail
$UserPassword = Clean-Arg $UserPassword

$scriptsDir = Join-Path (Get-Location) "backend/scripts"

if (-not (Test-Path $scriptsDir)) {
    throw "No existe la carpeta backend/scripts en el directorio actual."
}

Write-Host "== k6 load test =="
Write-Host "Base URL: $BaseUrl"
Write-Host "VUs: $VUs | Duration: $Duration"

docker run --rm -i `
  -e BASE_URL=$BaseUrl `
  -e VUS=$VUs `
  -e DURATION=$Duration `
  -e TEST_USER=$UserEmail `
  -e TEST_PASSWORD=$UserPassword `
  -v "${scriptsDir}:/scripts" `
  grafana/k6 run /scripts/k6_grafana_load.js
