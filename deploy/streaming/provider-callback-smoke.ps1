# RTC-006 SRS callback smoke test.
#
# The script exercises the Spring control-plane callback only. It does not
# publish media and therefore does not claim WHIP/WHEP or SRS restart support.
# Obtain short-lived publish/play tokens from the authenticated live API and
# pass them through environment variables or parameters. Secrets and response
# bodies are intentionally never printed.

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$CallbackBase,

    [Parameter(Mandatory = $true)]
    [string]$StreamKey,

    [string]$PublishToken = "",

    [string]$PlayToken = "",

    [string]$CallbackToken = "",

    [string]$ServerId = "smoke-srs",
    [string]$PublishClientId = "smoke-publisher",
    [string]$PlayClientId = "smoke-viewer",
    [switch]$SkipNegative
)

$ErrorActionPreference = "Stop"
$script:passed = 0
$script:failed = 0

function Read-CallbackSecret {
    param([Parameter(Mandatory = $true)][string]$Name, [string]$Value)

    if (-not [string]::IsNullOrWhiteSpace($Value)) { return $Value }
    $secure = Read-Host -Prompt $Name -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

$PublishToken = Read-CallbackSecret -Name "Short-lived ingest token" -Value $PublishToken
$PlayToken = Read-CallbackSecret -Name "Short-lived play token" -Value $PlayToken
$CallbackToken = Read-CallbackSecret -Name "SRS callback token" -Value $CallbackToken
if ([string]::IsNullOrWhiteSpace($PublishToken) -or [string]::IsNullOrWhiteSpace($PlayToken) -or
        [string]::IsNullOrWhiteSpace($CallbackToken)) {
    throw "Callback credentials must not be empty"
}

function Invoke-SrsCallback {
    param(
        [Parameter(Mandatory = $true)][string]$Action,
        [Parameter(Mandatory = $true)][string]$ClientId,
        [Parameter(Mandatory = $true)][string]$Token,
        [Parameter(Mandatory = $true)][string]$Secret
    )

    $base = $CallbackBase.TrimEnd('/')
    $encodedSecret = [Uri]::EscapeDataString($Secret)
    $uri = "$base/$Action`?callback_token=$encodedSecret"
    $form = @{
        action    = $Action
        server_id = $ServerId
        client_id = $ClientId
        stream    = $StreamKey
        token     = $Token
    }
    try {
        $response = Invoke-WebRequest -Method Post -Uri $uri -Body $form -ContentType "application/x-www-form-urlencoded" -TimeoutSec 10
        return [pscustomobject]@{
            status = [int]$response.StatusCode
            body   = ([string]$response.Content).Trim()
        }
    } catch {
        # Do not print the exception: PowerShell may include the callback URL.
        return [pscustomobject]@{ status = 0; body = "" }
    }
}

function Assert-Callback {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Action,
        [Parameter(Mandatory = $true)][string]$ClientId,
        [Parameter(Mandatory = $true)][string]$Token,
        [Parameter(Mandatory = $true)][string]$Secret,
        [Parameter(Mandatory = $true)][bool]$ShouldAccept
    )

    $result = Invoke-SrsCallback -Action $Action -ClientId $ClientId -Token $Token -Secret $Secret
    $accepted = $result.status -eq 200 -and $result.body -eq "0"
    if ($accepted -eq $ShouldAccept) {
        $script:passed++
        Write-Host "PASS $Name" -ForegroundColor Green
    } else {
        $script:failed++
        Write-Host "FAIL $Name (HTTP $($result.status))" -ForegroundColor Red
    }
}

Write-Host "SRS callback smoke" -ForegroundColor Cyan
Assert-Callback -Name "on_publish accepts signed host token" -Action "on_publish" `
    -ClientId $PublishClientId -Token $PublishToken -Secret $CallbackToken -ShouldAccept $true
Assert-Callback -Name "on_play accepts signed viewer token" -Action "on_play" `
    -ClientId $PlayClientId -Token $PlayToken -Secret $CallbackToken -ShouldAccept $true

if (-not $SkipNegative) {
    Assert-Callback -Name "wrong callback secret is rejected" -Action "on_play" `
        -ClientId $PlayClientId -Token $PlayToken -Secret "invalid-callback-secret" -ShouldAccept $false
    Assert-Callback -Name "wrong publish token is rejected" -Action "on_publish" `
        -ClientId $PublishClientId -Token $PlayToken -Secret $CallbackToken -ShouldAccept $false
}

# Close the exact provider generations. Repeating this script is safe: SRS
# retries are expected to produce successful no-op responses.
Assert-Callback -Name "on_stop closes viewer generation" -Action "on_stop" `
    -ClientId $PlayClientId -Token $PlayToken -Secret $CallbackToken -ShouldAccept $true
Assert-Callback -Name "on_unpublish closes publisher generation" -Action "on_unpublish" `
    -ClientId $PublishClientId -Token $PublishToken -Secret $CallbackToken -ShouldAccept $true

Write-Host "Passed: $script:passed; Failed: $script:failed"
if ($script:failed -gt 0) { exit 1 }
