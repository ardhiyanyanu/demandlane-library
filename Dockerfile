# Stage 1: Build stage using Maven
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="demandline-team"
LABEL description="Library Management Service"

WORKDIR /app

# Copy the JAR file from builder stage
COPY --from=builder /build/target/library-1.0.0.jar app.jar

# Expose the application port
EXPOSE 8080

# Set JVM options
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the Spring Boot application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

