# Stage 1: build the Spring Boot application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Dependencies resolve in their own layer, so a source-only change does not
# re-download the world on every image build.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: runtime with Tesseract for the OCR fallback
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

# The service parses hostile PDFs by design, so the process that does it should
# not be root. It writes nothing outside the JVM's temp dir.
RUN useradd --system --create-home --shell /usr/sbin/nologin scanner

# Wildcard so a version bump in pom.xml doesn't silently break the copy.
COPY --from=build /app/target/backend-*.jar app.jar

USER scanner
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
