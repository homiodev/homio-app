#!/bin/bash

docker login -u ${DOCKER_USERNAME} -p ${DOCKER_PASSWORD}
docker push ruslanmasuk1985/touchhome-core:$1
docker push USER/REPO
