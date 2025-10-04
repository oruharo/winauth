# Build stage
FROM maven:3.9.6-amazoncorretto-17 AS builder

WORKDIR /build

# Download dependencies with cache
RUN --mount=type=bind,source=pom.xml,target=pom.xml \
    --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B

# Build with source code mounted
RUN --mount=type=bind,source=pom.xml,target=pom.xml \
    --mount=type=bind,source=src,target=src \
    --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests && \
    mv target/*.jar /build/app.jar

# Runtime stage
FROM amazoncorretto:17

WORKDIR /app

# Copy JAR from build stage
COPY --from=builder /build/app.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
