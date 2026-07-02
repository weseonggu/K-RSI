-- =====================================================================
-- (isu_cd, date) 유니크 제약 추가 마이그레이션
-- 대상 DB: RSIData (localhost:3308)
--
-- 배경:
--   엔티티에는 @UniqueConstraint(date, isu_cd)가 선언되어 있으나
--   hibernate.hbm2ddl.auto=update 는 기존 테이블에 유니크 제약을
--   추가해 주지 않는다. 실 테이블에 제약이 없으면 배치 재실행 시
--   중복 행이 쌓이고, RSI 계산이 "조회 결과 14건" 전제에 어긋나
--   해당 종목이 통째로 스킵된다.
--
-- 실행 방법 (DB 컨테이너 기동 후):
--   docker exec -i RSI-data mysql -u<user> -p<password> RSIData < sql/2026-07-02_add-unique-daily-trading.sql
--
-- 주의: 2단계(중복 제거)는 DELETE를 수행하므로 실행 전 백업 권장.
-- =====================================================================

-- ---------------------------------------------------------------
-- 0. 현재 상태 확인 (제약 존재 여부)
-- ---------------------------------------------------------------
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
FROM information_schema.statistics
WHERE table_schema = 'RSIData'
  AND table_name IN ('kospi_daily_trading_information', 'kosdaq_daily_trading_information')
  AND non_unique = 0
GROUP BY table_name, index_name;

-- ---------------------------------------------------------------
-- 1. 중복 현황 확인 (건수가 0이면 2단계 생략 가능)
-- ---------------------------------------------------------------
SELECT 'kospi' AS market, COUNT(*) AS duplicated_rows
FROM (
    SELECT isu_cd, date
    FROM kospi_daily_trading_information
    GROUP BY isu_cd, date
    HAVING COUNT(*) > 1
) d
UNION ALL
SELECT 'kosdaq', COUNT(*)
FROM (
    SELECT isu_cd, date
    FROM kosdaq_daily_trading_information
    GROUP BY isu_cd, date
    HAVING COUNT(*) > 1
) d;

-- ---------------------------------------------------------------
-- 2. 중복 제거: (isu_cd, date)별로 id가 가장 작은 행만 남긴다.
--    RSI 갱신은 UPDATE ... WHERE isu_cd AND date 로 모든 중복 행에
--    동일하게 적용되므로 어느 행을 남겨도 값은 같다.
-- ---------------------------------------------------------------
DELETE t
FROM kospi_daily_trading_information t
JOIN (
    SELECT isu_cd, date, MIN(id) AS keep_id
    FROM kospi_daily_trading_information
    GROUP BY isu_cd, date
    HAVING COUNT(*) > 1
) d ON t.isu_cd = d.isu_cd AND t.date = d.date AND t.id <> d.keep_id;

DELETE t
FROM kosdaq_daily_trading_information t
JOIN (
    SELECT isu_cd, date, MIN(id) AS keep_id
    FROM kosdaq_daily_trading_information
    GROUP BY isu_cd, date
    HAVING COUNT(*) > 1
) d ON t.isu_cd = d.isu_cd AND t.date = d.date AND t.id <> d.keep_id;

-- ---------------------------------------------------------------
-- 3. 유니크 제약 추가 (이미 존재하면 Duplicate key name 에러 - 무시 가능)
-- ---------------------------------------------------------------
ALTER TABLE kospi_daily_trading_information
    ADD CONSTRAINT uk_kospi_daily_isu_date UNIQUE (isu_cd, date);

ALTER TABLE kosdaq_daily_trading_information
    ADD CONSTRAINT uk_kosdaq_daily_isu_date UNIQUE (isu_cd, date);

-- ---------------------------------------------------------------
-- 4. 검증
-- ---------------------------------------------------------------
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
FROM information_schema.statistics
WHERE table_schema = 'RSIData'
  AND table_name IN ('kospi_daily_trading_information', 'kosdaq_daily_trading_information')
  AND non_unique = 0
GROUP BY table_name, index_name;
