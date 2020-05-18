#!/bin/bash

docker login -u ${DOCKER_USERNAME} -p ${DOCKER_PASSWORD}
docker build -t touchhome/core:$1 .
docker push touchhome/core:$1
