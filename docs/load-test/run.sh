#!/usr/bin/env bash
# nanugi JMeter 부하 테스트 실행 스크립트.
# 전제: docker compose로 postgres·redis·kafka·payment-service·analysis-service가 떠 있고
#       seed.sql을 적용한 상태. 결과는 OUT_DIR(기본 ./results)에 남는다.
set -euo pipefail

cd "$(dirname "$0")"
OUT_DIR="${OUT_DIR:-./results}"
ROUNDS="${ROUNDS:-3}"
DURATION="${DURATION:-60}"
RAMPUP="${RAMPUP:-5}"
SKIP=$((RAMPUP + 5))
SETTLEMENT_THREADS="${SETTLEMENT_THREADS:-100}"
ANALYTICS_THREADS="${ANALYTICS_THREADS:-50}"
mkdir -p "$OUT_DIR"

TOKEN="$(python3 make-token.py 1 7200)"

run() { # plan threads duration label
  local out="$OUT_DIR/$4.jtl"
  rm -f "$out"
  jmeter -n -t "$1.jmx" -Jtoken="$TOKEN" -Jthreads="$2" -Jrampup="$RAMPUP" \
    -Jduration="$3" -l "$out" -j "$OUT_DIR/jmeter.log" > /dev/null
  printf '%-22s ' "$4"
  python3 analyze.py "$out" "$SKIP"
}

for plan in settlement analytics; do
  threads=$SETTLEMENT_THREADS
  [ "$plan" = analytics ] && threads=$ANALYTICS_THREADS
  run "$plan" "$threads" 30 "$plan-warmup" > /dev/null   # JIT·커넥션 풀 예열(집계 제외)
  for i in $(seq 1 "$ROUNDS"); do
    run "$plan" "$threads" "$((DURATION + SKIP))" "$plan-$i"
  done
done
