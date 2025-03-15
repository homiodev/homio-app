FROM eclipse-temurin:17-jdk-slim

LABEL maintainer="ruslan.masuk@gmail.com"
LABEL image.application.name=homio-app

# Install dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    gcc \
    unzip \
    wget \
    zip \
    git \
    ffmpeg \
    chromium-browser \
    tmate \
    nodejs \
    postgresql \
    python \
    python-dev \
    python-pip \
    python-virtualenv \
    --no-install-recommends && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir /opt/homio
WORKDIR /opt/homio

# Configure postgres
VOLUME  ["/var/lib/postgresql/data"]

COPY target/homio-app.jar homio-app.jar
ENTRYPOINT ["java", "-jar", "homio-app.jar"]

EXPOSE 9111
