package com.suresell.order.serivices;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de cache en disco resiliente.
 * Garantiza persistencia de datos críticos ante fallos de AWS o reinicios del microservicio.
 */
@Service
@Slf4j
public class DiskCacheService {

    private final ObjectMapper objectMapper;
    private final Path cacheBasePath;
    private final Map<String, LocalDateTime> lastUpdateTimes = new ConcurrentHashMap<>();

    @Value("${cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${cache.ttl-minutes:60}")
    private int ttlMinutes;

    public DiskCacheService(@Value("${cache.path:./cache}") String cachePath) {
        this.cacheBasePath = Paths.get(cachePath).toAbsolutePath().normalize();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        initializeCacheDirectories();
        log.info("DiskCacheService initialized at: {}", cacheBasePath);
    }

    private void initializeCacheDirectories() {
        try {
            Files.createDirectories(cacheBasePath);
            log.info("Cache directory ready: {}", cacheBasePath);
        } catch (IOException e) {
            log.error("CRITICAL: Cannot create cache directory: {}", cacheBasePath, e);
        }
    }

    /**
     * Guarda datos en cache con escritura atómica
     */
    public <T> boolean save(String cacheKey, T data) {
        if (!cacheEnabled) {
            return false;
        }

        try {
            Path filePath = cacheBasePath.resolve(cacheKey + ".json");
            Path tempFile = Files.createTempFile(cacheBasePath, ".tmp-", ".json");

            objectMapper.writeValue(tempFile.toFile(), data);
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            lastUpdateTimes.put(cacheKey, LocalDateTime.now());
            log.debug("Cache saved: {} ({} bytes)", cacheKey, Files.size(filePath));
            return true;
        } catch (IOException e) {
            log.error("Failed to save cache '{}': {}", cacheKey, e.getMessage());
            return false;
        }
    }

    /**
     * Lee datos desde cache
     */
    public <T> Optional<T> read(String cacheKey, TypeReference<T> typeReference) {
        if (!cacheEnabled) {
            return Optional.empty();
        }

        try {
            Path filePath = cacheBasePath.resolve(cacheKey + ".json");

            if (!Files.exists(filePath)) {
                log.debug("Cache miss: {}", cacheKey);
                return Optional.empty();
            }

            T data = objectMapper.readValue(filePath.toFile(), typeReference);
            log.debug("Cache hit: {}", cacheKey);
            return Optional.of(data);
        } catch (IOException e) {
            log.error("Failed to read cache '{}': {}", cacheKey, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lee datos desde cache (versión con Class)
     */
    public <T> Optional<T> read(String cacheKey, Class<T> clazz) {
        if (!cacheEnabled) {
            return Optional.empty();
        }

        try {
            Path filePath = cacheBasePath.resolve(cacheKey + ".json");

            if (!Files.exists(filePath)) {
                return Optional.empty();
            }

            T data = objectMapper.readValue(filePath.toFile(), clazz);
            return Optional.of(data);
        } catch (IOException e) {
            log.error("Failed to read cache '{}': {}", cacheKey, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Obtiene timestamp de última actualización
     */
    public Optional<LocalDateTime> getLastUpdateTime(String cacheKey) {
        return Optional.ofNullable(lastUpdateTimes.get(cacheKey));
    }

    /**
     * Limpia TODO el cache (usar con precaución)
     */
    public void clearAllCache() {
        log.warn("Clearing ALL cache...");
        try {
            Files.walk(cacheBasePath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.info("Deleted cache file: {}", path.getFileName());
                        } catch (IOException e) {
                            log.error("Failed to delete cache file: {}", path, e);
                        }
                    });
            lastUpdateTimes.clear();
            log.info("All cache cleared successfully");
        } catch (IOException e) {
            log.error("Failed to clear cache: {}", e.getMessage());
        }
    }

    /**
     * Estadísticas del cache
     */
    public CacheStats getStats() {
        try {
            long totalFiles = Files.walk(cacheBasePath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .count();

            long totalSize = Files.walk(cacheBasePath)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();

            return new CacheStats(cacheEnabled, totalFiles, totalSize, cacheBasePath.toString());
        } catch (IOException e) {
            return new CacheStats(false, 0, 0, cacheBasePath.toString());
        }
    }

    public record CacheStats(boolean enabled, long totalFiles, long totalSizeBytes, String cachePath) {
        public String formatSize() {
            if (totalSizeBytes < 1024) return totalSizeBytes + " B";
            if (totalSizeBytes < 1024 * 1024) return String.format("%.2f KB", totalSizeBytes / 1024.0);
            return String.format("%.2f MB", totalSizeBytes / (1024.0 * 1024.0));
        }
    }
}
