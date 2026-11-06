# 배포 측정 (`deploy/`)

`deploy/deploy.sh`를 로컬 Docker에서 그대로 돌려 배포 중 끊김, 되돌리기, 긴 마이그레이션, 실패한 마이그레이션 복구 절차(docs/10 12.2.1)를 잰다. 운영 서버에서는 돌리지 않는다.

배포 스크립트는 고치지 않고 `mkdeploy.sh`가 경로, 포트, 이미지 저장소, 잠금처럼 서버에만 있는 부분만 바꿔 `opt/deploy.lt.sh`로 쓴다. 바꾼 곳은 `opt/deploy.lt.diff`에 남고, 개수가 14개가 아니면 멈춘다. `deploy.sh`가 바뀌면 sed 한 줄이 조용히 빠져 운영 값(예: 8081 상태 확인)이 남을 수 있어서다.

## 준비

결과와 측정 틀(`opt/`)은 `backend/load-test/results/deploy`에 생긴다. 다른 곳에 두려면 `DEPLOY_LT_DIR`을 준다. `opt/`에는 가짜 비밀값과 MySQL root 비밀번호가 있어서, 이 폴더를 지우면 같은 compose 프로젝트의 MySQL 볼륨에 다시 들어갈 수 없다. 그때는 `DEPLOY_LT_PROJECT`로 다른 프로젝트 이름을 쓴다.

```bash
cd backend && ./gradlew bootJar && docker build -t moewadeul-lt-app:dev-$(git rev-parse --short=8 HEAD) .
docker tag <관리 포트 8081을 넣기 전 이미지> moewadeul-lt-app:o1   # 되돌리기 시험에만 쓴다. 없으면 그 회차를 건너뛴다
load-test/deploy/setup.sh moewadeul-lt-app:dev-<sha>
load-test/deploy/init-db.sh
```

`setup.sh`가 붙이는 태그는 이렇다.

| 태그 | 내용 |
|---|---|
| `n1`, `n2` | 받은 이미지 그대로. 평소 배포 |
| `k1` | 시작 20초 뒤 끝나는 버전. 시작 막바지에 죽는 앱을 흉내 낸다. SIGTERM에는 바로 끝난다 |
| `m1` | `sql/long`의 V999(알림 표 인덱스 두 개) |
| `f1` | `sql/fail`의 V999(인덱스를 만든 뒤 표를 만든다. `flyway-fail.sh`가 같은 이름 표를 미리 만들어 둘째 문장을 실패시킨다) |
| `o1` | 직접 붙인 관리 포트 전 버전 |

## 실행

```bash
caffeinate -i load-test/deploy/chain.sh            # 묶음 전체, 측정 잠금을 잡고 돈다
ROWS=9000000 MIGNAME=miglong load-test/deploy/migration.sh 3   # 마이그레이션이 Caddy 대기 30초를 넘는 경우
```

회차마다 `runs/<이름>/summary.txt`에 요청 수와 실패 수, 실패 구간, 컨테이너 이벤트, `deploy.sh` 단계 시각, 지연 p95, 댓글 중복, Caddy 502 수, MySQL 상태가 남는다. 앱과 MySQL, Caddy는 CPU 4개(cpuset 4-7)를 함께 쓴다.

## 함정

- 측정 MySQL이 unhealthy면 `deploy.sh`가 `--wait`에서 실패하고 모든 배포가 되돌리기로 끝난다. 회차마다 `mysql_health_before`와 `mysql_health_after`를 확인한다.
- 셸이 PID 1인 가짜 이미지는 SIGTERM을 무시한다. 그러면 되돌리기에서 `docker stop`이 셸 명령이 끝날 때까지 기다려 되돌리기 시간이 늘어난다. `k1`은 trap으로 바로 끝낸다.
- `deploy.sh`는 bash 4 이상이 필요하다. macOS 기본 bash는 3.2라 `BASH5`(기본 `/opt/homebrew/bin/bash`)로 실행한다.
