FROM adoptopenjdk/openjdk8:debian-slim

LABEL maintainer="Ruslan Masiuk <ruslan.masuk@gmail.com>"
LABEL image.application.name=touchHome-core

RUN apt-get update && apt-get upgrade && apt-get install -y postgresql
VOLUME  ["/etc/postgresql", "/var/log/postgresql", "/var/lib/postgresql"]

COPY app/target/touchHome.jar touchHome.jar
ENTRYPOINT ["java","-jar","/touchHome.jar"]

EXPOSE 9111
