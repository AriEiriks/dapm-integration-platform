#!/usr/bin/env bash

cd infra/kafka

docker compose -f kafka-orgA.yml -p kafka-orga down -v --remove-orphans
docker compose -f kafka-orgB.yml -p kafka-orgb down -v --remove-orphans
docker compose -f kafka-orgC.yml -p kafka-orgc down -v --remove-orphans
cd ../../
docker compose down --volumes --remove-orphans
