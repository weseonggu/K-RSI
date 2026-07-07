#!/bin/bash

# Docker Hub 이미지 빌드 & 푸시 스크립트
# 사용법: ./docker/build-and-push.sh [서비스명] [태그]
# 예시:
#   ./docker/build-and-push.sh all 1.0.0     # 전체 빌드 & 푸시 (태그: 1.0.0)
#   ./docker/build-and-push.sh collector     # 수집기만 (태그: latest)
#   ./docker/build-and-push.sh api 2.0       # API만 (태그: 2.0)

set -e

DOCKER_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$DOCKER_DIR")"

SERVICE="${1:-all}"
TAG="${2:-latest}"

# .env에서 DOCKERHUB_ID 로드
if [ -f "$DOCKER_DIR/.env" ]; then
    export $(grep -v '^#' "$DOCKER_DIR/.env" | xargs)
fi

DOCKERHUB_ID="${DOCKERHUB_ID:-weseeonggu}"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 백엔드(collector/api)는 공용 Dockerfile + MODULE 빌드 인자로 빌드한다
build_backend() {
    local MODULE=$1
    local IMAGE_TAG=$2
    local FULL_IMAGE="${DOCKERHUB_ID}/rsi-${MODULE}"

    log_info "${FULL_IMAGE}:${IMAGE_TAG} 빌드 중..."
    docker build \
        -f "$DOCKER_DIR/Dockerfile" \
        --build-arg MODULE="$MODULE" \
        -t "${FULL_IMAGE}:${IMAGE_TAG}" \
        "$PROJECT_ROOT"

    push_image "$FULL_IMAGE" "$IMAGE_TAG"
}

build_frontend() {
    local IMAGE_TAG=$1
    local FULL_IMAGE="${DOCKERHUB_ID}/rsi-frontend"

    log_info "${FULL_IMAGE}:${IMAGE_TAG} 빌드 중..."
    docker build -t "${FULL_IMAGE}:${IMAGE_TAG}" "$PROJECT_ROOT/frontend"

    push_image "$FULL_IMAGE" "$IMAGE_TAG"
}

push_image() {
    local FULL_IMAGE=$1
    local IMAGE_TAG=$2

    # latest가 아닌 경우 latest 태그도 함께 부여
    if [ "$IMAGE_TAG" != "latest" ]; then
        docker tag "${FULL_IMAGE}:${IMAGE_TAG}" "${FULL_IMAGE}:latest"
    fi

    log_info "${FULL_IMAGE}:${IMAGE_TAG} 푸시 중..."
    docker push "${FULL_IMAGE}:${IMAGE_TAG}"
    if [ "$IMAGE_TAG" != "latest" ]; then
        docker push "${FULL_IMAGE}:latest"
    fi

    log_info "${FULL_IMAGE}:${IMAGE_TAG} 완료!"
}

show_help() {
    echo "사용법: $0 <서비스> [태그]"
    echo ""
    echo "서비스:"
    echo "  collector - 수집기(배치) 이미지 빌드 & 푸시"
    echo "  api       - REST API 이미지 빌드 & 푸시"
    echo "  frontend  - Frontend 이미지 빌드 & 푸시"
    echo "  all       - 전체 이미지 빌드 & 푸시"
    echo ""
    echo "예시:"
    echo "  $0 all 1.0.0"
    echo "  $0 collector"
    echo "  $0 api 2.0"
}

log_info "대상: ${DOCKERHUB_ID}/* / 태그: ${TAG}"

case "$SERVICE" in
    collector)
        build_backend "collector" "$TAG"
        ;;
    api)
        build_backend "api" "$TAG"
        ;;
    frontend)
        build_frontend "$TAG"
        ;;
    all)
        build_backend "collector" "$TAG"
        build_backend "api" "$TAG"
        build_frontend "$TAG"
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        log_error "알 수 없는 서비스: $SERVICE"
        show_help
        exit 1
        ;;
esac

log_info "모든 작업 완료!"
