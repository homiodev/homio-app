FROM eclipse-temurin:21-jdk

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        build-essential \
        unzip \
        wget \
        zip \
        git \
        maven \
        ffmpeg \
        chromium \
        tmate \
        nodejs \
        npm \
        postgresql \
        postgresql-contrib \
        python3 \
        python3-pip \
        python3-venv \
        software-properties-common \
        gosu && \
    add-apt-repository -y ppa:mozillateam/ppa && \
    apt-get update && \
    apt-get install -y firefox-esr && \
    apt-mark hold firefox-esr && \
    groupadd -r postgres || true && \
    useradd -r -g postgres postgres || true && \
    mkdir -p /var/lib/postgresql/data && \
    chown -R postgres:postgres /var/lib/postgresql && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set Postgres data directory (as in official image) and create a volume
ENV PGDATA=/var/lib/postgresql/data
VOLUME /var/lib/postgresql/data

# Create directory for Homio app and copy JAR
RUN mkdir -p /opt/homio
COPY target/homio-app.jar /homio-app.jar

ENV PATH="/usr/lib/postgresql/16/bin:$PATH"

# Copy entrypoint script (to initialize DB and launch services)
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

RUN ln -s /usr/bin/firefox-esr /usr/bin/firefox

# Ensure /opt/homio is the working dir (optional)
WORKDIR /opt/homio

EXPOSE 9111 9911

# Use the entrypoint script to start Postgres and then the Java app
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
