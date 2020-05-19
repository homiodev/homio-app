FROM debian:10.3-slim

LABEL maintainer="Ruslan Masiuk <ruslan.masuk@gmail.com>"
LABEL image.application.name=touchHome-core

# Install dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    gcc \
    unzip \
    wget \
    zip \
    git \
    postgresql \
    python \
    python-dev \
    python-pip \
    python-virtualenv \
    --no-install-recommends && \
    rm -rf /var/lib/apt/lists/*

# Install java
ENV JAVA_HOME='/usr/lib/jvm/default-jvm' JAVA_VERSION="8"

RUN mkdir -p "${JAVA_HOME}" && \
    zulu8_amd64_url='https://cdn.azul.com/zulu/bin/zulu8.42.0.23-ca-jdk8.0.232-linux_x64.tar.gz' && \
    zulu8_armhf_url='https://cdn.azul.com/zulu-embedded/bin/zulu8.42.0.195-ca-jdk1.8.0_232-linux_aarch32hf.tar.gz' && \
    zulu8_arm64_url='https://cdn.azul.com/zulu-embedded/bin/zulu8.42.0.195-ca-jdk1.8.0_232-linux_aarch64.tar.gz' && \
    zulu11_amd64_url='https://cdn.azul.com/zulu/bin/zulu11.37.17-ca-jdk11.0.6-linux_x64.tar.gz' && \
    zulu11_armhf_url='https://cdn.azul.com/zulu-embedded/bin/zulu11.37.48-ca-jdk11.0.6-linux_aarch32hf.tar.gz' && \
    zulu11_arm64_url='https://cdn.azul.com/zulu-embedded/bin/zulu11.37.48-ca-jdk11.0.6-linux_aarch64.tar.gz' && \
    url_var="zulu${JAVA_VERSION}_$(dpkg --print-architecture)_url" && \
    eval "java_url=\$$url_var" && \
    wget -nv -O /tmp/java.tar.gz "${java_url}" && \
    tar --exclude='demo' --exclude='sample' --exclude='src.zip' -xf /tmp/java.tar.gz --strip-components=1 -C "${JAVA_HOME}" && \
    sed -i 's/^#crypto.policy=unlimited/crypto.policy=limited/' "${JAVA_HOME}/jre/lib/security/java.security"; \
    rm /tmp/java.tar.gz && \
    update-alternatives --install /usr/bin/java java "${JAVA_HOME}/bin/java" 50 && \
    update-alternatives --install /usr/bin/javac javac "${JAVA_HOME}/bin/javac" 50

RUN mkdir /opt/touchhome
WORKDIR /opt/touchhome

# Install pyserial
RUN pip install pyserial
RUN git clone https://github.com/WiringPi/WiringPi.git
ENV WIRINGPI_SUDO=''
RUN cd WiringPi && ./build && cd ..

RUN git clone https://github.com/technion/lol_dht22
RUN cd lol_dht22 && ./configure && make && cd ..

# Configure postgres
VOLUME  ["/var/lib/postgresql/data"]

COPY app/target/touchHome.jar touchHome.jar
ENTRYPOINT ["java","-jar","touchHome.jar"]

EXPOSE 9111
