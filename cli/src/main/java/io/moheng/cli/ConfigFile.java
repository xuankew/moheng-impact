package io.moheng.cli;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ConfigFile {

    private static final String FILE_NAME = ".moheng.yml";

    private final Path path;
    private final Map<String, Object> root;

    private ConfigFile(Path path, Map<String, Object> root) {
        this.path = path;
        this.root = root;
    }

    static ConfigFile load(Path repo) {
        Path file = repo.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return new ConfigFile(null, Map.of());
        }
        try (InputStream input = Files.newInputStream(file)) {
            Object loaded = new Yaml().load(input);
            if (loaded == null) {
                return new ConfigFile(file, Map.of());
            }
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new CliException(ExitCodes.USAGE, FILE_NAME + " 格式无效：顶层必须是映射");
            }
            Map<String, Object> values = new java.util.LinkedHashMap<>();
            map.forEach((key, value) -> values.put(String.valueOf(key), value));
            return new ConfigFile(file, values);
        } catch (IOException e) {
            throw new CliException(ExitCodes.USAGE, "无法读取 " + file + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            if (e instanceof CliException cliException) {
                throw cliException;
            }
            throw new CliException(ExitCodes.USAGE, FILE_NAME + " 解析失败: " + e.getMessage(), e);
        }
    }

    boolean present() {
        return path != null;
    }

    Path path() {
        return path;
    }

    Path directory() {
        return path == null ? Path.of(".").toAbsolutePath().normalize() : path.getParent();
    }

    String string(String... keys) {
        Object value = get(keys);
        return value == null ? null : String.valueOf(value);
    }

    Integer integer(String... keys) {
        Object value = get(keys);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new CliException(ExitCodes.USAGE, FILE_NAME + " 中 " + String.join(".", keys) + " 不是整数");
        }
    }

    Boolean bool(String... keys) {
        Object value = get(keys);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    List<String> strings(String... keys) {
        Object value = get(keys);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return List.of(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Object get(String... keys) {
        Object current = root;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(key);
        }
        return current;
    }
}
