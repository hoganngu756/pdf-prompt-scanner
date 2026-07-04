# Stage 1: Build the Spring Boot Application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create runtime environment with Tesseract
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install Tesseract OCR and language data
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

# Copy built JAR from Stage 1
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Expose backend port
EXPOSE 8080

# Run Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]
