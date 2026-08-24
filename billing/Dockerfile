# --- Stage 1: Build Phase (Debian-based for native glibc/protoc support) ---
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and package application
COPY src ./src
RUN mvn clean package -DskipTests

# --- Stage 2: Runtime Phase (Lightweight Alpine JRE) ---
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Expose HTTP and gRPC ports
EXPOSE 4001
EXPOSE 9001

# Optimized JVM runtime execution
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]