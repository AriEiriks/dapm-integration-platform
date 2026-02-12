#!/usr/bin/env bash

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export DATA_DIR="${REPO_ROOT}/infra/kafka/data"
echo "Using DATA_DIR=${DATA_DIR}"

# 1) Ensure shared external network
if ! docker network inspect kafka-cluster >/dev/null 2>&1; then
  echo "Creating docker network: kafka-cluster"
  docker network create --driver bridge kafka-cluster
else
  echo "Docker network 'kafka-cluster' already exists."
fi

cd infra
cd kafka

docker compose -f kafka-orgA.yml -p kafka-orga up -d
# docker compose -f kafka-orgB.yml -p kafka-orgb up -d

cd ..
cd ..


# 3) Build and run the root app compose
echo "=== Building Spring app (root)"
mvn clean package

echo "=== Restarting root compose (app + databases)"
docker compose down --volumes --remove-orphans
docker compose build --no-cache
docker compose up
