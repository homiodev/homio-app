IMAGE_NAME=homiodev/homio-app
TAG=latest
FULL_IMAGE=$(IMAGE_NAME):$(TAG)

.PHONY: all build push

all: build push

build:
	docker build -t $(FULL_IMAGE) .

push:
	docker push $(FULL_IMAGE)
