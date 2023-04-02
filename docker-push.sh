#!/bin/bash

docker login -u ${DOCKER_USERNAME} -p ${DOCKER_PASSWORD}

docker build -t homio/app:$1 .
docker push homio/app:$1

docker build -t homio/app:latest .
docker push homio/app:latest
