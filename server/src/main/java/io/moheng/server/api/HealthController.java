package io.moheng.server.api;

import io.moheng.server.config.MohengProperties;
import io.moheng.server.service.WorkerLocator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.moheng.server.api.Responses.data;

@RestController
@RequestMapping("/api")
public class HealthController {

    public static final String VERSION = "0.1.0";
    public static final String SCHEMA_VERSION = "1";

    private final JdbcTemplate jdbc;
    private final WorkerLocator workerLocator;
    private final MohengProperties properties;

    public HealthController(JdbcTemplate jdbc, WorkerLocator workerLocator, MohengProperties properties) {
        this.jdbc = jdbc;
        this.workerLocator = workerLocator;
        this.properties = properties;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean database = true;
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
        } catch (RuntimeException e) {
            database = false;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", database ? "ok" : "degraded");
        data.put("version", VERSION);
        data.put("schemaVersion", SCHEMA_VERSION);
        data.put("database", database);
        data.put("worker", workerLocator.locate().isPresent());
        data.put("workspaceRoot", properties.workspace().toString());
        data.put("dataDir", properties.dataDirectory().toString());
        data.put("workerHeap", properties.heap());
        data.put("analysisTimeoutSeconds", properties.timeoutSeconds());
        return data(data);
    }

    @GetMapping("/version")
    public Map<String, Object> version() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "墨衡 · 见微");
        data.put("version", VERSION);
        data.put("schemaVersion", SCHEMA_VERSION);
        return data(data);
    }
}
