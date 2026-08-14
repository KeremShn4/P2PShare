ARG BASE_IMAGE=eclipse-temurin:17-jdk
FROM ${BASE_IMAGE}

WORKDIR /app
COPY src ./src
RUN javac -d bin $(find src -name "*.java")

ENV SHARED_FOLDER=/shared
ENV TCP_PORT=5001
ENV EXCLUDED_FOLDERS=
ENV AUTO_DOWNLOAD=false

RUN mkdir -p /shared

CMD ["java", "-cp", "bin", "p2p.HeadlessNode"]
