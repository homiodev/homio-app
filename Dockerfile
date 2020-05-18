FROM adoptopenjdk/openjdk8:debian-slim

LABEL maintainer="Ruslan Masiuk <ruslan.masuk@gmail.com>"
LABEL image.application.name=touchHome-core

COPY app/target/touchHome.jar touchHome.jar
ENTRYPOINT ["java","-jar","/touchHome.jar"]

EXPOSE 9111
