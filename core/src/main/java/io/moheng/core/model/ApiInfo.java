package io.moheng.core.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public record ApiInfo(
        String id,
        String key,
        String verb,
        String path,
        String controller,
        String handlerKey,
        String handlerOwner,
        String handlerName,
        String file,
        int line,
        List<String> classPaths,
        List<String> methodPaths,
        Map<String, List<String>> conditions,
        String annotationResolution,
        boolean inherited
) {

    public ApiInfo {
        classPaths = classPaths == null ? List.of() : List.copyOf(classPaths);
        methodPaths = methodPaths == null ? List.of() : List.copyOf(methodPaths);
        conditions = conditions == null ? Map.of() : Map.copyOf(conditions);
        annotationResolution = annotationResolution == null ? "FQN" : annotationResolution;
    }

    public static String apiKey(String verb, String path) {
        return "API:" + verb + " " + path;
    }

    public static String apiId(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(key.hashCode());
        }
    }
}
