#!/bin/bash
# Kafka Connect에 MySQL Source + ES Sink Connector 등록

CONNECT_URL="http://localhost:8083"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONNECTORS_DIR="$SCRIPT_DIR/../docker/kafka-connect/connectors"

echo "=== Kafka Connect 상태 확인 ==="
until curl -s "$CONNECT_URL/" > /dev/null 2>&1; do
    echo "Kafka Connect 대기 중..."
    sleep 5
done
echo "Kafka Connect 준비 완료"

echo ""
echo "=== MySQL Source Connector 등록 ==="
curl -s -X POST "$CONNECT_URL/connectors" \
    -H "Content-Type: application/json" \
    -d @"$CONNECTORS_DIR/mysql-source.json" | python3 -m json.tool

echo ""
echo "=== ES Sink Connector 등록 ==="
curl -s -X POST "$CONNECT_URL/connectors" \
    -H "Content-Type: application/json" \
    -d @"$CONNECTORS_DIR/es-sink.json" | python3 -m json.tool

echo ""
echo "=== 등록된 Connector 목록 ==="
sleep 3
curl -s "$CONNECT_URL/connectors" | python3 -m json.tool

echo ""
echo "=== Connector 상태 확인 ==="
for connector in mysql-source es-sink; do
    echo "--- $connector ---"
    curl -s "$CONNECT_URL/connectors/$connector/status" | python3 -m json.tool
done
