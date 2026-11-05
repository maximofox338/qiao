# syntax=docker/dockerfile:1.7
# Build:  docker build -t qiao-backend .
#         (optional) --build-arg MAVEN_MIRROR=https://maven.aliyun.com/repository/public

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
ARG MAVEN_MIRROR=""
RUN printf '<settings><mirrors>%s</mirrors></settings>' \
      "$( [ -n "$MAVEN_MIRROR" ] && printf '<mirror><id>build-mirror</id><mirrorOf>central</mirrorOf><url>%s</url></mirror>' "$MAVEN_MIRROR" )" \
      > /build/settings.xml
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -s /build/settings.xml dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -s /build/settings.xml -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd --system qiao \
    && useradd --system --gid qiao --home-dir /app --no-create-home --shell /usr/sbin/nologin qiao \
    && mkdir -p /app /data/uploads \
    && chown -R qiao:qiao /app /data/uploads
WORKDIR /app
COPY --from=build --chown=qiao:qiao /build/target/qiao-backend.jar app.jar
USER qiao
ENV SPRING_PROFILES_ACTIVE=prod \
    STORAGE_LOCAL_DIR=/data/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
VOLUME /data/uploads
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
