#!/bin/bash

# RSIRanking 배포 스크립트
# Docker Hub 이미지 기반 배포
# 사용법: ./docker/deploy.sh [명령어] [서비스명]
# 예시:
#   ./docker/deploy.sh all        # 전체 배포 (인프라 + 앱)
#   ./docker/deploy.sh infra      # 인프라(MySQL x2 + Redis)만
#   ./docker/deploy.sh app        # 앱(collector + api + frontend)만 재배포
#   ./docker/deploy.sh collector  # collector만 재배포
#   ./docker/deploy.sh pull       # 이미지만 미리 다운로드

set -e

DOCKER_DIR="$(cd "$(dirname "$0")" && pwd)"

cd "$DOCKER_DIR"

# .env 파일 로드
if [ -f "$DOCKER_DIR/.env" ]; then
    export $(grep -v '^#' "$DOCKER_DIR/.env" | xargs)
fi

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

INFRA_FILE="docker-compose.yml"
APP_FILE="docker-compose.app.yml"

# 서비스별 배포 함수
deploy_infra() {
    log_info "인프라(MySQL meta/data + Redis) 배포 중..."
    docker compose -f "$INFRA_FILE" up -d
}

deploy_app() {
    log_info "앱 이미지 Pull 중..."
    docker compose -f "$APP_FILE" pull
    log_info "앱(collector + api + frontend) 배포 중..."
    docker compose -f "$APP_FILE" up -d
}

deploy_service() {
    local SERVICE=$1
    log_info "${SERVICE} 이미지 Pull 중..."
    docker compose -f "$APP_FILE" pull "$SERVICE"
    log_info "${SERVICE} 재배포 중..."
    docker compose -f "$APP_FILE" up -d --no-deps --force-recreate "$SERVICE"
}

deploy_all() {
    deploy_infra
    log_info "DB 기동 대기 (10초)..."
    sleep 10
    deploy_app
    log_info "첫 배포라면 collector가 기동 후 자동으로 과거 데이터 캐치업을 수행합니다."
    log_info "진행 상황: ./deploy.sh logs collector"
}

pull_all() {
    log_info "전체 이미지 미리 다운로드 중..."
    docker compose -f "$APP_FILE" pull
    log_info "이미지 다운로드 완료! './deploy.sh all' 로 배포하세요."
}

stop_all() {
    log_info "앱 중지 중..."
    docker compose -f "$APP_FILE" down
    log_info "인프라 중지 중..."
    docker compose -f "$INFRA_FILE" down
}

show_status() {
    log_info "인프라 상태:"
    docker compose -f "$INFRA_FILE" ps
    log_info "앱 상태:"
    docker compose -f "$APP_FILE" ps
}

show_logs() {
    SERVICE=$1
    if [ -z "$SERVICE" ]; then
        docker compose -f "$APP_FILE" logs -f --tail=100
    else
        docker compose -f "$APP_FILE" logs -f --tail=100 "$SERVICE"
    fi
}

# 메인 로직
case "${1:-all}" in
    infra)
        deploy_infra
        ;;
    app)
        deploy_app
        ;;
    collector)
        deploy_service collector
        ;;
    api)
        deploy_service api
        ;;
    frontend)
        deploy_service frontend
        ;;
    all)
        deploy_all
        ;;
    pull)
        pull_all
        ;;
    stop)
        stop_all
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs "$2"
        ;;
    *)
        echo "사용법: $0 {infra|app|collector|api|frontend|all|pull|stop|status|logs [service]}"
        echo ""
        echo "명령어:"
        echo "  infra             - MySQL(meta/data) + Redis 배포"
        echo "  app               - 앱 전체(collector + api + frontend) 재배포"
        echo "  collector         - collector만 재배포 (pull + up)"
        echo "  api               - API만 재배포 (pull + up)"
        echo "  frontend          - Frontend만 재배포 (pull + up)"
        echo "  all               - 전체 배포 (인프라 + 앱)"
        echo "  pull              - 이미지만 미리 다운로드"
        echo "  stop              - 전체 중지"
        echo "  status            - 상태 확인"
        echo "  logs [service]    - 로그 확인"
        exit 1
        ;;
esac

log_info "완료!"
