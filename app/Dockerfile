FROM openjdk:8-jdk-alpine

LABEL maintainer "Ruslan Masiuk <ruslan.masuk@gmail.com>"
LABEL image.application.name=touchHome-core

COPY target/touchHome.jar touchHome.jar
ENTRYPOINT ["java","-jar","/app.jar"]

EXPOSE 9111
