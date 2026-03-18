# Web Crawler - 학습용 프로젝트

웹 크롤러의 핵심 개념을 학습하기 위한 Kotlin 기반 프로젝트입니다.
단일 프로세스로 동작하며, 실제 서비스 운영에 필요한 핵심 요소(politeness, robots.txt, rate limiting, retry 등)를 모두 포함합니다.

## 핵심 기능

- **URL Frontier**: BFS/DFS 선택 가능한 방문 대기열
- **URL 정규화**: scheme/host 소문자화, 기본 포트 제거, fragment 제거, trailing slash 처리
- **중복 방문 방지**: 정규화된 URL 기반 deduplication
- **robots.txt 준수**: User-Agent 기반 규칙 파싱, 캐싱, fallback 정책
- **Politeness**: per-host rate limiting으로 대상 서버 보호
- **HTTP Fetch**: redirect 추적, content-type 검사, timeout 처리
- **Retry/Backoff**: 5xx/네트워크 오류에 대한 지수 백오프 재시도
- **HTML 파싱**: 링크 추출, title 추출, canonical URL 처리, base tag 지원
- **결과 저장**: JSONL 파일 형식
- **CLI 인터페이스**: 모든 주요 설정을 CLI 인자로 제어

## 시스템 아키텍처

```
┌──────────────┐
│  Seed URLs   │
└──────┬───────┘
       ▼
┌──────────────┐    ┌─────────────────┐
│   Frontier   │◄───│  URL Normalizer │
│  (BFS Queue) │    │  + Dedup Check  │
└──────┬───────┘    └─────────────────┘
       ▼
┌──────────────┐    ┌─────────────────┐
│ Robots Check │───►│  RobotsPolicy   │
│              │    │  (cached/host)  │
└──────┬───────┘    └─────────────────┘
       ▼
┌──────────────┐
│ Rate Limiter │  per-host delay
└──────┬───────┘
       ▼
┌──────────────┐
│   Fetcher    │  HTTP GET + redirect tracking
│  (w/ retry)  │  + exponential backoff
└──────┬───────┘
       ▼
┌──────────────┐
│Content-Type  │  HTML만 파싱 대상
│   Check      │
└──────┬───────┘
       ▼
┌──────────────┐    ┌─────────────────┐
│  HTML Parser │───►│  Link Extractor │
│  (jsoup)     │    │  + Normalizer   │
└──────┬───────┘    └────────┬────────┘
       │                     │
       ▼                     ▼
┌──────────────┐    ┌─────────────────┐
│   Storage    │    │ Enqueue new URLs│
│  (JSONL)     │    │ to Frontier     │
└──────────────┘    └─────────────────┘
```

### 데이터 흐름

1. Seed URL을 정규화 후 Frontier에 추가
2. Frontier에서 URL을 꺼냄 (BFS: FIFO, DFS: LIFO)
3. robots.txt 규칙 확인 (캐시 사용, 호스트별)
4. Per-host rate limiting 적용
5. HTTP GET 수행 (redirect 추적, retry with backoff)
6. Content-Type 확인 (HTML만 파싱)
7. jsoup으로 HTML 파싱 → title, 링크, canonical URL 추출
8. 추출된 링크를 정규화 → 중복 체크 → Frontier에 추가
9. 크롤링 결과를 JSONL로 저장
10. max pages 또는 Frontier 소진까지 반복

### 주요 설계 결정

| 결정 | 이유 |
|------|------|
| BFS 기본 전략 | 사이트를 레벨별로 탐색하여 넓은 커버리지 확보. 깊은 링크 체인에 갇히는 것 방지 |
| In-memory Frontier | 학습용 단일 프로세스에 적합. Redis로 교체 가능한 구조 |
| Per-host rate limiting | 동일 호스트에 연속 요청 간 최소 지연 보장. 서버 부하 방지 |
| Manual redirect tracking | redirect chain을 기록하여 디버깅과 학습에 활용 |
| JSONL 저장 | 스트리밍 쓰기에 적합, jq 등으로 후처리 용이 |
| robots.txt 캐싱 | 동일 호스트의 robots.txt를 반복 요청하지 않음 |

## 프로젝트 구조

```
web-crawler/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
├── src/
│   ├── main/
│   │   ├── kotlin/com/crawler/
│   │   │   ├── Main.kt                    # CLI 진입점
│   │   │   ├── config/
│   │   │   │   └── CrawlConfig.kt         # 중앙 설정
│   │   │   ├── model/
│   │   │   │   └── Models.kt              # 도메인 모델
│   │   │   ├── crawler/
│   │   │   │   └── WebCrawler.kt          # 메인 오케스트레이터
│   │   │   ├── frontier/
│   │   │   │   └── CrawlFrontier.kt       # URL 대기열 + 방문 추적
│   │   │   ├── url/
│   │   │   │   └── UrlNormalizer.kt        # URL 정규화
│   │   │   ├── robots/
│   │   │   │   └── RobotsPolicy.kt         # robots.txt 파싱/판정
│   │   │   ├── fetcher/
│   │   │   │   └── HttpFetcher.kt          # HTTP 클라이언트 + retry
│   │   │   ├── parser/
│   │   │   │   └── HtmlParser.kt           # HTML 파싱 + 링크 추출
│   │   │   ├── ratelimit/
│   │   │   │   └── RateLimiter.kt          # Per-host 요청 지연
│   │   │   └── storage/
│   │   │       ├── CrawlStorage.kt         # 저장소 인터페이스
│   │   │       └── JsonlStorage.kt         # JSONL 파일 저장
│   │   └── resources/
│   │       └── logback.xml
│   └── test/
│       ├── kotlin/com/crawler/
│       │   ├── url/UrlNormalizerTest.kt
│       │   ├── robots/RobotsPolicyTest.kt
│       │   ├── parser/HtmlParserTest.kt
│       │   ├── frontier/CrawlFrontierTest.kt
│       │   ├── fetcher/HttpFetcherTest.kt
│       │   ├── ratelimit/RateLimiterTest.kt
│       │   ├── storage/JsonlStorageTest.kt
│       │   └── crawler/WebCrawlerIntegrationTest.kt
│       └── resources/
│           └── logback-test.xml
└── crawl-output/                           # 크롤링 결과 (gitignored)
```

## 실행 방법

### 요구사항

- JDK 21+

### 빌드

```bash
./gradlew build
```

### 실행

```bash
# 기본 실행 (소규모 크롤링)
./gradlew run --args="https://example.com --max-pages 10 --max-depth 2"

# 옵션 설명
./gradlew run --args="--help"

# 여러 seed URL
./gradlew run --args="https://example.com https://example.org --max-pages 20"

# DFS 전략, 긴 지연
./gradlew run --args="https://example.com --dfs --delay 2000 --max-depth 1"

# 출력 디렉터리 지정
./gradlew run --args="https://example.com -o ./my-output --max-pages 5"
```

### CLI 옵션

| 옵션 | 기본값 | 설명 |
|------|--------|------|
| `--max-pages` | 50 | 최대 크롤링 페이지 수 |
| `--max-depth` | 3 | 최대 크롤링 깊이 |
| `--delay` | 1000 | 호스트별 요청 간 지연 (ms) |
| `--output`, `-o` | crawl-output | 결과 저장 디렉터리 |
| `--user-agent` | LearningCrawler/1.0 | User-Agent 헤더 |
| `--no-robots` | false | robots.txt 무시 |
| `--same-domain` | true | 동일 도메인만 크롤링 |
| `--dfs` | false | DFS 전략 사용 |

### 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 특정 테스트 클래스
./gradlew test --tests "com.crawler.url.UrlNormalizerTest"

# 통합 테스트만
./gradlew test --tests "com.crawler.crawler.WebCrawlerIntegrationTest"
```

## 저장 결과 예시

JSONL 파일의 각 라인:

```json
{
  "requestUrl": "https://example.com/",
  "finalUrl": "https://example.com/",
  "statusCode": 200,
  "title": "Example Domain",
  "linkCount": 1,
  "extractedLinks": ["https://www.iana.org/domains/example"],
  "depth": 0,
  "crawledAt": "2024-01-15T10:30:00Z",
  "error": null,
  "responseTimeMs": 245
}
```

결과 확인:
```bash
# 전체 결과 보기
cat crawl-output/crawl-*.jsonl | jq .

# title만 추출
cat crawl-output/crawl-*.jsonl | jq -r .title

# 에러가 있는 결과만
cat crawl-output/crawl-*.jsonl | jq 'select(.error != null)'
```

## URL 정규화 정책

| 항목 | 정책 | 설정 |
|------|------|------|
| Fragment (#) | 기본 제거 | `stripFragment = true` |
| Trailing slash | 기본 제거 (root "/" 유지) | `stripTrailingSlash = true` |
| Query string | 유지 | - |
| Scheme | 소문자화, 기본 유지 (http↔https 변환 안 함) | `normalizeHttps = false` |
| Host | 소문자화 | - |
| Default port | 제거 (80, 443) | - |
| Relative URL | base URL 기준 절대경로 변환 | - |

## robots.txt 처리

- **목적**: robots.txt는 크롤러에 대한 접근 허용/비허용 **권고**입니다. 인증이나 인가 수단이 아닙니다.
- **파싱**: User-Agent 기반 Allow/Disallow 규칙, Crawl-delay, Sitemap 디렉티브 지원
- **매칭**: 가장 구체적인(긴) 경로 매칭이 우선
- **캐싱**: 호스트별 캐시 (기본 30분 TTL)
- **Fallback**: robots.txt fetch 실패 시 기본 허용 (설정 변경 가능)
- **404**: robots.txt가 없으면 (404) 모든 경로 허용

## Redirect 처리 전략

- 수동 redirect 추적 (Java HttpClient의 자동 redirect 비활성화)
- 301, 302, 303, 307, 308 모두 처리
- Redirect chain을 기록하여 결과에 포함
- 최대 redirect 횟수 제한 (기본 5회)
- Relative Location 헤더도 base URL 기준으로 해석

## Canonical URL 전략

- `<link rel="canonical">` 태그가 있으면 해당 URL을 권위 URL로 사용
- Canonical URL이 이미 방문된 URL이면 중복으로 처리
- Canonical URL이 다른 도메인이면 same-domain 정책에 따라 처리

## 안전장치 (기본값)

| 항목 | 기본값 |
|------|--------|
| 최대 페이지 수 | 50 |
| 최대 깊이 | 3 |
| 호스트별 요청 지연 | 1000ms |
| 요청 타임아웃 | 10초 |
| 연결 타임아웃 | 5초 |
| 최대 재시도 | 3회 |
| 최대 redirect | 5회 |

## 한계점

### 현재 버전에서 제외된 범위

- **JavaScript 렌더링**: SPA 등 JS로 동적 생성되는 콘텐츠는 크롤링 불가
- **대규모 분산 크롤링**: 단일 프로세스 기반, 멀티노드 미지원
- **Anti-bot 우회**: CAPTCHA, IP 차단 등 우회 기능 없음
- **인증 기반 페이지**: 로그인 필요 페이지 미지원
- **Content fingerprint**: 콘텐츠 기반 near-duplicate 탐지 미구현
- **Sitemap.xml 활용**: 구조는 고려되었으나 적극 활용 미구현
- **멀티스레드 크롤링**: 현재 단일 스레드 순차 처리
- **Persistent state**: 크롤링 중단 후 재개 미지원

## 확장 방향

### 1. Redis 기반 Frontier
`CrawlFrontier`를 Redis List/Sorted Set으로 교체하여 분산 worker 간 큐 공유.

### 2. PostgreSQL 기반 URL State
`CrawlStorage` 인터페이스를 PostgreSQL로 구현. 크롤링 상태 영속화, 재개 지원.

### 3. Kafka 기반 Fetch/Parse 분리
URL fetch와 HTML parse를 별도 consumer group으로 분리하여 독립 스케일링.

### 4. Distributed Crawler
```
[Scheduler] → [Redis Queue] → [Fetcher Workers × N] → [Kafka] → [Parser Workers × N] → [PostgreSQL]
```

### 5. JavaScript 렌더링
Playwright/Selenium 기반 headless browser를 Fetcher 대안으로 추가.

### 6. Sitemap.xml 적극 활용
크롤링 시작 시 sitemap.xml을 파싱하여 중요 URL을 우선 enqueue.

### 7. Content Fingerprint
SimHash 등으로 페이지 콘텐츠 fingerprint를 생성하여 near-duplicate 탐지.

### 8. Observability 강화
OpenTelemetry로 trace/metrics 수집, Grafana/Prometheus로 대시보드 구성.

## 학습 포인트

이 프로젝트를 통해 학습할 수 있는 웹 크롤러 핵심 개념:

1. **URL Frontier와 BFS/DFS**: 웹 그래프 탐색 전략
2. **URL 정규화**: 같은 리소스를 가리키는 다양한 URL 형태 통일
3. **robots.txt 프로토콜**: 크롤러 윤리와 표준 준수
4. **Politeness 정책**: 대상 서버에 부담을 주지 않는 요청 패턴
5. **HTTP 리다이렉트 체인**: 301/302 등 리다이렉트의 의미와 처리
6. **Retry와 Exponential Backoff**: 일시적 오류의 안정적 복구
7. **Content-Type 기반 필터링**: 크롤링 대상 선별
8. **Canonical URL**: 검색엔진이 중복 페이지를 처리하는 방식
9. **HTML 파싱과 링크 추출**: 구조화된 웹 문서에서 정보 추출
10. **저장소 추상화**: 인터페이스 분리로 저장소 교체 용이성 확보
