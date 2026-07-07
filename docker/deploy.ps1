# RSIRanking 배포 스크립트 (PowerShell)
# Docker Hub 이미지 기반 배포
# 사용법: .\docker\deploy.ps1 [명령어] [서비스명]
# 예시:
#   .\docker\deploy.ps1 all        # 전체 배포 (인프라 + 앱)
#   .\docker\deploy.ps1 infra      # 인프라(MySQL x2 + Redis)만
#   .\docker\deploy.ps1 app        # 앱(collector + api + frontend)만 재배포
#   .\docker\deploy.ps1 collector  # collector만 재배포
#   .\docker\deploy.ps1 pull       # 이미지만 미리 다운로드

param(
    [Parameter(Position=0)]
    [string]$Command = "all",

    [Parameter(Position=1)]
    [string]$Service = ""
)

$ErrorActionPreference = "Stop"

$DockerDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Set-Location $DockerDir

# .env 파일 로드
$EnvFile = Join-Path $DockerDir ".env"
if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $val = $matches[2].Trim()
            [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
        }
    }
}

function Write-Info($message) {
    Write-Host "[INFO] $message" -ForegroundColor Green
}

function Write-Warn($message) {
    Write-Host "[WARN] $message" -ForegroundColor Yellow
}

function Write-Err($message) {
    Write-Host "[ERROR] $message" -ForegroundColor Red
}

$InfraFile = "docker-compose.yml"
$AppFile = "docker-compose.app.yml"

$DockerHubId = if ($env:DOCKERHUB_ID) { $env:DOCKERHUB_ID } else { "weseeonggu" }
$Tag = if ($env:TAG) { $env:TAG } else { "latest" }

# 이미지 pull. SSH 비대화 세션에서는 Docker Desktop 자격증명 헬퍼가 죽어
# "A specified logon session does not exist" 오류가 나므로,
# 실패 시 빈 DOCKER_CONFIG(공개 저장소는 인증 불필요)로 한 번 더 시도한다.
function Invoke-Pull {
    param([string]$Image)
    docker pull $Image
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "pull 실패 — 자격증명 헬퍼 우회(빈 DOCKER_CONFIG) 후 재시도: $Image"
        $CleanConfig = Join-Path $DockerDir ".docker-clean"
        New-Item -ItemType Directory -Force -Path $CleanConfig | Out-Null
        Set-Content -Path (Join-Path $CleanConfig "config.json") -Value "{}"
        $env:DOCKER_CONFIG = $CleanConfig
        docker pull $Image
        if ($LASTEXITCODE -ne 0) {
            Write-Err "pull 재시도 실패: $Image"
            exit 1
        }
    }
}

# 서비스별 배포 함수
function Deploy-Infra {
    Write-Info "인프라(MySQL meta/data + Redis) 배포 중..."
    docker compose -f $InfraFile up -d
}

function Deploy-App {
    Write-Info "앱 이미지 Pull 중..."
    foreach ($name in @("rsi-collector", "rsi-api", "rsi-frontend")) {
        Invoke-Pull "$DockerHubId/${name}:$Tag"
    }
    Write-Info "앱(collector + api + frontend) 배포 중..."
    docker compose -f $AppFile up -d --pull never
}

function Deploy-Service {
    param([string]$ServiceName)
    Write-Info "$ServiceName 이미지 Pull 중..."
    Invoke-Pull "$DockerHubId/rsi-${ServiceName}:$Tag"
    Write-Info "$ServiceName 재배포 중..."
    docker compose -f $AppFile up -d --no-deps --force-recreate --pull never $ServiceName
}

function Deploy-All {
    Deploy-Infra
    Write-Info "DB 기동 대기 (10초)..."
    Start-Sleep -Seconds 10
    Deploy-App
    Write-Info "첫 배포라면 collector가 기동 후 자동으로 과거 데이터 캐치업을 수행합니다."
    Write-Info "진행 상황: .\deploy.ps1 logs collector"
}

function Pull-All {
    Write-Info "전체 이미지 미리 다운로드 중..."
    foreach ($name in @("rsi-collector", "rsi-api", "rsi-frontend")) {
        Invoke-Pull "$DockerHubId/${name}:$Tag"
    }
    Write-Info "이미지 다운로드 완료! 'deploy.ps1 all' 로 배포하세요."
}

function Stop-All {
    Write-Info "앱 중지 중..."
    docker compose -f $AppFile down
    Write-Info "인프라 중지 중..."
    docker compose -f $InfraFile down
}

function Show-Status {
    Write-Info "인프라 상태:"
    docker compose -f $InfraFile ps
    Write-Info "앱 상태:"
    docker compose -f $AppFile ps
}

function Show-Logs {
    param([string]$ServiceName)

    if ([string]::IsNullOrEmpty($ServiceName)) {
        docker compose -f $AppFile logs -f --tail=100
    } else {
        docker compose -f $AppFile logs -f --tail=100 $ServiceName
    }
}

function Show-Help {
    Write-Host @"
사용법: .\deploy.ps1 <명령어> [서비스명]

명령어:
  infra             - MySQL(meta/data) + Redis 배포
  app               - 앱 전체(collector + api + frontend) 재배포
  collector         - collector만 재배포 (pull + up)
  api               - API만 재배포 (pull + up)
  frontend          - Frontend만 재배포 (pull + up)
  all               - 전체 배포 (인프라 + 앱)
  pull              - 이미지만 미리 다운로드
  stop              - 전체 중지
  status            - 상태 확인
  logs [service]    - 로그 확인

예시:
  .\deploy.ps1 all             # 전체 배포
  .\deploy.ps1 collector       # collector만 재배포
  .\deploy.ps1 logs collector  # 캐치업 진행 상황 확인
"@
}

# 메인 로직
switch ($Command.ToLower()) {
    "infra" { Deploy-Infra }
    "app" { Deploy-App }
    "collector" { Deploy-Service -ServiceName "collector" }
    "api" { Deploy-Service -ServiceName "api" }
    "frontend" { Deploy-Service -ServiceName "frontend" }
    "all" { Deploy-All }
    "pull" { Pull-All }
    "stop" { Stop-All }
    "status" { Show-Status }
    "logs" { Show-Logs -ServiceName $Service }
    "help" { Show-Help }
    default {
        Write-Err "알 수 없는 명령어: $Command"
        Show-Help
        exit 1
    }
}

Write-Info "완료!"
