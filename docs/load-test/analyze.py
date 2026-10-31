"""JMeter 결과(.jtl CSV)에서 램프업 구간을 뺀 정상 구간의 처리량과 지연 시간을 계산한다.

사용법: python3 analyze.py <result.jtl> <제외할 앞부분 초>
"""
import csv
import math
import sys


def percentile(sorted_values, pct):
    index = math.ceil(pct / 100 * len(sorted_values)) - 1
    return sorted_values[max(0, min(index, len(sorted_values) - 1))]


def analyze(path, skip_seconds):
    with open(path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    if not rows:
        sys.exit(f"{path}: 결과가 비어 있습니다")
    first = min(int(r["timeStamp"]) for r in rows)
    window_start = first + skip_seconds * 1000
    steady = [r for r in rows if int(r["timeStamp"]) >= window_start]
    if not steady:
        sys.exit(f"{path}: 제외 구간 이후 샘플이 없습니다")
    window_end = max(int(r["timeStamp"]) + int(r["elapsed"]) for r in steady)
    seconds = (window_end - window_start) / 1000
    errors = sum(1 for r in steady if r["success"] != "true")
    latencies = sorted(int(r["elapsed"]) for r in steady)
    return {
        "samples": len(steady),
        "errors": errors,
        "throughput": len(steady) / seconds,
        "p50": percentile(latencies, 50),
        "p95": percentile(latencies, 95),
        "p99": percentile(latencies, 99),
    }


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("usage: analyze.py <result.jtl> <skipSeconds>")
    r = analyze(sys.argv[1], int(sys.argv[2]))
    print(f"samples={r['samples']} errors={r['errors']} throughput={r['throughput']:.1f}/s "
          f"p50={r['p50']}ms p95={r['p95']}ms p99={r['p99']}ms")
