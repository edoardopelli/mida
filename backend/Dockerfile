# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp -q dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -q -DskipTests package

FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 mida \
    && useradd --system --uid 10001 --gid mida --home-dir /app --shell /usr/sbin/nologin mida

WORKDIR /app
COPY --from=build /workspace/target/mida-backend-*.jar /app/app.jar

ENV TZ=Europe/Rome \
    JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -Djava.security.egd=file:/dev/urandom"

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
