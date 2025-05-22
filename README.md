# RSI지표 프로젝트

## 📈 RSI 지표란?
_RSI(Relative Strength Index, 상대강도지수)_ 는
주식이나 암호화폐 등의 자산이 과매수(overbought) 또는 과매도(oversold) 상태인지 판단하는 데 사용되는 대표적인 기술적 지표입니다.
1978년 J. Welles Wilder에 의해 개발되었으며, 0에서 100 사이의 값을 가집니다.

## 프로젝트 목표
배치 애플리 케이션에서 KRX에서 제공하는 API를 사용하여 일일 주식 정보를 배치를 통해서 업데이트하여 RSI지표를 계산합니다.
웹 애플리케이션에서 일별로 RSI의 순위를 볼 수 있는 API를 제공하며 검색 밑 주식에 과거 매매 기록을 볼 수 있도록 제공합니다.

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
## 블록그 글
[K-RSI 종목 업데이트 배치 구현](https://blog.naver.com/fkskdldh/223799505942)<br>
[RSI 프로젝트 일일 매매 정보 업데이트 배치](https://blog.naver.com/fkskdldh/223874475953)
## 트러블 슈팅
[Step 간 데이터 공유 문제](https://blog.naver.com/fkskdldh/223799523690)<br>
[종목 업데이트 배치 성능 테스트 및 jdbc 적용](https://blog.naver.com/fkskdldh/223799536017)<br>
[재시도 적용하기](https://blog.naver.com/fkskdldh/223806769165)<br>
[Redis Template과 제네릭 메서드](https://blog.naver.com/fkskdldh/223812944394)<br>
[조건부 Insert 쿼리 사용](https://blog.naver.com/fkskdldh/223874481464)<br>