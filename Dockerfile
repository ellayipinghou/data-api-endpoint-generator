# Stage 1: Build the app
FROM eclipse-temurin:25 AS builder
WORKDIR /app

# Copy Gradle configuration first so dependency layers can be cached
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy application source
COPY src src

# Build the application
RUN ./gradlew bootJar --no-daemon


# Stage 2: Run the app
# Does not need Gradle or JDK compilation tools
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=builder /app/build/libs/dataserv.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]