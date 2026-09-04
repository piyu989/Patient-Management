# --- Stage 1: Build Phase ---
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and package application
COPY src ./src
RUN mvn clean package -DskipTests

# --- Stage 2: Runtime Phase ---
FROM eclipse-temurin:21-jre AS runner
WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 4000

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]