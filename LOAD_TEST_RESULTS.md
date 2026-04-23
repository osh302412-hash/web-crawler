# Web Crawler BFS vs DFS Benchmark Results

## Environment
- Kotlin JVM crawler (single-threaded, politeness-controlled)
- Seed: https://en.wikipedia.org/wiki/Web_crawler
- max-pages=30, max-depth=3, delay=50ms, same-domain=true
- robots.txt 준수

## Results

| Strategy | Pages | Elapsed | pages/sec | Avg Response |
|----------|-------|---------|-----------|--------------|
| BFS      | 30    | 6.33s   | **4.73**  | 381ms        |
| DFS      | 30    | 17.43s  | 1.72      | 609ms        |

**BFS가 DFS보다 2.75배 빠름**

## Analysis

- **BFS**: 넓이 우선으로 depth=1 페이지를 먼저 처리. Wikipedia robots.txt가 `/wiki/Special:*`를 block하므로 663건 중복/차단 skip → 실제 fetch 적음(13 success). 빠른 처리속도
- **DFS**: 깊이 우선으로 depth=3까지 진입. robots.txt 차단이 4건뿐, 실제 26건 fetch → 응답 대기 시간이 누적되어 avg 609ms, 전체 17초 소요
- **결론**: robots.txt 패턴이 많은 사이트에서는 BFS가 효율적. 콘텐츠 수집 목적에서는 DFS가 더 많은 실질 페이지 수집

## Key Features Implemented
- BFS/DFS 전략 선택 가능
- robots.txt 완전 준수 (432 rules parsed)
- per-host delay (politeness control)
- 중복 URL 필터링 (663 중복 감지)
- JSONL 형식 결과 저장
- 재시도 로직
