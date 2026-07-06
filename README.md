# RSI지표 프로젝트

## 📈 RSI 지표란?
_RSI(Relative Strength Index, 상대강도지수)_ 는
주식이나 암호화폐 등의 자산이 과매수(overbought) 또는 과매도(oversold) 상태인지 판단하는 데 사용되는 대표적인 기술적 지표입니다.
1978년 J. Welles Wilder에 의해 개발되었으며, 0에서 100 사이의 값을 가집니다.

## 프로젝트 목표
배치 애플리 케이션에서 KRX에서 제공하는 API를 사용하여 일일 주식 정보를 배치를 통해서 업데이트하여 RSI지표를 계산합니다.
웹 애플리케이션에서 일별로 RSI의 순위를 볼 수 있는 API를 제공하며 검색 밑 주식에 과거 매매 기록을 볼 수 있도록 제공합니다.

## 프로젝트 구조 (Gradle 멀티모듈)
```
RSIRanking/
├── common/      # 공통 모듈: 엔티티, 공용 DTO, 리포지토리(DAO), 데이터 DB/Redis 설정
├── collector/   # 데이터 수집기: KRX 수집·RSI 계산 Spring Batch 애플리케이션 (port 8080)
├── api/         # REST API 서버: 일별 RSI 순위 조회 (port 8081)
└── frontend/    # Vue 3 + Vite 프론트엔드 (dev port 3000, /api → 8081 프록시)
```
collector와 api는 동일한 데이터 DB(MySQL)와 Redis를 공유하며,
영속성 계층은 common 모듈 한 곳에서만 정의한다.

### 실행
```bash
# 인프라 (MySQL x2, Redis)
docker compose -f docker/docker-compose.yml up -d

# 수집기 / API 서버 (환경변수는 .env.example 참고)
./gradlew :collector:bootRun
./gradlew :api:bootRun

# 프론트엔드
cd frontend && npm install && npm run dev   # http://localhost:3000
```

## 개발기간
2025.03 ~ 개발 중

## 참여자
위성구

## 기술 스택
<span>
  <img src="https://img.shields.io/badge/java-007396?style=for-the-badge&logo=java&logoColor=white">
  <img src="https://img.shields.io/badge/springboot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/springbatch-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/springdatajpa-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/mysql-4479A1?style=for-the-badge&logo=mysql&logoColor=white">
  <img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=Redis&logoColor=white">
</span>



## 아키택처
<img src="/img/architecture.png" width="70%"></img>
## 블로그
[K-RSI 종목 업데이트 배치 구현](https://blog.naver.com/fkskdldh/223799505942)<br>
[RSI 프로젝트 일일 매매 정보 업데이트 배치](https://blog.naver.com/fkskdldh/223874475953)<br>
[RSI 계산](https://blog.naver.com/fkskdldh/223874613532)<br>
## 트러블 슈팅
[Step 간 데이터 공유 문제](https://blog.naver.com/fkskdldh/223799523690)<br>
[종목 업데이트 배치 성능 테스트 및 jdbc 적용](https://blog.naver.com/fkskdldh/223799536017)<br>
[재시도 적용하기](https://blog.naver.com/fkskdldh/223806769165)<br>
[Redis Template과 제네릭 메서드](https://blog.naver.com/fkskdldh/223812944394)<br>
[조건부 Insert 쿼리 사용](https://blog.naver.com/fkskdldh/223874481464)<br>