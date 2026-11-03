package io.moheng.core.risk;

import io.moheng.core.model.FileChange;
import io.moheng.core.model.RiskReason;
import io.moheng.core.model.SafeMode;
import io.moheng.core.model.SymbolChange;
import io.moheng.core.model.SymbolInfo;
import io.moheng.core.source.AnalyzedProject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SafeModeDetector {

    private static final String[] AUTH_MARKERS = {
            "/auth/", "auth/", "/security/", "security/", "shiro", "/login", "login/",
            "/token", "token/", "permission"
    };

    public SafeMode detect(List<FileChange> changes, List<SymbolChange> symbolChanges,
                           AnalyzedProject head) {
        Map<String, RiskReason> triggers = new LinkedHashMap<>();
        for (FileChange change : changes) {
            String path = normalize(change.path());
            String baseName = baseName(path);
            String lower = path.toLowerCase(Locale.ROOT);

            if (isBuildFile(baseName)) {
                add(triggers, "BUILD_FILE_CHANGED", 20, "构建文件变更", path);
            }
            if (isConfigFile(baseName)) {
                add(triggers, "CONFIG_CHANGED", 15, "应用配置变更", path);
            }
            if (isDatabaseFile(path, baseName)) {
                add(triggers, "DB_SCHEMA_CHANGED", 20, "数据库 schema 或迁移变更", path);
            }
            if (path.contains("/common/") || path.startsWith("common/")
                    || path.contains("/commons/") || path.startsWith("commons/")) {
                add(triggers, "COMMON_MODULE_CHANGED", 15, "公共模块变更", path);
            }
            if (containsAny(lower, AUTH_MARKERS)) {
                add(triggers, "AUTH_AREA_CHANGED", 20, "鉴权或会话相关代码变更", path);
            }
            if (lower.contains("jackson") || lower.contains("objectmapper")) {
                add(triggers, "SERIALIZATION_CHANGED", 15, "序列化配置变更", path);
            }
            if (isCiFile(path, baseName)) {
                add(triggers, "CI_DEPLOY_CHANGED", 10, "CI 或部署配置变更", path);
            }
            if (change.javaFile()) {
                detectSymbolTriggers(triggers, change, head);
            }
        }

        for (SymbolChange change : symbolChanges) {
            if (change.publicDto() && isPublicDtoContractChange(change)) {
                add(triggers, "PUBLIC_DTO_CHANGED", 20, "公共 DTO 契约变化", normalize(change.file()),
                        Map.of("symbol", change.key().id(), "change", change.changeType().name()));
            }
        }

        List<RiskReason> reasons = new ArrayList<>(triggers.values());
        String recommendation = reasons.isEmpty() ? ""
                : "建议执行全量或扩大范围回归；至少覆盖受影响 API 与公共契约。";
        return new SafeMode(!reasons.isEmpty(), reasons, recommendation);
    }

    private void detectSymbolTriggers(Map<String, RiskReason> triggers, FileChange change,
                                      AnalyzedProject head) {
        String path = normalize(change.path());
        for (SymbolInfo symbol : head.symbolsInFile(path).values()) {
            String annotations = symbol.annotationFingerprint();
            if (annotations.contains("Json") || annotations.contains("serialVersionUID")) {
                add(triggers, "SERIALIZATION_CHANGED", 15, "序列化注解或字段变更", path,
                        Map.of("symbol", symbol.key().id()));
            }
            if (symbol.key().kind() == io.moheng.core.model.SymbolKey.Kind.FIELD
                    && symbol.key().name().equals("serialVersionUID")) {
                add(triggers, "SERIALIZATION_CHANGED", 15, "serialVersionUID 变更", path,
                        Map.of("symbol", symbol.key().id()));
            }
            if (annotations.contains("Configuration")
                    || symbol.structuralFingerprint().contains("WebMvcConfigurer")) {
                add(triggers, "FRAMEWORK_CONFIG_CHANGED", 15, "框架配置类变更", path,
                        Map.of("symbol", symbol.key().id()));
            }
        }
    }

    private boolean isPublicDtoContractChange(SymbolChange change) {
        return switch (change.key().kind()) {
            case FIELD -> change.changeType() == SymbolChange.ChangeType.ADDED
                    || change.changeType() == SymbolChange.ChangeType.REMOVED
                    || change.changeType() == SymbolChange.ChangeType.STRUCTURE_CHANGED
                    || change.changeType() == SymbolChange.ChangeType.CONTRACT_CHANGED;
            case CLASS -> change.changeType() == SymbolChange.ChangeType.REMOVED
                    || change.changeType() == SymbolChange.ChangeType.STRUCTURE_CHANGED
                    || change.changeType() == SymbolChange.ChangeType.CONTRACT_CHANGED;
            case METHOD -> false;
        };
    }

    private boolean isBuildFile(String baseName) {
        return baseName.equals("pom.xml")
                || baseName.equals("build.gradle")
                || baseName.equals("build.gradle.kts")
                || baseName.equals("settings.gradle")
                || baseName.equals("settings.gradle.kts")
                || baseName.equals("gradle.properties");
    }

    private boolean isConfigFile(String baseName) {
        return (baseName.startsWith("application") || baseName.startsWith("bootstrap"))
                && (baseName.endsWith(".yml") || baseName.endsWith(".yaml")
                || baseName.endsWith(".properties"));
    }

    private boolean isDatabaseFile(String path, String baseName) {
        String lower = path.toLowerCase(Locale.ROOT);
        return baseName.endsWith(".sql")
                || lower.contains("/db/migration/")
                || lower.contains("/liquibase/")
                || (baseName.contains("schema") && (baseName.endsWith(".xml") || baseName.endsWith(".sql")));
    }

    private boolean isCiFile(String path, String baseName) {
        return baseName.equals("Dockerfile")
                || (baseName.startsWith("docker-compose") && (baseName.endsWith(".yml") || baseName.endsWith(".yaml")))
                || baseName.equals(".gitlab-ci.yml")
                || path.startsWith(".github/workflows/") || path.contains("/.github/workflows/");
    }

    private boolean containsAny(String value, String[] markers) {
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private void add(Map<String, RiskReason> triggers, String code, int weight, String message, String file) {
        add(triggers, code, weight, message, file, Map.of());
    }

    private void add(Map<String, RiskReason> triggers, String code, int weight, String message,
                     String file, Map<String, Object> extra) {
        String key = code + "|" + file;
        if (triggers.containsKey(key)) {
            return;
        }
        Map<String, Object> evidence = new LinkedHashMap<>(extra);
        evidence.put("file", file);
        triggers.put(key, new RiskReason(code, weight, message, evidence));
    }

    private String normalize(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    private String baseName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
