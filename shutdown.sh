#!/usr/bin/env bash

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export DATA_DIR="${REPO_ROOT}/infra/kafka/data"
echo "Using DATA_DIR=${DATA_DIR}"

cd infra/kafka

docker compose -f kafka-orgA.yml -p kafka-orga down -v --remove-orphans
docker compose -f kafka-orgB.yml -p kafka-orgb down -v --remove-orphans
cd ../../
docker compose down --volumes --remove-orphans
