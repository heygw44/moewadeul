# 전체 API·쿼리 측정 도구

장부(`docs/portfolio/tools/ledger.tsv`)의 `API:` 행과 `QUERY:` 행을 모두 재는 도구다. 손으로 API를 고르지 않는다. 장부에 있는데 `apis.py`에 부르는 법이 없으면 `sweep.py plan`이 멈춘다.

## 준비

1. 결과 폴더를 만든다. 기본은 gitignore된 `backend/load-test/results/sweep`이고 `SWEEP_DIR`로 바꿀 수 있다. `./init.sh`가 측정 전용 가짜 비밀값, Caddy 설정, 공공데이터 고정 파일을 채운다.
2. 앱 이미지를 만든다. `./gradlew bootJar` 뒤 jar 안에 바꾼 클래스와 매퍼가 있는지 보고, `docker build --label git.sha=<SHA> -t moewadeul-lt-app:sweep-<SHA> .`로 만든다. `APP_TAG=sweep-<SHA>`로 고른다.
3. 데이터 볼륨 `moewadeul-lt-sweep_mysql-data`를 채운다. 다른 측정이 쓰는 볼륨을 직접 쓰지 말고 복사본을 쓴다. `../seed/seed.sh`로 `base,extra,sweep` 세 단계를 넣고(`SEED_STAGES`, 규모와 이유는 `../README.md` 1절), `./snapshot.sh save`로 얼린다.
4. 장부 경로가 저장소 밖이면 `LEDGER=<경로>/ledger.tsv`를 준다. 시드 회원의 검증용 비밀번호(`../seed/seed.sql` 머리말)를 `LOADTEST_PASSWORD`로 준다.

## 돌리기

`./all.sh`가 아래를 차례로 한다. 단계마다 `./pipeline.sh prepare|map|measure`로도 돌릴 수 있다.

| 단계 | 하는 일 |
|---|---|
| prepare | 얼린 데이터로 되돌리고, 행사·신청 같은 고정값을 넣고, API마다 요청 묶음을 만들고 회원을 로그인시킨 뒤 `moewadeul-lt-sweep-planned`로 다시 얼린다 |
| map | SQL 디버그 로그를 켜고 API마다 한 번씩 부른다. 응답의 추적 ID로 API별 쿼리를, 로그로 쿼리 id별 실제 SQL과 값을 모은다(`maplog.py`). 스케줄 작업을 2분마다로 당겨 함께 돌린다. 이 회차의 시간은 쓰지 않는다 |
| measure | 다시 되돌려 로그 없이 데우고, 다이제스트를 비운 뒤 혼자 재기(읽기 60건, 쓰기 30건)와 50 VU 재기를 하고, 스케줄 작업을 돌린다. 그다음 238개 쿼리를 모두 직접 EXPLAIN하고(`explain.py`, 돌지 않은 쿼리는 `render.py`가 XML을 펼친다) 표를 만든다(`summary.py`) |

결과는 `SWEEP_DIR/SUMMARY.md`의 표, `out/api.tsv`, `out/query.tsv`, `out/findings.tsv`에 남는다. `ledger_out.py`가 장부 조각을 만든다.

## 전후 비교

| 도구 | 하는 일 |
|---|---|
| `ab.py <사례> [회차]` | 같은 DB에서 인덱스를 넣고 빼며 옛 SQL과 새 SQL을 번갈아 잰다(서버 시간과 읽은 행은 performance_schema). 쓰기 문장은 트랜잭션 안에서 되돌린다 |
| `lockprobe.sh <문장> <쓰기>` | 문장을 트랜잭션 안에서 쥔 채 행 잠금 수와, 다른 연결의 쓰기가 기다린 시간을 잰다 |
| `writecost.py [회차] [표…]` | 새 인덱스마다 같은 쓰기 2,000행을 번갈아 재 쓰기 비용을 본다. 인덱스를 만든 직후 첫 쓰기는 버린다 |
| `api_ab.sh <API 슬러그> <만들 DDL> <지울 DDL> [회차]` | 같은 앱 이미지에서 인덱스만 바꿔 API 혼자 재기와 50 VU를 번갈아 재고 앱·MySQL CPU를 적는다 |

## 지켜야 할 것

- 앱·MySQL·Caddy는 CPU 0,1, k6와 스텁은 CPU 4~7에 둔다. 메모리 한도는 운영 compose 그대로다. 디스크 제한은 걸지 않는다(`LT_EXTRA`로 더할 수 있다).
- 같은 맥에서 다른 측정이 돌면 응답 시간은 찾기용이다. 실행계획과 읽은 행은 그대로 쓴다.
- 외부 API와 메일은 `stub/`의 WireMock과 `sink.py`로 막는다. 운영 주소로 요청을 보내지 않는다.
