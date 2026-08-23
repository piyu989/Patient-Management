# --- Stage 1: Build Phase ---
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Cache Maven dependencies before copying source code
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and package application
COPY src ./src
RUN mvn clean package -DskipTests

# --- Stage 2: Runtime Phase ---
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Copy built JAR from the builder stage
COPY --from=builder /build/target/*.jar app.jar

# Expose actual app port (configured in application.properties)
EXPOSE 4000

# Optimized JVM settings for container environments
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]