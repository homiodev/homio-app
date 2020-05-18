#!/bin/bash

docker login -u ${DOCKER_USERNAME} -p ${DOCKER_PASSWORD}
docker build -t ruslanmasuk1985/touchhome-core:$1 .
docker push ruslanmasuk1985/touchhome-core:$1
