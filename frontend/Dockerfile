FROM node:22-alpine AS build

WORKDIR /app

# 의존성 캐시 레이어 분리
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# 실행 이미지: nginx가 정적 파일 서빙 + /api 를 api 컨테이너로 프록시
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
