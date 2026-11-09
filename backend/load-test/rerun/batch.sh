#!/usr/bin/env bash
# 사용: withlock.sh ./batch.sh <APP_TAG> [사례 번호...]. 사례 1~5 표제 수치를 다시 잰다. 번호를 주면 그 사례만 돈다.
# 결과가 있는 실행은 건너뛰므로 끊긴 뒤 같은 명령으로 이어서 돈다. 회차의 순서(순차·번갈아)는 각 줄 주석에 적었다.
set -euo pipefail
cd "$(dirname "$0")"
TAG=${1:?APP_TAG}; shift
CASES=${*:-1 2 3 4 5}
has() { [[ " $CASES " == *" $1 "* ]]; }

if has 1; then
  # 인덱스 없음과 있음을 묶음마다 번갈아 2묶음, 정리 작업은 시각 모양 둘을 번갈아 2번씩.
  [ -f "$(python3 -c 'import lt;print(lt.RESULTS)')/runs/case1/result.jsonl" ] || python3 case1.py "$TAG" 2
fi

if has 2; then
  # 표제 조건(풀 10, 신고 20, 60초 잠금, 디스크 제한) 순차 3회.
  for n in 1 2 3; do python3 case2.py "$TAG" c2-head-$n 10 20 60 1; done
  # 풀 표(Tomcat 70, 디스크 제한 없음, 30초 잠금). 칸 순서를 회차마다 하나씩 밀어 번갈아 3회.
  CELLS=("10 10" "10 20" "20 10" "20 20" "20 40")
  for n in 1 2 3; do
    for k in 0 1 2 3 4; do
      c=(${CELLS[$(( (k + n - 1) % 5 ))]})
      python3 case2.py "$TAG" "c2-p${c[0]}-r${c[1]}-$n" "${c[0]}" "${c[1]}" 30 0
    done
  done
fi

if has 3; then
  # 섞인 부하. 고유 인덱스 계획(i0)과 status 계획(i5000)을 번갈아 3회. 각 계획의 첫 회(w0)는 데우기로 버린다.
  python3 case3.py "$TAG" c3-i0-w0 0
  python3 case3.py "$TAG" c3-i5000-w0 5000
  for n in 1 2 3; do python3 case3.py "$TAG" c3-i0-$n 0; python3 case3.py "$TAG" c3-i5000-$n 5000; done
  # 행사 오픈 2,000건. 묶음마다 오픈만 재시작 직후 1회와 이어서 2회(순차), 지도 300 VU와 함께 풀 10과 풀 30을 재시작해 1회씩(번갈아). 3묶음.
  for n in 1 2 3; do
    python3 run.py "$TAG" open-solo-cold-$n k6_MAP_VUS=0
    python3 run.py "$TAG" open-solo-warm-$n-a k6_MAP_VUS=0 restart=0 warm=0
    python3 run.py "$TAG" open-solo-warm-$n-b k6_MAP_VUS=0 restart=0 warm=0
    python3 run.py "$TAG" openmap-p10-$n pool=10
    python3 run.py "$TAG" openmap-p30-$n pool=30
  done
  # 경합 라운드(Testcontainers). 스택과 같은 CPU를 쓰므로 부하 측정과 겹치지 않게 여기서 돈다.
  OUT="$(python3 -c 'import lt;print(lt.RESULTS)')/runs/rounds"
  if [ ! -f "$OUT/done" ]; then
    rm -rf ../../build/measure
    (cd ../.. && ./gradlew measureRounds -Drounds=1000 >"/tmp/rerun-rounds.log" 2>&1)
    mkdir -p "$OUT" && cp ../../build/measure/*.json "$OUT/" && touch "$OUT/done"
  fi
fi

if has 4; then
  STUB3=3000,50,50,300,30; SLOW=1700,1700,1700,7000,1700
  for n in 1 2 3; do
    # 기상청 단기 3초, 날씨 초당 16건, 지도 50 VU 3분. Tomcat 50과 70을 번갈아.
    for t in 50 70; do python3 run.py "$TAG" c4-s1-3s-t$t-$n script=mix tomcat=$t stub=$STUB3 k6_VUS=50 k6_DURATION=3m k6_WEATHER_RATE=16; done
    # 넷 다 느림(기상청·천문연·S3 HEAD 1.7초, OpenAI 7초), 날씨 초당 50건+찬 격자 8건, AI 3건, 첨부 12 VU. Tomcat 50과 70을 번갈아.
    for t in 70 50; do python3 run.py "$TAG" c4-all-t$t-$n script=mix tomcat=$t stub=$SLOW k6_VUS=50 k6_DURATION=3m k6_WEATHER_RATE=50 k6_HOT_RATE=8 k6_AI_RATE=3 k6_ATTACH_VUS=12; done
    # 찬 격자에 날씨 초당 50건 30초(기상청 1.7초). 날씨 누락 비율.
    python3 run.py "$TAG" c4-hot-$n script=mix stub=1700,1700,50,300,30 k6_VUS=50 k6_DURATION=30s k6_HOT_RATE=50
  done
fi

if has 5; then
  python3 case5_explain.py "$TAG"
  # 지도 300 VU 3분(요청 사이 1~3초), 재시작마다 데운 뒤 순차 3회.
  for n in 1 2 3; do python3 run.py "$TAG" c5-map300-$n k6_APPLICANTS=0 k6_MAP_VUS=300 k6_MAP_DUR=180s; done
fi
echo "batch done $(date +%T)"
