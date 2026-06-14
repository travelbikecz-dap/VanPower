# Crea el repositorio VanPower en GitHub y sube el codigo.
# Requisito: gh auth login (una vez)
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path $PSScriptRoot -Parent
Set-Location $repoRoot

$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) {
    Write-Error "Instala GitHub CLI: winget install GitHub.cli"
}

gh auth status | Out-Null

$existing = git remote 2>$null | Where-Object { $_ -eq 'origin' }
if ($existing) {
    Write-Host "Remote origin ya configurado: $(git remote get-url origin)"
} else {
    gh repo create VanPower --public --source=. --remote=origin --description "VanPower: control Delta 3 desde Android Auto (BLE)"
    Write-Host "Repositorio creado y remote origin configurado."
}

git push -u origin main
Write-Host "Listo. URL:" (gh repo view --json url -q .url)
