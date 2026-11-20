# Run from any directory: powershell -File backend/scripts/start-integration.ps1
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path $PSScriptRoot -Parent
$configPath = Join-Path $backendRoot '.env.integration'
function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}
if (-not (Test-Path $configPath)) {
    $config = @(
        '# Generated local secrets. Never commit this file.',
        "DB_PASSWORD=$(New-LocalSecret)",
        "JWT_SECRET=$(New-LocalSecret)",
        "STORAGE_PASSWORD=$(New-LocalSecret)",
        'MINIO_PUBLIC_ENDPOINT=http://10.0.2.2:9000'
    )
    [System.IO.File]::WriteAllLines($configPath, $config, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host 'Konfigurasi lokal dibuat. Nilai rahasia tidak ditampilkan.'
}
Push-Location $backendRoot
try {
    docker compose --env-file .env.integration -f docker-compose.integration.yml up --build -d
    if ($LASTEXITCODE -ne 0) { throw 'Docker belum berhasil. Periksa Docker Desktop dan log di atas.' }
    Write-Host 'Lihat proses startup: docker compose --env-file backend/.env.integration -f backend/docker-compose.integration.yml logs -f api'
    Write-Host 'Android Emulator: http://10.0.2.2:8080/api/v1/'
} finally { Pop-Location }
