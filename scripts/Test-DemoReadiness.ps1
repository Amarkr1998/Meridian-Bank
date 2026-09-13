[CmdletBinding()]
param(
    [string]$FrontendBaseUrl = "http://localhost:5173",
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$PrometheusBaseUrl = "http://localhost:9090",
    [string]$GrafanaBaseUrl = "http://localhost:3000"
)

$ErrorActionPreference = "Stop"

function Test-Endpoint {
    param(
        [Parameter(Mandatory)] [string]$Name,
        [Parameter(Mandatory)] [string]$Uri,
        [scriptblock]$Validate
    )

    try {
        $response = Invoke-RestMethod -Uri $Uri -Method Get -TimeoutSec 10
        if ($Validate -and -not (& $Validate $response)) {
            throw "Response validation failed"
        }
        [pscustomobject]@{ Component = $Name; Status = "READY"; Endpoint = $Uri }
    }
    catch {
        [pscustomobject]@{ Component = $Name; Status = "NOT READY"; Endpoint = $Uri }
        $script:readinessFailed = $true
    }
}

$script:readinessFailed = $false
$results = @(
    Test-Endpoint -Name "Frontend" -Uri $FrontendBaseUrl
    Test-Endpoint -Name "API Gateway" -Uri "$GatewayBaseUrl/actuator/health" `
        -Validate { param($body) $body.status -eq "UP" }
    Test-Endpoint -Name "Prometheus" -Uri "$PrometheusBaseUrl/-/ready"
    Test-Endpoint -Name "Grafana" -Uri "$GrafanaBaseUrl/api/health" `
        -Validate { param($body) $body.database -eq "ok" }
)

$results | Format-Table -AutoSize

if ($script:readinessFailed) {
    Write-Error "The demo stack is not ready. Run 'docker compose ps' and inspect unhealthy container logs."
}

Write-Host "The complete demo is ready. Open $FrontendBaseUrl to begin."
