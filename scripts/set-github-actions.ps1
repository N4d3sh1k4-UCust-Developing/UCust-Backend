#Requires -Version 5.1
<#
.SYNOPSIS
    Идемпотентно проставляет secrets/variables UCust в GitHub Actions
    и показывает, чего не хватает.

.DESCRIPTION
    Требует установленный GitHub CLI (https://cli.github.com) и выполненный
    `gh auth login` (или переменную окружения GH_TOKEN с правами repo).

    Скрипт НЕ перезаписывает уже существующие секреты, значения которых не
    переданы явно: если параметр пуст и секрет уже есть — он пропускается.
    Если параметр пуст и секрета нет — для INTERNAL_API_SECRET генерируется
    новое значение, для остальных выводится предупреждение.

.PARAMETER AiOrchestratorEndpoint
    База Единого Шлюза Оркестратора, напр. http://10.0.0.2:8000 (без пути).
    Передайте пустую строку, чтобы не трогать существующий секрет.

.PARAMETER InternalApiSecret
    Общий S2S-секрет X-Internal-Secret. Если пуст и секрета нет — генерируется
    случайное значение и выводится в конце: его же надо прописать в .env
    AI-контура, иначе callback/квоты работать не будут.

.PARAMETER TelegramBotToken
    Токен бота из @BotFather. Нужен только для верификации Telegram-каналов.

.EXAMPLE
    .\scripts\set-github-actions.ps1 -DryRun
    .\scripts\set-github-actions.ps1 -TelegramBotToken '123456:AA...'
#>
[CmdletBinding()]
param(
    [string]$BackendRepo = 'N4d3sh1k4-UCust-Developing/UCust-Backend',
    [string]$OpsRepo = 'N4d3sh1k4-UCust-Developing/ops',

    [string]$AiOrchestratorEndpoint = 'http://10.0.0.2:8000',
    [string]$InternalApiSecret = '',
    [string]$TelegramBotToken = '',
    [string]$TelegramBotProxy = 'http://xray:1080',
    [string]$CorsAllowedOrigins = 'https://ucust.ru,https://www.ucust.ru,https://adm.ucust.n4d3sh1k4.site,https://api.ucust.n4d3sh1k4.site',

    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

# Обязательные секреты бэкенда (по .github/workflows/main.yml).
$BackendSecrets = @(
    'SUBMODULES_PAT', 'SERVER_TAILSCALE_IP', 'SERVER_USER', 'SERVER_SSH_KEY',
    'TS_OAUTH_CLIENT_ID', 'TS_OAUTH_SECRET',
    'DB_PASSWORD', 'JWT_SECRET_ACCESS',
    'DEFAULT_ADMIN_EMAIL', 'DEFAULT_ADMIN_PASSWORD',
    'YANDEX_CLIENT_ID', 'YANDEX_CLIENT_SECRET', 'VK_CLIENT_ID', 'VK_CLIENT_SECRET',
    'GEO_API_URL',
    'AI_ORCHESTRATOR_ENDPOINT', 'INTERNAL_API_SECRET',
    'SUPPORT_EMAIL', 'FRONTEND_URL',
    'MAIL_HOST', 'MAIL_PORT', 'MAIL_USERNAME', 'MAIL_PASSWORD', 'MAIL_SSL', 'MAIL_FROM', 'MAIL_FROM_NAME',
    'CONTENT_MINIO_ACCESS_KEY', 'CONTENT_MINIO_SECRET_KEY', 'CONTENT_MINIO_PUBLIC_BASE_URL'
)
# Необязательный: без него verify Telegram уходит в fallback на t.me.
$BackendOptionalSecrets = @('TELEGRAM_BOT_TOKEN', 'TELEGRAM_BOT_PROXY')
$BackendVariables = @('CORS_ALLOWED_ORIGINS')

$OpsSecrets = @(
    'TS_OAUTH_CLIENT_ID', 'TS_OAUTH_SECRET',
    'SERVER_TAILSCALE_IP', 'SERVER_USER', 'SERVER_SSH_KEY',
    'POSTGRES_PASSWORD', 'RABBITMQ_PASS', 'MINIO_ROOT_PASSWORD'
)

function Assert-Gh {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw 'GitHub CLI не найден. Установите: winget install --id GitHub.cli -e'
    }
    gh auth status *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Нет авторизации в gh. Выполните: gh auth login'
    }
}

function Get-ActionNames {
    param([string]$Kind, [string]$Repo)   # Kind: secret | variable
    $out = & gh $Kind list --repo $Repo 2>$null
    if ($LASTEXITCODE -ne 0) { return @() }
    $out | ForEach-Object { ($_ -split "`t")[0].Trim() } | Where-Object { $_ }
}

function Set-ActionValue {
    param([string]$Kind, [string]$Repo, [string]$Name, [string]$Value)
    if ($DryRun) {
        Write-Host ("  [dry-run] gh {0} set {1} --repo {2} --body ***" -f $Kind, $Name, $Repo)
        return
    }
    & gh $Kind set $Name --repo $Repo --body $Value | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Не удалось задать $Kind $Name в $Repo" }
    Write-Host ("  {0} {1} -> задано" -f $Kind, $Name)
}

function New-SecretValue {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    [Convert]::ToBase64String($bytes).Replace('+', '-').Replace('/', '_').TrimEnd('=')
}

function Show-Gaps {
    param([string]$Repo, [string[]]$Required, [string[]]$Optional, [string[]]$Variables)
    Write-Host ""
    Write-Host "Проверка $Repo" -ForegroundColor Cyan

    $have = Get-ActionNames -Kind 'secret' -Repo $Repo
    $missing = $Required | Where-Object { $have -notcontains $_ }
    $missingOpt = $Optional | Where-Object { $have -notcontains $_ }

    if ($missing.Count -eq 0) { Write-Host "  секреты: все обязательные заданы" -ForegroundColor Green }
    else { Write-Host ("  ОТСУТСТВУЮТ секреты: {0}" -f ($missing -join ', ')) -ForegroundColor Yellow }

    if ($missingOpt.Count -gt 0) {
        Write-Host ("  необязательные не заданы: {0}" -f ($missingOpt -join ', ')) -ForegroundColor DarkYellow
    }

    if ($Variables -and $Variables.Count -gt 0) {
        $haveVars = Get-ActionNames -Kind 'variable' -Repo $Repo
        $missingVars = $Variables | Where-Object { $haveVars -notcontains $_ }
        if ($missingVars.Count -eq 0) { Write-Host "  variables: все заданы" -ForegroundColor Green }
        else { Write-Host ("  ОТСУТСТВУЮТ variables: {0}" -f ($missingVars -join ', ')) -ForegroundColor Yellow }
    }
}

# ---------------------------------------------------------------- main
Assert-Gh

if ($DryRun) { Write-Host "РЕЖИМ DRY-RUN: ничего не меняется`n" -ForegroundColor Magenta }

Write-Host "Бэкенд: $BackendRepo" -ForegroundColor Cyan

# 1. Variable CORS
if ($CorsAllowedOrigins) {
    Set-ActionValue -Kind 'variable' -Repo $BackendRepo -Name 'CORS_ALLOWED_ORIGINS' -Value $CorsAllowedOrigins
}

# 2. Секреты, которые можно задать/обновить автоматически
$existing = Get-ActionNames -Kind 'secret' -Repo $BackendRepo

if ($AiOrchestratorEndpoint) {
    Set-ActionValue -Kind 'secret' -Repo $BackendRepo -Name 'AI_ORCHESTRATOR_ENDPOINT' -Value $AiOrchestratorEndpoint
}

$generatedSecret = $null
if ($InternalApiSecret) {
    Set-ActionValue -Kind 'secret' -Repo $BackendRepo -Name 'INTERNAL_API_SECRET' -Value $InternalApiSecret
}
elseif ($existing -notcontains 'INTERNAL_API_SECRET') {
    $generatedSecret = New-SecretValue
    Set-ActionValue -Kind 'secret' -Repo $BackendRepo -Name 'INTERNAL_API_SECRET' -Value $generatedSecret
}
else {
    Write-Host "  secret INTERNAL_API_SECRET уже задан -> пропущен" -ForegroundColor DarkGray
}

if ($TelegramBotToken) {
    Set-ActionValue -Kind 'secret' -Repo $BackendRepo -Name 'TELEGRAM_BOT_TOKEN' -Value $TelegramBotToken
}
else {
    Write-Host "  secret TELEGRAM_BOT_TOKEN не передан -> пропущен (verify TG будет через t.me)" -ForegroundColor DarkYellow
}

if ($TelegramBotProxy) {
    Set-ActionValue -Kind 'secret' -Repo $BackendRepo -Name 'TELEGRAM_BOT_PROXY' -Value $TelegramBotProxy
}

# 3. Отчёт по пробелам
Show-Gaps -Repo $BackendRepo -Required $BackendSecrets -Optional $BackendOptionalSecrets -Variables $BackendVariables
Show-Gaps -Repo $OpsRepo -Required $OpsSecrets -Optional @() -Variables @()

if ($generatedSecret) {
    Write-Host ""
    Write-Host "Сгенерирован INTERNAL_API_SECRET. Пропишите ТО ЖЕ значение в .env AI-контура:" -ForegroundColor Yellow
    Write-Host "  INTERNAL_API_SECRET=$generatedSecret" -ForegroundColor Yellow
    Write-Host "Без этого callback контура и /internal/quota/* будут отдавать 401." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Готово. Напоминание: push в master запускает пересборку и деплой." -ForegroundColor DarkGray
