#!/bin/bash

# =============================================================================
# Concert Reservation Load Test Runner (10th Week)
# =============================================================================

# 기본 설정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JMX_FILE="${SCRIPT_DIR}/concert-full-load-test.jmx"
RESULT_DIR="${SCRIPT_DIR}/results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# 기본 파라미터
DURATION=${DURATION:-180}
THREADS_TOKEN=${THREADS_TOKEN:-100}
THREADS_STATUS=${THREADS_STATUS:-200}
THREADS_CONCERT=${THREADS_CONCERT:-100}
THREADS_SEAT=${THREADS_SEAT:-200}
RAMP_UP=${RAMP_UP:-30}

# 결과 디렉토리 생성
mkdir -p "${RESULT_DIR}"

echo "=============================================="
echo "Concert Reservation Load Test (10th Week)"
echo "=============================================="
echo "Test File: ${JMX_FILE}"
echo "Duration: ${DURATION}s"
echo "Threads - Token: ${THREADS_TOKEN}, Status: ${THREADS_STATUS}"
echo "Threads - Concert: ${THREADS_CONCERT}, Seat: ${THREADS_SEAT}"
echo "Ramp-up: ${RAMP_UP}s"
echo "=============================================="

# JMeter 실행
echo ""
echo "[1/3] Starting JMeter load test..."

jmeter -n -t "${JMX_FILE}" \
  -l "${RESULT_DIR}/result_${TIMESTAMP}.jtl" \
  -j "${RESULT_DIR}/jmeter_${TIMESTAMP}.log" \
  -JDURATION=${DURATION} \
  -JTHREADS_TOKEN=${THREADS_TOKEN} \
  -JTHREADS_STATUS=${THREADS_STATUS} \
  -JTHREADS_CONCERT=${THREADS_CONCERT} \
  -JTHREADS_SEAT=${THREADS_SEAT} \
  -JRAMP_UP=${RAMP_UP}

if [ $? -ne 0 ]; then
  echo "ERROR: JMeter test failed!"
  exit 1
fi

echo ""
echo "[2/3] Generating HTML report..."

# HTML 리포트 생성
REPORT_DIR="${RESULT_DIR}/report_${TIMESTAMP}"
jmeter -g "${RESULT_DIR}/result_${TIMESTAMP}.jtl" -o "${REPORT_DIR}"

if [ $? -eq 0 ]; then
  echo "HTML Report: ${REPORT_DIR}/index.html"
else
  echo "WARNING: Failed to generate HTML report"
fi

echo ""
echo "[3/3] Test completed!"
echo "=============================================="
echo "Results:"
echo "  - JTL File: ${RESULT_DIR}/result_${TIMESTAMP}.jtl"
echo "  - Log File: ${RESULT_DIR}/jmeter_${TIMESTAMP}.log"
echo "  - Report:   ${REPORT_DIR}/index.html"
echo "=============================================="
