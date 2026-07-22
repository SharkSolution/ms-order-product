# ============================================
# Stage 1: Build
# ============================================
FROM gradle:8.4-jdk17 AS builder

WORKDIR /app

# Copy Gradle wrapper and configuration files first for dependency caching
COPY gradlew ./
COPY gradle ./gradle
# lombok.config es OBLIGATORIO en la imagen: sin él, @Qualifier no se copia a los
# constructores de Lombok y el perfil cloud inyecta el JdbcTemplate equivocado
# (login lento ~60 s). Ver docs/220 / commit 5989c74.
COPY build.gradle settings.gradle lombok.config ./

# Download dependencies (this layer will be cached if dependencies don't change)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds)
RUN ./gradlew clean bootJar -x test --no-daemon

# Verify the JAR was created
RUN ls -lh /app/build/libs/

# ============================================
# Stage 2: Runtime
# ============================================
FROM amazoncorretto:17-alpine3.19

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy the JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Change ownership to non-root user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose port 8081
EXPOSE 8081

# JVM options for containerized environments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=100 \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom"

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Run the application
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
