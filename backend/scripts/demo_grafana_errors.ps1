param(
    [string]$BaseUrl = "http://localhost:8000",
    [int]$OkBursts = 30,
    [int]$ErrorBursts = 12,
    [int]$DbDownSeconds = 12,
    [int]$Extra4xxSeconds = 0,
    [int]$Extra5xxSeconds = 0,
    [int]$ErrorScale = 1,
    [switch]$Quick1m,
    [int]$RequestTimeoutSec = 1,
    [switch]$Include403,
    [string]$UserEmail = "user@example.com",
    [string]$UserPassword = "Pass123!"
)

$ErrorActionPreference = "Stop"

if ($Quick1m) {
    $OkBursts = 12
    $ErrorBursts = 8
    $DbDownSeconds = 4
    $Extra4xxSeconds = 2
    $Extra5xxSeconds = 2
    $ErrorScale = 1
    $RequestTimeoutSec = 1
}

function Invoke-Request {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = $null,
        [string]$Body = $null
    )

    try {
        if ($Body) {
            Invoke-WebRequest -UseBasicParsing -TimeoutSec $RequestTimeoutSec -Method $Method -Uri $Url -Headers $Headers -ContentType "application/json" -Body $Body | Out-Null
        } else {
            Invoke-WebRequest -UseBasicParsing -TimeoutSec $RequestTimeoutSec -Method $Method -Uri $Url -Headers $Headers | Out-Null
        }
    } catch {
        # Expected for forced 4xx/5xx demo cases.
    }
}

function Send-OkTraffic {
    param([int]$Count)
    for ($i = 0; $i -lt $Count; $i++) {
        Invoke-Request -Method "GET" -Url "$BaseUrl/health"
        Start-Sleep -Milliseconds 120
    }
}

function Send-4xxTraffic {
    param([int]$Count)
    for ($i = 0; $i -lt $Count; $i++) {
        # 401
        Invoke-Request -Method "GET" -Url "$BaseUrl/users/me"
        # 400
        Invoke-Request -Method "GET" -Url "$BaseUrl/products/?order_by=invalid"
        Start-Sleep -Milliseconds 180
    }
}

function Send-5xxTraffic {
    param([int]$Count)
    for ($i = 0; $i -lt $Count; $i++) {
        try {
            Invoke-WebRequest `
                -UseBasicParsing `
                -TimeoutSec $RequestTimeoutSec `
                -Method "POST" `
                -Uri "$BaseUrl/auth/login" `
                -ContentType "application/x-www-form-urlencoded" `
                -Body @{
                    username = "admin@example.com"
                    password = "Pass123!"
                } | Out-Null
        } catch {
            # Expected while DB is down.
        }
        Start-Sleep -Milliseconds 220
    }
}

function Get-UserToken {
    param(
        [string]$Email,
        [string]$Password
    )
    try {
        $response = Invoke-WebRequest `
            -UseBasicParsing `
            -TimeoutSec $RequestTimeoutSec `
            -Method "POST" `
            -Uri "$BaseUrl/auth/login" `
            -ContentType "application/x-www-form-urlencoded" `
            -Body @{
                username = $Email
                password = $Password
            }

        $payload = $response.Content | ConvertFrom-Json
        if ($payload.access_token) {
            return [string]$payload.access_token
        }
    } catch {
        return $null
    }
    return $null
}

function Send-403Traffic {
    param([int]$Count)
    $token = Get-UserToken -Email $UserEmail -Password $UserPassword
    if (-not $token) {
        Write-Host "No se pudo obtener token USER; se omite tramo 403."
        return
    }
    $headers = @{ Authorization = "Bearer $token" }
    for ($i = 0; $i -lt $Count; $i++) {
        Invoke-Request -Method "GET" -Url "$BaseUrl/users/admin-only" -Headers $headers
        Start-Sleep -Milliseconds 180
    }
}

Write-Host "== Grafana demo traffic =="
Write-Host "Base URL: $BaseUrl"

$scaledErrorBursts = [Math]::Max(1, $ErrorBursts * [Math]::Max(1, $ErrorScale))

try {
    Write-Host "[1/5] Sending baseline 2xx traffic..."
    Send-OkTraffic -Count $OkBursts

    Write-Host "[2/5] Sending controlled 4xx traffic..."
    Send-4xxTraffic -Count $scaledErrorBursts

    if ($Include403) {
        Write-Host "[2b/5] Sending controlled 403 traffic..."
        Send-403Traffic -Count ([Math]::Max(6, [int]($scaledErrorBursts / 2)))
    }

    if ($Extra4xxSeconds -gt 0) {
        Write-Host "[2c/5] Extending 4xx window for $Extra4xxSeconds seconds..."
        $until4xx = (Get-Date).AddSeconds($Extra4xxSeconds)
        while ((Get-Date) -lt $until4xx) {
            Send-4xxTraffic -Count 1
            if ($Include403) {
                Send-403Traffic -Count 1
            }
        }
    }

    Write-Host "[3/5] Stopping DB to force 5xx..."
    docker compose -f backend/docker-compose.yml stop db | Out-Null

    Write-Host "[4/5] Sending traffic while DB is down..."
    Send-5xxTraffic -Count $scaledErrorBursts
    if ($Extra5xxSeconds -gt 0) {
        Write-Host "[4b/5] Extending 5xx window for $Extra5xxSeconds seconds..."
        $until5xx = (Get-Date).AddSeconds($Extra5xxSeconds)
        while ((Get-Date) -lt $until5xx) {
            Send-5xxTraffic -Count 1
        }
    }
    Start-Sleep -Seconds $DbDownSeconds
}
finally {
    Write-Host "[5/5] Restoring DB and final 2xx traffic..."
    docker compose -f backend/docker-compose.yml up -d db | Out-Null

    $dbReady = $false
    for ($i = 0; $i -lt 30; $i++) {
        $dbPs = docker compose -f backend/docker-compose.yml ps db
        if ($dbPs -match "Up|running") {
            $dbReady = $true
            break
        }
        Start-Sleep -Seconds 1
    }

    if (-not $dbReady) {
        Write-Host "Aviso: DB no confirmada como activa tras restauracion."
    }

    Start-Sleep -Seconds 3
    Send-OkTraffic -Count ([Math]::Max(8, [int]($OkBursts / 3)))
    Write-Host "Done. Open Grafana and set time range to Last 2 minutes."
}
