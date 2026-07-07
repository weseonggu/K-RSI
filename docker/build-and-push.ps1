# Docker Hub 이미지 빌드 & 푸시 스크립트 (PowerShell)
# 사용법: .\docker\build-and-push.ps1 [서비스명] [태그]
# 예시:
#   .\docker\build-and-push.ps1 all 1.0.0     # 전체 빌드 & 푸시 (태그: 1.0.0)
#   .\docker\build-and-push.ps1 collector     # 수집기만 (태그: latest)
#   .\docker\build-and-push.ps1 api 2.0       # API만 (태그: 2.0)

param(
    [Parameter(Position=0)]
    [string]$Service = "all",

    [Parameter(Position=1)]
    [string]$Tag = "latest"
)

$ErrorActionPreference = "Stop"

$DockerDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $DockerDir

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

$DockerHubId = if ($env:DOCKERHUB_ID) { $env:DOCKERHUB_ID } else { "weseeonggu" }

function Write-Info($message) {
    Write-Host "[INFO] $message" -ForegroundColor Green
}

function Write-Err($message) {
    Write-Host "[ERROR] $message" -ForegroundColor Red
}

function Push-Image {
    param([string]$FullImage, [string]$ImageTag)

    # latest가 아닌 경우 latest 태그도 함께 부여
    if ($ImageTag -ne "latest") {
        docker tag "${FullImage}:${ImageTag}" "${FullImage}:latest"
    }

    Write-Info "${FullImage}:${ImageTag} 푸시 중..."
    docker push "${FullImage}:${ImageTag}"
    if ($ImageTag -ne "latest") {
        docker push "${FullImage}:latest"
    }

    Write-Info "${FullImage}:${ImageTag} 완료!"
}

# 백엔드(collector/api)는 공용 Dockerfile + MODULE 빌드 인자로 빌드한다
function Build-Backend {
    param([string]$Module, [string]$ImageTag)

    $FullImage = "$DockerHubId/rsi-$Module"

    Write-Info "${FullImage}:${ImageTag} 빌드 중..."
    docker build `
        -f (Join-Path $DockerDir "Dockerfile") `
        --build-arg MODULE=$Module `
        -t "${FullImage}:${ImageTag}" `
        $ProjectRoot

    Push-Image -FullImage $FullImage -ImageTag $ImageTag
}

function Build-Frontend {
    param([string]$ImageTag)

    $FullImage = "$DockerHubId/rsi-frontend"

    Write-Info "${FullImage}:${ImageTag} 빌드 중..."
    docker build -t "${FullImage}:${ImageTag}" (Join-Path $ProjectRoot "frontend")

    Push-Image -FullImage $FullImage -ImageTag $ImageTag
}

function Show-Help {
    Write-Host @"
사용법: .\build-and-push.ps1 <서비스> [태그]

서비스:
  collector - 수집기(배치) 이미지 빌드 & 푸시
  api       - REST API 이미지 빌드 & 푸시
  frontend  - Frontend 이미지 빌드 & 푸시
  all       - 전체 이미지 빌드 & 푸시

예시:
  .\build-and-push.ps1 all 1.0.0
  .\build-and-push.ps1 collector
  .\build-and-push.ps1 api 2.0
"@
}

Write-Info "대상: $DockerHubId/* / 태그: $Tag"

switch ($Service.ToLower()) {
    "collector" { Build-Backend -Module "collector" -ImageTag $Tag }
    "api" { Build-Backend -Module "api" -ImageTag $Tag }
    "frontend" { Build-Frontend -ImageTag $Tag }
    "all" {
        Build-Backend -Module "collector" -ImageTag $Tag
        Build-Backend -Module "api" -ImageTag $Tag
        Build-Frontend -ImageTag $Tag
    }
    "help" { Show-Help }
    default {
        Write-Err "알 수 없는 서비스: $Service"
        Show-Help
        exit 1
    }
}

Write-Info "모든 작업 완료!"
