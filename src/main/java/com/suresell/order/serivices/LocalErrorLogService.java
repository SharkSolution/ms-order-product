package com.suresell.order.serivices;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@Slf4j
public class LocalErrorLogService {

    private final Path errorLogFilePath;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public LocalErrorLogService(@Value("${cache.path:./cache}") String cachePath) {
        // Ensure error log file is within the cache directory
        this.errorLogFilePath = Paths.get(cachePath).toAbsolutePath().normalize().resolve("cache_errors.json");

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void initializeErrorLogFile() {
        try {
            Files.createDirectories(errorLogFilePath.getParent());
            if (!Files.exists(errorLogFilePath)) {
                Files.createFile(errorLogFilePath);
                // Write an empty JSON array to ensure it's a valid JSON file
                Files.writeString(errorLogFilePath, "[]");
            }
            log.info("Local error log file initialized at: {}", errorLogFilePath);
        } catch (IOException e) {
            log.error("CRITICAL: Failed to initialize local error log file: {}", errorLogFilePath, e);
        }
    }

    public void logError(String component, String cacheKey, String errorMessage) {
        lock.writeLock().lock();
        try {
            List<ErrorLogEntry> errors = readErrorLogFile().orElse(new ArrayList<>());
            ErrorLogEntry newEntry = new ErrorLogEntry(
                    errors.isEmpty() ? 1 : Collections.max(errors, (e1, e2) -> Integer.compare(e1.id(), e2.id())).id() + 1,
                    LocalDateTime.now(),
                    component,
                    cacheKey,
                    errorMessage,
                    false
            );
            errors.add(newEntry);
            objectMapper.writeValue(errorLogFilePath.toFile(), errors);
            log.info("Error logged locally: Component={}, CacheKey={}, Message={}", component, cacheKey, errorMessage);
        } catch (IOException e) {
            log.error("CRITICAL: Failed to write error to local log file: Component={}, CacheKey={}, Message={}, FileError={}",
                    component, cacheKey, errorMessage, e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<List<ErrorLogEntry>> getAllErrors() throws IOException {
        lock.readLock().lock();
        try {
            return readErrorLogFile();
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean markErrorAsResolved(int id) {
        lock.writeLock().lock();
        try {
            Optional<List<ErrorLogEntry>> errorsOpt = readErrorLogFile();
            if (errorsOpt.isEmpty()) {
                return false;
            }

            List<ErrorLogEntry> errors = errorsOpt.get();
            boolean foundAndUpdated = false;
            for (int i = 0; i < errors.size(); i++) {
                if (errors.get(i).id() == id) {
                    errors.set(i, errors.get(i).withResuelta(true));
                    foundAndUpdated = true;
                    break;
                }
            }

            if (foundAndUpdated) {
                objectMapper.writeValue(errorLogFilePath.toFile(), errors);
                log.info("Error ID {} marked as resolved in local log.", id);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("Failed to mark error ID {} as resolved in local log: {}", id, e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Optional<List<ErrorLogEntry>> readErrorLogFile() throws IOException {
        if (!Files.exists(errorLogFilePath) || Files.size(errorLogFilePath) == 0) {
            return Optional.of(new ArrayList<>());
        }
        return Optional.of(objectMapper.readValue(errorLogFilePath.toFile(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ErrorLogEntry.class)));
    }

    // Record para representar una entrada de log de error
    public record ErrorLogEntry(int id, LocalDateTime timestamp, String componente, String cache_key, String mensaje_error, boolean resuelta) {
        // Método helper para crear una nueva instancia con 'resuelta' modificado
        public ErrorLogEntry withResuelta(boolean resuelta) {
            return new ErrorLogEntry(this.id, this.timestamp, this.componente, this.cache_key, this.mensaje_error, resuelta);
        }
    }
}
