FROM gradle:7.6-jdk17 AS build

# 빌드할 모듈 선택 (collector | api). 기본값은 collector.
# 예: docker build --build-arg MODULE=api ...
ARG MODULE=collector

# 작업 디렉토리 설정
WORKDIR /app

# 프로젝트 전체 복사 (멀티모듈: common + collector + api)
COPY .. .

# Gradlew에 실행 권한 부여 후 대상 모듈만 빌드
RUN chmod +x ./gradlew
RUN ./gradlew clean :${MODULE}:bootJar -x test

# bootJar 산출물을 스테이지 간 고정 경로로 이동
RUN cp ${MODULE}/build/libs/*SNAPSHOT.jar /app/app.jar

# 실행 이미지 생성
FROM openjdk:17-jdk-slim
WORKDIR /app
# JAR 파일 복사
COPY --from=build /app/app.jar ./app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
