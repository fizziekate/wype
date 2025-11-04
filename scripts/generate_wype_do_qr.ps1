param(
  [Parameter(Mandatory = $true)] [string]$JsonPath,
  [Parameter(Mandatory = $true)] [string]$OutputPath
)

if (-not (Test-Path $JsonPath)) {
  Write-Error "Provisioning JSON not found at: $JsonPath"
  exit 1
}

# Read JSON and normalize to a single-line string for QR encoding
$raw = Get-Content -Raw -Path $JsonPath
try {
  $obj = $raw | ConvertFrom-Json
  $jsonCompact = $obj | ConvertTo-Json -Compress
} catch {
  Write-Error "Invalid JSON in $JsonPath"
  exit 1
}

# Ensure output directory exists
$dir = Split-Path -Parent $OutputPath
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

# Option A: Node 'qrcode' CLI (no global install required)
# This will create the PNG at $OutputPath
# Example:
#   npx --yes qrcode -o "$OutputPath" "$jsonCompact"

Write-Host "Ready to generate QR. Example command:"
Write-Host "npx --yes qrcode -o `"$OutputPath`" `"$jsonCompact`""
Write-Host "Or use a PowerShell QR module and pipe `$jsonCompact into it."

# Do not auto-execute generation to keep this step non-destructive.
