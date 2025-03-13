FROM gradle:7.6-jdk17 AS build

# 작업 디렉토리 설정
WORKDIR /app

# 루트 프로젝트의 build.gradle 파일과 settings.gradle 파일 복사
COPY settings.gradle ./
COPY build.gradle ./
# Gradle Wrapper 파일을 복사하고 종속성 다운로드
COPY gradlew ./
COPY gradle ./gradle

# Gradlew에 실행 권한 부여
RUN chmod +x ./gradlew

# 모듈 코드 복사
COPY . .

ARG MODULE
# 빌드 수행 - 로그를 파일에 저장
RUN chmod +x ./gradlew
RUN ./gradlew clean build -x test

# 실행 이미지 생성
FROM openjdk:17-jdk-slim
WORKDIR /app
# JAR 파일 복사
COPY --from=build /app/build/libs/*SNAPSHOT.jar ./app.jar

# Set environment variables if needed

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
