# Stage 1: Build the Spring Boot application
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Run the application
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# JVM memory for a 512 MB container. Tomcat/Hikari settings do not limit the
# JVM itself: without these, heap + metaspace + thread stacks can exceed the
# container limit and the process is killed.
# - MaxRAMPercentage=40: heap up to ~205 MB (the app uses ~50 MB); the rest of
#   the process (metaspace ~100 MB, code cache, threads, native) needs ~250 MB
# - Xss512k: smaller per-thread stacks (default 1 MB)
# - UseSerialGC: least memory overhead; fine for a small single-CPU instance
# - MaxMetaspaceSize / ReservedCodeCacheSize: cap class metadata and JIT code
# - ExitOnOutOfMemoryError: restart cleanly instead of running half-broken
# Override in Render by setting JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=40 -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]