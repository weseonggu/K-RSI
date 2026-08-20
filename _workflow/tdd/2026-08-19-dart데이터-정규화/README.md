---
title: DART 사업보고서 분류 입력 정규화
status: draft
created: 2026-08-19
branch: feat/dart-business-report-normalization
---

# TDD 개발 계획서: DART 사업보고서 분류 입력 정규화

## 1. 목적

DART 사업보고서의 `II. 사업의 내용` 전체를 그대로 분류기에 넘기지 않고, 사업 정체성을 설명하는 `사업 개요`와 `주요 제품 및 서비스`를 우선 선별해 길이가 제한되고 재현 가능한 분류 입력과 SHA-256 해시를 만든다. 원문 본문과 원문 해시는 감사·재처리를 위해 그대로 보존하며, 정규화 결과와 생략·fallback 사유를 별도 필드로 노출한다.

## 2. 배경 / 현재 상태

- `collector/src/main/java/com/service/RSIranking/dart/BusinessSectionParser.java`는 ZIP의 각 엔트리를 MS949 또는 XML 선언의 UTF-8로 디코딩하고, `<TITLE>` 중 `II/Ⅱ/2. 사업의 내용`부터 다음 `III/Ⅲ/3.` 직전까지를 Jsoup 텍스트로 평탄화한다. 탭·수직 공백·CR·연속 스페이스를 정리한 뒤 UTF-8 SHA-256을 계산하며, 여러 ZIP 엔트리 중 가장 긴 성공 결과를 선택한다.
- 현재 `ParsedBusinessSection`에는 평탄화된 `text`, `hash`만 있어 하위 제목의 경계 정보가 사라진다. 따라서 본문에 들어 있는 교차 참조와 실제 하위 제목을 정규화 단계에서 문자열 검색만으로 안전하게 구분할 수 없다. 실제 네이버 결과에는 본문 안의 `'II. 사업의 내용 - 2. 주요제품 및 서비스'` 참조가 실제 `2. 주요 제품 및 서비스` 제목보다 먼저 나온다.
- `DartBusinessReportService.collect`는 `businessText`, `businessHash`를 `DartBusinessReport`에 옮기고 `status=SUCCESS`, 빈 `warnings`를 반환한다. 현재 이 레코드와 서비스의 사용처는 DART 패키지와 수동 live runner뿐이며 `common` 영속 모델, `api`, 정규 배치에는 연결되어 있지 않다.
- `collector/build/dart-spike/summary.json` 및 종목별 JSON을 확인한 결과 10/10이 `SUCCESS`이고 저장된 `businessHash` 10개 모두 현재 `businessText`의 UTF-8 SHA-256과 일치한다. 길이는 다음과 같다.

| 구분 | 종목 코드 | 원문 길이(문자) | 확인된 구조 |
|------|-----------|----------------|---------------|
| 일반 범위 8개 | 068270, 042700, 005380, 005930, 035420, 277810, 454910, 365550 | 1,723~5,511 | 대부분 `1. 사업의 개요`와 `2. 주요 제품 및 서비스`; 005380은 제목에 `(제조서비스업)` 접두어와 II 직후 preamble이 있고, 365550은 제품·서비스가 해당 없음으로 기재됨 |
| 다사업 지주 | 402340(SK스퀘어) | 17,437 | `1. 사업의 개요`가 약 15,154자까지 이어진 뒤 `2. 주요 제품 및 서비스`가 나옴 |
| 금융지주 | 105560(KB금융) | 138,703 | `1. 사업의 개요` 안에 `가. 사업의 내용(요약)`이 있고 약 2,294자 지점부터 `2. 영업의 현황`과 대규모 수치 표·계열사별 설명이 이어짐; 정확한 `주요 제품 및 서비스` 제목은 없음 |

- 일반 8개의 5,511자는 **선택 group 길이가 아니라 평탄화된 II 전체 길이**다. 따라서 6,000 code point는 정확도나 tokenizer에 근거한 값이 아니라 현재 corpus 전체를 수용하는 초기 heuristic(관측 최댓값 대비 약 8.9% 여유)이다. 전체 앞부분만 자르는 방식은 SK스퀘어의 15,154자 뒤 제품·서비스를 잃으므로 products 예산을 별도로 예약해야 한다.
- 현재 focused 테스트는 `BusinessSectionParserTest` 2개와 `CorpCodeParserTest` 1개다. 2026-08-19에 `./gradlew :collector:test --tests "com.service.RSIranking.dart.*"`를 재실행해 3개 모두 통과(4초)했다. 기존 스파이크 기록상 전체 `:collector:test`는 3분 타임아웃으로 완료 여부가 확인되지 않았으므로 구현 후 다시 실행해야 한다.
- 로컬 JSON은 평탄화 결과만 보존하고 원문 ZIP을 의도적으로 폐기한다. 따라서 하위 제목이 실제 원문에서 모두 `<TITLE>`인지 여부는 현재 artifact만으로 검증할 수 없다. 2026-08-19에 저장된 receipt 10개를 대상으로 메모리 전용 manifest를 시도했으나 DART가 모두 `HTTP 302 -> /error1.html`로 돌려보내 관측에 실패했고 파일은 저장하지 않았다. 이 때문에 제목 selector/level 관측은 승인 및 Red 이전의 차단 prerequisite(P0)로 명시한다.

## 3. 변경 범위

### 3.1 변경/신규 파일

| 파일 | 신규/수정 | 설명 |
|------|----------|------|
| `collector/src/main/java/com/service/RSIranking/dart/BusinessSectionParser.java` | 수정 | II 구간의 원문 텍스트/해시는 유지하면서 실제 제목 요소 기준 하위 블록과 II 직후 preamble을 함께 반환하고, 본문 속 제목 문자열을 경계로 오인하지 않음 |
| `collector/src/main/java/com/service/RSIranking/dart/BusinessReportNormalizer.java` | 신규 | 제목 분류, 공백 canonicalization, 의미 블록 선택, code-point 안전 길이 제한, 분류 해시와 warning 생성 |
| `collector/src/main/java/com/service/RSIranking/dart/ClassificationSection.java` | 신규 | `classificationSections`의 허용 enum 값 `OVERVIEW`, `PRODUCTS_AND_SERVICES`, `FALLBACK` 정의 |
| `collector/src/main/java/com/service/RSIranking/dart/NormalizationWarning.java` | 신규 | 누락·빈 section·fallback·truncation의 닫힌 warning enum 정의 |
| `collector/src/main/java/com/service/RSIranking/dart/DartBusinessReport.java` | 수정 | 원문 필드 뒤에 `classificationText`, `classificationHash`, `classificationSections`를 추가하고 기존 `warnings`에 정규화 경고를 전달 |
| `collector/src/main/java/com/service/RSIranking/dart/DartBusinessReportService.java` | 수정 | 파서 결과를 normalizer에 전달하고 원문·분류 입력·각 해시를 함께 조립 |
| `collector/src/test/java/com/service/RSIranking/dart/BusinessSectionParserTest.java` | 수정 | 하위 제목 경계, preamble, 본문 교차 참조, 다음 대단원 제외, 기존 오류 동작의 Red/회귀 테스트 추가 |
| `collector/src/test/java/com/service/RSIranking/dart/BusinessReportNormalizerTest.java` | 신규 | 허용 제목, 선택 순서, whitespace/hash, 길이 제한, 누락/fallback, Unicode 경계의 단위 테스트 |
| `collector/src/test/java/com/service/RSIranking/dart/DartBusinessReportServiceTest.java` | 신규 | mock `DartClient`로 원문/분류 결과와 warning 전달을 검증하는 무네트워크 단위 테스트 |
| `collector/src/test/resources/dart/business-section-baseline.xml` | 신규 | 변경 전 parser의 exact text/hash 호환성 oracle로 사용할 고정 합성 XML |
| `collector/src/test/java/com/service/RSIranking/integration/batch/live/DartBusinessReportSpikeRunner.java` | 수정 | 종목별 JSON/summary에 정규화 길이·해시·선택 섹션·warning을 기록하고, 원문 ZIP은 계속 남기지 않음 |

`common`의 엔티티·DTO·repository·DB/Redis 설정은 바뀌지 않는다. 로직은 시장 구분을 받지 않고 종목코드에 동일 적용되므로 KOSPI/KOSDAQ 분기나 프로필 비대칭을 만들지 않는다. 구조 키워드와 디렉터리 트리는 그대로여서 `collector/AGENTS.md`의 트리 설명 변경은 필요 없다.

### 3.2 제외 범위 (이번에 하지 않을 것)

- LLM 호출, 임베딩, 업종 taxonomy 정의, prompt 설계, 모델별 tokenizer/token 예산 조정
- 정규화된 결과의 DB/Redis 영속화, `common` 스키마/DTO, `api` 응답, Spring Batch Job/스케줄러
- 사업보고서 선택 정책(최근 2년, 최신 사업보고서), DART 호출 throttle/retry, corp-code 매핑 변경
- `III. 재무에 관한 사항` 이후 추출, 정정공시 간 비교, 연결/별도 보고서 합성
- 표를 자연어로 재작성하거나 매출 비율을 계산하는 의미 변환
- 원문 ZIP 또는 10개 전체 사업 본문의 Git 추적. API 키와 보고서 원문은 테스트 fixture에 넣지 않는다.

## 4. 구현 설계

### 4.1 Red 단계

0. **P0 prerequisite가 먼저다.** 4.2의 pinned-receipt manifest를 성공시켜 실제 selector/level 표와 종목별 semantic match 수를 이 문서 4.2에 추가하고 plan-reviewer 재검토를 받아야 한다. P0가 실패하거나 DART가 응답하지 않으면 `status: draft`를 유지하고 test writer/implementer를 시작하지 않는다.
1. 승인 후 `tdd_test_writer`(`gpt-5.6-terra`, medium)가 T1~T20과 fixture를 `src/test`에만 작성한다. 신규 production class/accessor가 없으므로 첫 focused 실행은 예상된 **compile Red**다. 이 실패 로그와 case-to-symbol 매핑을 handoff한다.
2. `tdd_implementer`(`gpt-5.6-terra`, medium)는 compile Red를 받은 뒤 테스트를 건드리지 않고, 계획에 적힌 constructor/record/enum/method signature와 모든 메서드가 빈 결과 또는 `UnsupportedOperationException`을 내는 최소 production API skeleton만 만든다. focused 테스트를 다시 실행해 T1~T20의 **assertion/exception Red**를 한 번에 기록한다. skeleton 생성과 이 두 번째 Red는 같은 implementer 작업의 첫 checkpoint이며 Green 구현으로 간주하지 않는다.
3. implementer는 parser(T1~T5) → normalizer grammar/선택(T6~T12) → 예산·상태·결정성(T13~T18) → service/live 배선(T19~T20) 순으로 최소 구현하고, 각 묶음의 이전 Red와 이후 Green을 기록한다. 테스트 추가·수정은 test writer에게만 돌아가며 implementer는 테스트를 완화하지 않는다.
4. 최종 `tdd_verifier`(`gpt-5.6-terra`, medium, read-only)가 case ID, exact oracle, focused/full test, 범위 외 변경을 독립 확인한다.

Red 완료 기준은 “첫 compile Red + skeleton 후 각 case 묶음의 의미 있는 assertion Red”다. T1~T20을 각각 별도 커맨드로 20회 실패시킬 필요는 없지만, 결과 로그에서 모든 ID가 어느 실패 assertion으로 보호되는지 추적 가능해야 한다.

### 4.2 파서가 보존할 구조

- `ParsedBusinessSection` 계약은 `String text`, `String hash`, `String preamble`, `List<BusinessSubsection> subsections`다. `BusinessSubsection`은 `String sourceHeading`, `String body`, 관측으로 확정한 `HeadingLevel level`을 가진다. `body`는 source heading 자체를 **포함하지 않으며**, 해당 heading 닫힘 직후부터 관측된 같은 level의 다음 heading 시작 직전까지다. 목록과 문자열은 source order이고 목록은 `List.copyOf`로 방어 복사한다.
- II/III 대단원 탐지, 기존 원문 `text/hash`, 여러 ZIP 엔트리 중 Java `String.length()`가 가장 긴 결과 선택은 그대로 유지한다. 평탄화된 본문 문자열은 heading 경계 입력으로 쓰지 않는다.
- preamble은 II heading 닫힘 직후부터 첫 `OVERVIEW` source heading 시작 직전까지의 serialize된 nonblank body다. **인식된 OVERVIEW가 있을 때만** 그 group 앞에 붙으며 overview content 예산에 포함된다. overview가 없으면 preamble을 별도 overview로 승격하지 않고 fallback 원문에만 남긴다.

#### P0: 승인 전 비영속 heading manifest

P0는 아래 receipt를 `findLatestAnnualReport` 없이 직접 한 번씩 다운로드한다. 응답 byte/ZIP/XML/본문은 메모리에서만 처리하고 파일·Gradle report·로그에 남기지 않는다.

| 종목 | pinned receipt | 종목 | pinned receipt |
|------|----------------|------|----------------|
| 005930 | 20260310002820 | 035420 | 20260313001021 |
| 005380 | 20260318001394 | 454910 | 20260318001562 |
| 277810 | 20260320000803 | 068270 | 20260316001415 |
| 105560 | 20260313001191 | 402340 | 20260317000830 |
| 365550 | 20260814004149 | 042700 | 20260312001230 |

manifest가 출력할 수 있는 항목은 `stockCode`, `receiptNumber`, element tag, class/level 관련 attribute 이름과 값, ancestor의 tag/class signature, document-order ordinal, canonical heading key, `OVERVIEW/PRODUCTS/OTHER` 판정, 종목별 match count뿐이다. 본문, API key, 비대상 제목 원문은 출력하지 않는다.

P0 성공 조건은 다음 모두다.

1. 10개 pinned receipt가 모두 성공하고 II/III 대단원 selector와 하위 heading selector/level attribute가 관측된다.
2. 동일한 selector+level 규칙이 네이버 본문 교차 참조를 heading으로 세지 않고, SK스퀘어의 `2. 원스토어`/`2. FSK L&S` 및 KB금융의 `가. 사업의 내용(요약)`을 top-level boundary로 세지 않는다.
3. 각 종목의 overview/products/other top-level match 수와 selector signature를 이 subsection의 표로 추가하고, 그 실제 tag/attribute 형태를 T1~T5 fixture에 반영한 뒤 재검토한다.

현재 결과는 **미충족**이다. 2026-08-19 시도는 10개 모두 DART `HTTP 302 -> /error1.html`이었고 아무 artifact도 저장하지 않았다. 따라서 “다음 같은 수준”이라는 추측 규칙은 삭제하며, P0 표가 채워지기 전 application 구현은 금지한다. 런타임에 P0에서 승인된 selector와 일치하지 않는 문서는 semantic section을 추측하지 않고 4.5의 deterministic fallback으로 보낸다.

### 4.3 block/표 직렬화 규칙

heading boundary 사이 DOM을 다음 순수 renderer로 `BusinessSubsection.body` 또는 preamble로 만든다.

1. source heading element 자체는 출력하지 않는다. text node는 HTML entity decode 후 원래 DOM 순서로 한 번만 방문한다.
2. `<br>`은 LF 하나다. `<p>`, `<div>`, `<li>`, `<section>` 등 P0 fixture에서 관측된 block element의 닫힘 뒤에는 LF 하나를 둔다. nested block은 leaf text를 중복 출력하지 않는다.
3. `<table>`은 하나의 원자 block으로 취급한다. 각 `<tr>`을 DOM 순서로 한 번, 각 행의 nonblank `<th>/<td>`를 DOM 순서로 한 번 render한다. cell은 TAB(U+0009), row는 LF로 구분한다. 빈 cell은 생략하되 두 인접 nonblank 숫자 cell 사이 TAB은 반드시 남긴다. table 전후에는 LF 하나를 둔다.
4. 그 밖의 inline element는 자체 delimiter를 만들지 않고 자식 text를 순서대로 render하되, 두 인접 text run이 모두 nonblank이고 source에 공백이 없더라도 단어가 합쳐지지 않게 ASCII space 하나를 경계에 둔다.
5. canonicalization은 CRLF/CR→LF, NBSP/U+3000/기타 가로 공백→ASCII space, text 내부 연속 space→한 칸, TAB 주변 space 제거, 연속 TAB→하나, LF 주변 space/TAB 제거, 연속 LF→하나, 앞뒤 whitespace 제거, Unicode NFC 순서다. 구조 renderer가 만든 TAB/LF는 일반 가로 공백 collapse 대상에서 제외한다.
6. 결과에는 trailing LF/TAB/space가 없다. table cell 값은 정확히 한 번, 원래 순서로 남는다. NFKC는 heading match key에만 쓰고 body에는 적용하지 않는다.

### 4.4 허용할 제목 기준

제목 판정용 key와 출력 본문 정규화는 분리한다.

1. raw heading에 CR/LF 또는 Unicode control이 있으면 거부한다. 그 뒤 NFKC, NBSP/U+3000 포함 공백 제거, 전각 마침표→`.`로 만든 key만 match에 쓴다.
2. overview key는 `1.사업의개요`, `1.사업개요`, `1.(<prefix>)사업의개요`만 허용한다. products key는 `2.주요제품및서비스`, `2.주요제품과서비스`, `2.(<prefix>)주요제품및서비스`만 허용한다.
3. `<prefix>`는 NFKC/공백 제거 후 `\p{L}`, `\p{N}`, `/`, `&`, `·`, `-`만으로 된 1~30 **code point**다. 빈 값, 31자, 중첩 괄호, 점, control/개행은 거부한다.
4. 이 문자열 whitelist는 P0에서 승인된 selector와 top-level 값인 heading에만 적용한다. 같은 문자열이 body, table cell, nested/other-level heading에 있어도 semantic match가 아니다.
5. matching heading이 여러 개면 각 type 안에서 source order로 body를 `LF LF`로 연결한다. 중복 제거는 하지 않는다.

### 4.5 최종 출력 문법, 예산, 상태 계약

#### 타입과 byte 문법

- `classificationSections`의 Java 타입은 `List<ClassificationSection>`이고 enum 허용 값은 `OVERVIEW`, `PRODUCTS_AND_SERVICES`, `FALLBACK`뿐이다. structured 결과는 source 의미 순서에 따라 `[OVERVIEW, PRODUCTS_AND_SERVICES]`, `[OVERVIEW]`, `[PRODUCTS_AND_SERVICES]` 중 하나이며 fallback은 정확히 `[FALLBACK]`이다. `List.copyOf`로 불변 반환한다.
- `warnings`의 Java 타입은 `List<NormalizationWarning>`이고 4.5 상태표의 여덟 코드 외 값은 없다. `List.copyOf`로 불변 반환하며 Jackson JSON 값은 enum name 그대로다.
- structured 문자열의 ABNF 유사 문법은 `overview-block [ LF LF products-block ] / products-block`, `overview-block = "[OVERVIEW]" LF overview-content`, `products-block = "[PRODUCTS_AND_SERVICES]" LF products-content`다.
- fallback 문법은 `"[FALLBACK]" LF fallback-content`다. content는 nonblank이고 source heading을 포함하지 않는다. 빈 group header, 선행 LF, trailing LF는 없다. charset은 UTF-8이며 `classificationHash = lowercaseHex(SHA-256(UTF-8(classificationText)))`다.
- overview content는 인식된 overview가 있을 때 `nonblank preamble [LF LF]` 뒤에 nonblank overview body들을 `LF LF`로 이은 값이다. preamble도 overview 예산을 사용한다. products content도 nonblank products body들을 같은 방식으로 잇는다.

#### 충돌 없는 code-point 예산

- 단일 overview effective group: content cap 6,000; 출력 최댓값은 `[OVERVIEW]` 10 + LF 1 + content 6,000 = 6,011.
- 단일 products effective group: content cap 6,000; 출력 최댓값은 `[PRODUCTS_AND_SERVICES]` 23 + LF 1 + content 6,000 = 6,024.
- 두 group: 고정 overhead는 37 code point(`10 + 1 + 2 + 23 + 1`)다. products content cap 6,000을 먼저 예약하고 overview content cap을 `12,000 - 37 - 6,000 = 5,963`으로 정한다. products를 우선한 이유는 SK스퀘어에서 products가 overview 15,154자 뒤에 있어 전체 앞부분 절단으로 사라지는 관측 문제를 직접 막기 위해서다.
- fallback content cap은 6,000이고 출력 최댓값은 6,011이다. 마지막 전역 substring 단계는 **없다**. 따라서 header/delimiter가 잘리거나 알려지지 않은 추가 절단이 발생하지 않는다.
- cap은 canonicalization 후 Unicode code point 기준이다. cap 초과일 때 정확히 cap code point를 남기고 이후를 버린다. surrogate pair는 나누지 않는다. combining grapheme 전체 보존은 보장하지 않는 제한을 문서화하며, 동일 JDK/입력에서는 항상 같은 경계다.

#### exact 상태표와 warning 순서

effective group은 heading이 있고 `preamble+body`(overview) 또는 body(products)가 canonicalization 후 nonblank인 group이다. recognized heading은 있지만 content가 blank면 `*_EMPTY`, heading 자체가 없으면 `*_MISSING`이며 둘을 동시에 내지 않는다.

| 입력 상태 | 출력 sections | exact warnings 순서 | truncation |
|-----------|---------------|---------------------|------------|
| overview + products 유효 | `[OVERVIEW, PRODUCTS_AND_SERVICES]` | overview 초과 시 `OVERVIEW_TRUNCATED`, 이어 products 초과 시 `PRODUCTS_AND_SERVICES_TRUNCATED` | 5,963 / 6,000 |
| overview만 유효, products heading 없음 | `[OVERVIEW]` | `PRODUCTS_AND_SERVICES_MISSING`, 이후 필요 시 `OVERVIEW_TRUNCATED` | 6,000 |
| overview만 유효, products heading body blank | `[OVERVIEW]` | `PRODUCTS_AND_SERVICES_EMPTY`, 이후 필요 시 `OVERVIEW_TRUNCATED` | 6,000 |
| products만 유효, overview heading 없음 | `[PRODUCTS_AND_SERVICES]` | `OVERVIEW_MISSING`, 이후 필요 시 `PRODUCTS_AND_SERVICES_TRUNCATED` | 6,000 |
| products만 유효, overview heading+preamble/body blank | `[PRODUCTS_AND_SERVICES]` | `OVERVIEW_EMPTY`, 이후 필요 시 `PRODUCTS_AND_SERVICES_TRUNCATED` | 6,000 |
| 둘 다 heading 없음; II nonblank(여기에 preamble-only/미지원 selector 포함) | `[FALLBACK]` | `OVERVIEW_MISSING`, `PRODUCTS_AND_SERVICES_MISSING`, `STRUCTURE_FALLBACK`, 필요 시 `FALLBACK_TRUNCATED` | 6,000 |
| overview/products heading은 있으나 둘 다 effective하지 않음; II nonblank | `[FALLBACK]` | overview의 `MISSING/EMPTY`, products의 `MISSING/EMPTY`, `STRUCTURE_FALLBACK`, 필요 시 `FALLBACK_TRUNCATED` | 6,000 |
| II null 또는 canonicalization 후 blank | 결과 없음 | `IllegalArgumentException`; warning/hash/status 생성 안 함 | 해당 없음 |

전역 warning 순서는 항상 overview availability(`MISSING` 또는 `EMPTY`) → products availability(`MISSING` 또는 `EMPTY`) → `STRUCTURE_FALLBACK` → `OVERVIEW_TRUNCATED` → `PRODUCTS_AND_SERVICES_TRUNCATED` → `FALLBACK_TRUNCATED`이며 해당 없는 코드는 생략하고 중복하지 않는다. usable fallback/부분 structured 결과의 report `status`는 `SUCCESS`다.

### 4.6 거부할 정규화 기준과 근거

| 거부 기준 | 거부 이유 |
|-----------|-----------|
| `II. 사업의 내용` 전체를 그대로 사용 | KB금융 138,703자처럼 분류와 무관한 자금조달·운용 표가 입력을 지배하고 길이가 종목별로 80배 이상 벌어진다. |
| 전체 본문의 앞 N자만 일괄 자르기 | SK스퀘어의 제품·서비스 제목이 약 15,154자 뒤에 있어 제품/서비스 신호를 완전히 잃는다. |
| `사업`, `제품`, `서비스` 키워드가 있는 문장만 추출 | 키워드가 반복되는 업종을 과대대표하고 동의어 업종을 누락하며, 정답 taxonomy가 없는 상태에서 규칙 자체가 분류 편향을 만든다. |
| 본문 문자열 regex만으로 제목 경계 추정 | 네이버 결과의 앞선 교차 참조와 레인보우로보틱스의 뒤쪽 `[Ⅱ 사업의 내용,2. 주요 제품 및 서비스]참고`가 실제 제목으로 오인될 수 있다. |
| 모든 `2.` 절 또는 금융사의 `2. 영업의 현황`을 자동 포함 | 번호는 의미가 아니며 KB금융의 2절은 장대한 수치/영업 자료다. products 별칭으로 명시된 제목만 포함한다. 금융업 분류 단서는 1절 요약에 이미 은행·증권·보험·카드 사업으로 관측된다. |
| 숫자·표·가격·비율 일괄 제거 | 삼성전자·네이버 결과의 제품/서비스 구성과 매출 비중, 리츠의 자산 유형은 사업 정체성 신호가 될 수 있고 현재 DOM 평탄화 이후 안전한 표 의미 판별 근거가 없다. |
| 이미지 파일명처럼 보이는 문자열, 반복 문장 자동 삭제 | `*.jpg` 패턴이나 반복이 실제 제품명/모델명일 수 있어 false positive 기준이 없다. 필요하면 별도 corpus 평가 후 후속 계획으로 다룬다. |
| 번역, 형태소 분석, stemming, LLM 요약 | 외부 모델/사전 버전에 따라 결과가 변하고 네트워크·비용·prompt 의존성을 만들어 “동일 입력→동일 결과”를 깨뜨린다. |
| 출력 전체에 Unicode NFKC 적용 | 원문 기호, 단위, 번호 문자의 표현을 바꿀 수 있다. NFKC는 제목 매칭 key에만 쓰고 출력은 NFC만 사용한다. |

### 4.7 Green 단계

1. P0에서 문서화한 selector/level 규칙만 구현해 parser Red를 Green으로 만든다. 기존 `text`와 `hash` exact oracle이 동일한 회귀 assertion을 먼저 통과시킨다.
2. `BusinessReportNormalizer`에 순수 함수 `NormalizedBusinessText normalize(ParsedBusinessSection section)`을 구현한다. 네트워크, Spring context, 시간, locale, 파일시스템을 참조하지 않는다.
3. `NormalizedBusinessText`는 `text`, `hash`, `classificationSections`, `warnings`를 불변 값으로 반환한다. 목록은 호출자가 변경할 수 없게 복사한다.
4. `DartBusinessReportService` 생성자에 normalizer를 명시적으로 주입하고 report 필드를 채운다. live runner도 같은 production 경로만 사용하며 별도 정규화 구현을 두지 않는다.
5. focused 테스트를 통과시킨 뒤 `:collector:test` 전체를 실행한다. 실패가 기존 heavyweight integration 환경 문제이면 신규 focused 테스트 결과와 실패 원인을 분리해 보고하며 테스트를 비활성화하지 않는다.

### 4.8 호환성과 관측성

- 기존 `businessText`, `businessHash`, `status` 값은 유지한다. 기존에 항상 빈 `List<String>`이던 `warnings`는 닫힌 `List<NormalizationWarning>`으로 source contract가 바뀌고, `classificationText/hash/sections`가 추가된다. 현재 저장/공개 API 사용처가 없고 DART package/live runner만 함께 수정하므로 `common`/`api` 마이그레이션은 없다.
- summary에는 `rawTextCodePointCount`, `classificationTextCodePointCount`, `classificationHash`, `classificationSections`, `warnings`, `baselineReceiptNumber`, `actualReceiptNumber`, `receiptDrift`를 기록한다. API 키, 원문 ZIP, 전체 본문을 summary에 넣지 않는다.
- pinned baseline 검증은 receipt drift가 없는 경우만 corpus regression으로 판정한다. latest 탐색은 별도 mode이며 `actualReceiptNumber != baselineReceiptNumber`이면 `receiptDrift=true`로 보고하되 정규화 회귀 실패로 오판하지 않는다.

### 4.9 확정값과 후속 범위

- P0 selector/level 표 외 정책 결정을 implementer에게 넘기지 않는다. P0 미완료 상태에서는 이 문서가 승인될 수 없다.
- 12,000 code point 한도와 5,963/6,000 allocation은 이번 구현의 고정값이다. 향후 실제 classifier tokenizer/context budget 또는 정확도 corpus가 생기면 별도 계획으로 재평가한다.

## 5. 테스트 계획

### 5.1 테스트 전략

- **P0 prerequisite**: test writer 전에 pinned receipt의 in-memory manifest로 실제 selector/level을 관측하고 4.2 표와 fixture를 확정한다. 302/API 장애 또는 10개 미완료는 skip이 아니라 계획 승인 차단이다.
- **파서 단위 테스트**: 고정 baseline XML 및 P0 형태를 축약한 인메모리 ZIP으로 실제 selector/level, heading 제외 body, preamble, block/table serialization을 검증한다. Spring context나 DART 네트워크 없이 실행한다.
- **normalizer 단위 테스트**: 직접 만든 `ParsedBusinessSection`과 반복 문자열을 사용한다. 선택/누락/길이/공백/hash의 조합을 작은 독립 테스트로 나눠 실패 원인을 명확히 한다.
- **서비스 단위 테스트**: Mockito로 `DartClient`, parser, normalizer를 대체해 orchestration과 필드 전달만 검증한다. DART API 호출을 하지 않는다.
- **수동 live 회귀**: `DartBusinessReportSpikeRunner`는 `-Ddart.live.run=true`일 때 pinned receipt mode로 실행한다. optional latest mode는 receipt drift 관측용으로 분리한다. 외부 API 상태에 의존하므로 일반 `:collector:test` 합격 조건에는 넣지 않지만 P0 자체는 승인 prerequisite다.
- **모듈 회귀**: `./gradlew :collector:test`를 실행한다. 공통 모듈 계약이나 frontend를 변경하지 않으므로 이번 범위에서 `:api:test`/frontend build는 필수로 하지 않는다.

### 5.2 테스트 케이스 목록

| ID | 대상 클래스/메서드 | 시나리오 | 입력 (테스트 데이터) | 기대 결과 | 종류 |
|----|------------------|---------|--------------------|----------|------|
| T1 | `BusinessSectionParser.parse` | 변경 전 원문 호환성 | 고정 `business-section-baseline.xml`을 MS949 ZIP으로 변환 | `text`가 exact `Ⅱ. 사업의 내용 반도체와 로봇 부품을 제조합니다. 주요 제품은 감속기입니다.`, hash가 exact `d7df9ec55786572ef64ba148bb21725073ec45da2bb8d507a91ab32bfc2de532` | 단위 |
| T2 | `BusinessSectionParser.parse` | P0 selector/level과 nested 번호 | P0와 같은 tag/attribute로 top-level 1/2, nested `2. 원스토어`, `2. FSK L&S`, `가. 사업의 내용(요약)` | top-level semantic block만 분리; nested/other-level은 해당 body 안에 유지 | 단위 |
| T3 | `BusinessSectionParser.parse` | preamble, source heading 제외, 교차 참조 | II 뒤 preamble, 접두어 heading, body 안 quoted products 참조 | preamble 별도 보존, `body`에 source heading 없음, 참조는 boundary 아님 | 단위 |
| T4 | block/table renderer | 표와 block 직렬화 | `<p>`, `<br>`, nested inline, 2행×3열 table, whitespace가 다른 동치 fixture | exact LF/TAB 문자열, cell 값 원순서로 각 1회, 인접 숫자 사이 TAB, 두 fixture 결과 동일, trailing delimiter 없음 | 단위 |
| T5 | `BusinessSectionParser.parse` | 기존 오류/다중 ZIP 회귀 | II 없는 엔트리, 짧은/긴 성공 엔트리 | 기존 `ZIP 파싱 실패`; Java UTF-16 `text.length()`가 긴 기존 결과 선택 | 단위 |
| T6 | heading classifier | 허용 alias/prefix 경계 | 표준/공백/전각점/`과`, prefix 1자·30자와 `(제조서비스업)` | P0 top-level에서 exact enum match | 단위 |
| T7 | heading classifier | 거부 alias/prefix/위치 | prefix 0자·31자, 금지 문자/중첩괄호/CRLF/control, `주요제품`, `2. 영업의 현황`, body/nested 위치 | semantic match 없음 | 단위 |
| T8 | `normalize` | exact structured 문법 | overview `개요`, products `제품` | exact `[OVERVIEW]\n개요\n\n[PRODUCTS_AND_SERVICES]\n제품`, no trailing LF, sections exact 두 enum, warnings empty | 단위 |
| T9 | `normalize` | preamble/복수 block 합성 | preamble + overview 2개 + products 2개 | source headings 없이 각 content `\n\n` 연결, preamble이 overview 첫 content, enum 순서/defensive copy | 단위 |
| T10 | `normalize` | overview-only 상태 | products heading 없음 및 별도 blank products heading | exact overview 문법; 각각 exact `[PRODUCTS_AND_SERVICES_MISSING]`, `[PRODUCTS_AND_SERVICES_EMPTY]` | 단위 |
| T11 | `normalize` | products-only 상태 | overview heading 없음 및 별도 blank overview+preamble | exact products 문법; 각각 exact `[OVERVIEW_MISSING]`, `[OVERVIEW_EMPTY]` | 단위 |
| T12 | `normalize` | 금융/미지원 구조 fallback | semantic heading 없이 nonblank II, preamble-only, P0 미지원 selector | exact fallback 문법/`[FALLBACK]`; exact missing→missing→fallback warning 순서 | 단위 |
| T13 | `normalize` | 둘 다 6,000 경계 | canonical overview 6,000 + products 6,000 | overview 5,963으로 절단, products 6,000 유지, 총 exact 12,000, warnings exact `[OVERVIEW_TRUNCATED]` | 단위 |
| T14 | `normalize` | products도 초과 | overview 5,964 + products 6,001 | 각각 effective cap, 총 12,000, warnings exact overview→products truncation 순서 | 단위 |
| T15 | `normalize` | 단일 group/fallback 경계 | 각 6,000 및 6,001 code point인 overview-only, products-only, fallback | 6,000은 no truncation, 6,001은 해당 exact truncation code, header 포함 최대치 6,011/6,024/6,011 | 단위 |
| T16 | `normalize` | Unicode/whitespace normalization | CRLF/LF/CR, tabs, NBSP/U+3000, NFD/NFC, body compatibility char, cap 경계 emoji | 동치 text/hash, body NFKC 미적용, surrogate 무손상; grapheme 전체는 보장하지 않음 | 단위 |
| T17 | `normalize` | 빈 content 상태표 | 한쪽/양쪽 matching body blank, II nonblank; null/전체 blank II | exact `EMPTY/MISSING/FALLBACK` 목록; null/blank는 `IllegalArgumentException` | 단위 |
| T18 | `normalize` | 결정성/hash/불변성 | 같은 parsed object를 100회 normalize하고 반환 목록 mutation 시도 | 100회 byte/hash 동일, 독립 UTF-8 SHA-256 oracle 일치, enum 목록 mutation은 `UnsupportedOperationException` | 단위 |
| T19 | `DartBusinessReportService.collect` | 원문/분류 조립 | mock client/parser/normalizer | 두 text/hash와 enum sections/warnings exact 전달, report 목록 불변 | 단위 |
| T20 | live runner | pinned 10종목 재현 | 4.2 receipt map, ZIP 한 번 download/parse 후 같은 parsed object 100회 normalize | receipt drift 없이 10개 nonblank/deterministic; 단위 명시 summary; ZIP 미보존 | 수동 live |

### 5.3 테스트 데이터 준비

- T1 fixture `business-section-baseline.xml`은 구현 변경 전에 고정한다. 변경 전 production parser로 직접 확인한 exact oracle은 `Ⅱ. 사업의 내용 반도체와 로봇 부품을 제조합니다. 주요 제품은 감속기입니다.`와 `d7df9ec55786572ef64ba148bb21725073ec45da2bb8d507a91ab32bfc2de532`다. 새 구현 결과를 보고 fixture나 hash를 갱신하지 않는다.
- T2~T7 fixture는 P0 manifest에서 관측한 tag/class/level attribute의 최소 형태만 합성한다. 회사 본문은 복사하지 않고 `개요-시작`, `nested-유지`, `제품-도달` sentinel로 바꾼다. P0 표와 다른 임의 selector를 test writer가 발명하지 않는다.
- T4의 두 DOM은 행/cell의 source whitespace만 다르고 의미 cell 목록은 동일하게 수기 작성한다. exact oracle은 `첫 문단\n줄바꿈\nA\t100\t200\nB\t300\t400`처럼 테스트 상수로 고정하며 renderer 출력으로 기대값을 생성하지 않는다.
- T8~T19는 짧은 수기 exact 문자열과 `String.repeat` 장문을 쓴다. 장문 oracle은 production helper와 독립된 test-side `codePoints().count()` 및 start/end sentinel로 계산한다. hash는 JDK `MessageDigest`를 테스트에서 직접 사용하며 production hash method를 공유하지 않는다.
- whitespace fixture에는 CRLF/LF/CR, TAB, U+00A0, U+3000, NFD/NFC를 넣는다. compatibility character는 body에서 보존되는 exact assertion을 둔다. supplementary character는 surrogate pair 무손상만 보장하고 combining grapheme 경계 보존은 계약이 아님을 테스트명에 드러낸다.
- Git 비추적 `collector/build/dart-spike`는 관측 근거일 뿐 자동 oracle 입력이 아니다. baseline은 4.2의 receipt map, 10/10 성공, 평탄화 II 길이 1,723~138,703, 저장 원문 해시 10/10 일치로 고정한다.
- T20은 latest 조회를 하지 않고 pinned receipt ZIP을 각각 **한 번** 메모리로 받아 한 번 parse한 동일 object를 100회 normalize한다. 별도 latest mode를 실행할 경우 receipt 차이는 `receiptDrift`로만 기록한다. summary field는 `rawTextCodePointCount`, `classificationTextCodePointCount`처럼 단위를 이름에 포함한다.
- P0/T20 결과는 `collector/build/dart-spike/` summary 외에 원문을 쓰지 않는다. API key, ZIP, XML, 전체 본문은 로그·fixture·Git에 남기지 않는다. DART `020`/`800`/302는 성공으로 간주하지 않고 P0 또는 live 미완료로 정확히 보고한다.

## 6. 완료 기준 (Definition of Done)

- [ ] P0 pinned-receipt manifest 10/10이 성공했고 실제 selector/level, 종목별 match 수가 4.2에 기록되어 plan-reviewer 재승인을 받았다. 실패 상태에서는 구현을 시작하지 않았다.
- [ ] `tdd_test_writer`가 T1~T20을 `src/test`에만 먼저 추가해 compile Red를 기록했고, `tdd_implementer`가 최소 API skeleton 후 모든 case 묶음의 assertion Red를 기록했다.
- [ ] parser→normalizer→service 순 Green 후 T1~T20과 기존 DART focused 테스트가 모두 통과했다.
- [ ] `./gradlew :collector:test --tests "com.service.RSIranking.dart.*"`가 통과한다.
- [ ] `./gradlew :collector:test` 전체가 통과하고 기존 테스트를 삭제·비활성화·완화하지 않았다. 환경 의존 실패가 있으면 정확한 테스트명과 원인을 보고한다.
- [ ] T1 exact 원문/hash oracle이 그대로이며 새 코드 결과로 oracle을 갱신하지 않았다.
- [ ] exact 출력 grammar, no trailing LF, enum section/warning 순서, defensive copy가 T8~T19로 고정되었다.
- [ ] 두 group일 때 overview 5,963/products 6,000 + overhead 37 = 최대 12,000이고, 단일/fallback 및 6,000/6,001 경계의 exact warning이 통과한다. 최종 전역 substring은 없다.
- [ ] 표의 nonblank cell이 DOM 순서로 한 번씩 남고 TAB/LF serialization과 whitespace 동치 fixture가 통과한다.
- [ ] 동일 parsed object 100회 결과가 byte/hash 동일하고 독립 SHA-256 oracle과 일치하며 supplementary character가 손상되지 않는다.
- [ ] products-only, overview-only, blank matching body, preamble-only, unsupported selector, fallback >6,000, null/blank II의 exact 상태표가 모두 자동 테스트로 고정되었다.
- [ ] T20 실행 시 pinned receipt 10개를 한 번씩만 받아 동일 parsed object로 100회 결정성을 확인했다. latest receipt drift는 별도 필드로 보고했고 정규화 회귀로 오판하지 않았다.
- [ ] 원문 ZIP/API 키/전체 회사 본문을 Git에 추가하지 않았고 summary에도 비밀정보가 없다.
- [ ] 3.1에 명시한 파일 외에는 수정하지 않았으며 `common`, `api`, frontend, DB schema에는 변경이 없다.

## 7. 구현 난이도 평가

- 규모: 중
- 권장 구현 모델: `gpt-5.6-terra`, reasoning effort `medium` (`.codex/agents/tdd-implementer.toml` 강제값)
- 계획/재검토 모델: `gpt-5.6-sol`, reasoning effort `high` (`tdd-planner.toml`, `tdd-plan-reviewer.toml`)
- 근거: P0 DOM selector/level 조사와 모든 정책 결정은 승인 전 `sol/high` 계획 단계에서 끝낸다. 그 뒤 구현은 확정된 grammar/enum/예산/fixture를 옮기는 중간 규모 deterministic Java 작업이므로 repository가 강제하는 `terra/medium` implementer가 적합하다. verifier도 설정상 `terra/medium` read-only다.

## 8. 검토 의견 (plan-reviewer 작성)

### 2차 판정

**P0 수행에 한해 조건부 승인한다. application Red/Green 구현은 아직 승인하지 않는다.** 이전 검토의 계약·테스트·실행 순서 문제는 수정됐다. subsection의 heading/body 구분, exact 출력 문법과 enum, 5,963/6,000 배분으로 계산되는 12,000 code-point 상한, 표 renderer, 누락·빈 값·fallback·truncation 상태표, 고정 원문/hash oracle, pinned receipt 재현성, compile Red→skeleton→assertion Red handoff, repository agent의 모델/effort가 서로 일관되고 T1~T20 및 DoD로 검증 가능하다. T1의 고정 문자열 SHA-256도 계획값과 일치한다.

남은 불확실성은 실제 DART DOM의 heading selector/level 하나로 격리됐고, 이를 추측하지 않도록 P0가 승인 전 차단 조건으로 올바르게 설계됐다. 따라서 별도의 계획 재작성은 요구하지 않으며 다음 조건을 모두 충족한 뒤 최종 재검토를 받아야 한다.

1. pinned receipt 10개 모두에서 비영속 manifest가 성공해야 한다. `302`, 일부 receipt 실패, selector/level 미관측은 성공 또는 skip으로 간주하지 않는다.
2. 4.2에 실제 selector/level signature와 종목별 `OVERVIEW/PRODUCTS/OTHER` top-level match 수를 기록하고, 네이버 교차 참조·SK스퀘어 nested `2.*`·KB금융 nested `가.*`가 top-level 경계가 아님을 관측값으로 확인해야 한다.
3. 관측한 최소 tag/attribute/level 형태와 block element 집합을 T2~T7 및 T4 fixture 계약에 반영해야 한다. 관측 결과가 현재 parser 구조·renderer 계약을 바꾸면 그 변경도 구현 전에 재검토한다.
4. P0 표가 채워지고 plan-reviewer가 최종 승인하기 전에는 `status: draft`를 유지하고 `tdd_test_writer` 및 `tdd_implementer`를 시작하지 않는다. P0가 계속 실패하면 본 계획은 blocked 상태이며 추정 selector나 평탄화 문자열 regex로 우회하지 않는다.

권장 사항은 P0 실행 로그에 응답 본문·ZIP·XML·API key를 남기지 않고, 실패 시 HTTP 상태·receipt·시각만 기록하는 것이다. 이 조건 외 추가 필수 수정은 없다.
