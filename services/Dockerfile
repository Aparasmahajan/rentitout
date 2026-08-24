# One Dockerfile for every service: the module name is a build argument.
#   docker build -f services/Dockerfile --build-arg MODULE=user-service .
# Build context is the repo root (radius/).

FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /build
# Copy only the POMs first so a code-only change reuses the dependency layer.
COPY services/pom.xml services/pom.xml
COPY services/common/pom.xml services/common/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/user-service/pom.xml services/user-service/pom.xml
COPY services/listing-service/pom.xml services/listing-service/pom.xml
COPY services/search-service/pom.xml services/search-service/pom.xml
COPY services/booking-service/pom.xml services/booking-service/pom.xml
COPY services/payment-service/pom.xml services/payment-service/pom.xml
COPY services/notification-service/pom.xml services/notification-service/pom.xml
RUN mvn -f services/pom.xml -B -q dependency:go-offline -DskipTests || true

FROM deps AS build
ARG MODULE
WORKDIR /build
COPY services services
RUN mvn -f services/pom.xml -B -pl ${MODULE} -am -DskipTests package \
 && cp services/${MODULE}/target/*.jar /build/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S radius && adduser -S radius -G radius
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER radius
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseZGC -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
