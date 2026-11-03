package io.moheng.analyzer.java;

import io.moheng.core.model.AnalysisRequest;
import io.moheng.core.source.SourceSet;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeMember;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class SpoonContext {

    private final SourceSet sources;
    private final AnalysisRequest.Options options;
    private final List<CtType<?>> types;
    private final Map<String, CtType<?>> typesByFqn = new LinkedHashMap<>();
    private final Map<String, String> typeFiles = new HashMap<>();
    private final Set<String> projectAnnotationNames = new TreeSet<>();
    private final Set<String> interfaceTypes = new TreeSet<>();
    private final Map<String, List<String>> directSupertypes = new HashMap<>();
    private final Map<String, Set<String>> ancestorCache = new HashMap<>();
    private final Map<String, List<CtExecutable<?>>> ownExecutables = new HashMap<>();
    private final Map<String, CtExecutable<?>> executablesByKey = new LinkedHashMap<>();
    // Spoon 元素按结构实现 equals/hashCode，普通 HashMap 键会跨类型碰撞，必须按身份查找。
    private final Map<CtExecutable<?>, String> keysByExecutable = new IdentityHashMap<>();
    private final Map<String, List<String>> overrides = new HashMap<>();
    private final Map<CtField<?>, String> fieldKeys = new IdentityHashMap<>();
    private final SourcePaths sourcePaths;

    SpoonContext(SourceSet sources, AnalysisRequest.Options options, Collection<CtType<?>> allTypes) {
        this.sources = sources;
        this.options = options;
        this.sourcePaths = new SourcePaths(sources);
        List<CtType<?>> collected = new ArrayList<>();
        Set<CtType<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CtType<?> type : allTypes) {
            collectType(type, collected, visited);
        }
        collected.sort(Comparator.comparing(CtType::getQualifiedName));
        this.types = List.copyOf(collected);
        for (CtType<?> type : types) {
            typesByFqn.putIfAbsent(type.getQualifiedName(), type);
            typeFiles.put(type.getQualifiedName(), sourcePaths.resolve(type, type));
            if (type.isAnnotationType()) {
                projectAnnotationNames.add(type.getSimpleName());
            }
            if (type.isInterface()) {
                interfaceTypes.add(type.getQualifiedName());
            }
        }
        buildHierarchy();
        buildExecutables();
        buildOverrides();
    }

    private static void collectType(CtType<?> type, List<CtType<?>> collected, Set<CtType<?>> visited) {
        if (type == null || type.isShadow() || !visited.add(type)) {
            return;
        }
        String fqn = type.getQualifiedName();
        String simple = type.getSimpleName();
        if (fqn != null && !fqn.isBlank() && simple != null && !simple.isBlank()
                && !simple.equals("package-info") && !simple.matches("\\d+")) {
            collected.add(type);
        }
        for (CtType<?> nested : type.getNestedTypes()) {
            collectType(nested, collected, visited);
        }
    }

    private void buildHierarchy() {
        for (CtType<?> type : types) {
            List<String> supers = new ArrayList<>();
            CtTypeReference<?> superclass = type.getSuperclass();
            if (superclass != null) {
                String name = superclass.getQualifiedName();
                if (typesByFqn.containsKey(name)) {
                    supers.add(name);
                }
            }
            List<String> interfaces = new ArrayList<>();
            for (CtTypeReference<?> ref : type.getSuperInterfaces()) {
                String name = ref.getQualifiedName();
                if (typesByFqn.containsKey(name)) {
                    interfaces.add(name);
                }
            }
            interfaces.sort(String::compareTo);
            supers.addAll(interfaces);
            directSupertypes.put(type.getQualifiedName(), List.copyOf(supers));
        }
    }

    private void buildExecutables() {
        for (CtType<?> type : types) {
            List<CtExecutable<?>> own = new ArrayList<>();
            for (CtTypeMember member : type.getTypeMembers()) {
                if (!(member instanceof CtExecutable<?> executable) || executable.isImplicit()) {
                    continue;
                }
                String key = executableKey(executable);
                if (key == null || executablesByKey.containsKey(key)) {
                    continue;
                }
                own.add(executable);
                executablesByKey.put(key, executable);
                keysByExecutable.put(executable, key);
                ownExecutables.computeIfAbsent(type.getQualifiedName(), name -> new ArrayList<>()).add(executable);
            }
            for (CtField<?> field : type.getFields()) {
                fieldKeys.put(field, fieldKey(field));
            }
        }
    }

    private void buildOverrides() {
        for (CtType<?> type : types) {
            String owner = type.getQualifiedName();
            for (CtExecutable<?> executable : ownExecutables.getOrDefault(owner, List.of())) {
                if (isConstructor(executable)
                        || modifiersOf(executable).contains(ModifierKind.PRIVATE)
                        || modifiersOf(executable).contains(ModifierKind.STATIC)) {
                    continue;
                }
                String signature = signature(executable);
                if (signature == null) {
                    continue;
                }
                String childKey = keysByExecutable.get(executable);
                for (String ancestor : ancestors(owner)) {
                    for (CtExecutable<?> parent : ownExecutables.getOrDefault(ancestor, List.of())) {
                        if (isConstructor(parent) || !signature.equals(signature(parent))) {
                            continue;
                        }
                        String parentKey = keysByExecutable.get(parent);
                        if (parentKey != null && !parentKey.equals(childKey)) {
                            overrides.computeIfAbsent(parentKey, key -> new ArrayList<>()).add(childKey);
                        }
                    }
                }
            }
        }
        overrides.replaceAll((key, value) -> value.stream().distinct().toList());
    }

    List<CtType<?>> types() {
        return types;
    }

    Map<String, CtType<?>> typesByFqn() {
        return typesByFqn;
    }

    Set<String> projectAnnotationNames() {
        return projectAnnotationNames;
    }

    boolean isProjectType(String fqn) {
        return fqn != null && typesByFqn.containsKey(fqn);
    }

    CtType<?> type(String fqn) {
        return typesByFqn.get(fqn);
    }

    boolean isInterface(String fqn) {
        return interfaceTypes.contains(fqn);
    }

    SourceSet sources() {
        return sources;
    }

    AnalysisRequest.Options options() {
        return options;
    }

    List<CtExecutable<?>> ownExecutables(String owner) {
        return ownExecutables.getOrDefault(owner, List.of());
    }

    Map<String, CtExecutable<?>> executablesByKey() {
        return executablesByKey;
    }

    Map<CtExecutable<?>, String> keysByExecutable() {
        return keysByExecutable;
    }

    Map<String, List<String>> overrides() {
        return overrides;
    }

    Map<CtField<?>, String> fieldKeys() {
        return fieldKeys;
    }

    Set<String> ancestors(String fqn) {
        Set<String> cached = ancestorCache.get(fqn);
        if (cached != null) {
            return cached;
        }
        Set<String> result = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>(directSupertypes.getOrDefault(fqn, List.of()));
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!result.add(current)) {
                continue;
            }
            stack.addAll(directSupertypes.getOrDefault(current, List.of()));
        }
        ancestorCache.put(fqn, result);
        return result;
    }

    Set<String> selfAndAncestors(String fqn) {
        Set<String> result = new LinkedHashSet<>();
        result.add(fqn);
        result.addAll(ancestors(fqn));
        return result;
    }

    String executableKey(CtExecutable<?> executable) {
        if (executable == null) {
            return null;
        }
        CtType<?> declaring = declaringType(executable);
        if (declaring == null || declaring.getQualifiedName() == null) {
            return null;
        }
        String name = isConstructor(executable) ? "<init>" : executable.getSimpleName();
        String descriptor = descriptor(executable);
        return "METHOD:" + declaring.getQualifiedName() + "#" + name + descriptor;
    }

    String keyOf(CtExecutable<?> executable) {
        return keysByExecutable.get(executable);
    }

    String descriptor(CtExecutable<?> executable) {
        StringBuilder builder = new StringBuilder("(");
        List<CtTypeReference<?>> parameters = parameterTypes(executable);
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(typeName(parameters.get(i)));
        }
        builder.append("):");
        if (isConstructor(executable)) {
            builder.append("void");
        } else {
            builder.append(typeName(executable.getType()));
        }
        return builder.toString();
    }

    String signature(CtExecutable<?> executable) {
        return executable.getSimpleName() + "(" + paramTypes(executable) + ")";
    }

    String paramTypes(CtExecutable<?> executable) {
        StringBuilder builder = new StringBuilder();
        List<CtTypeReference<?>> parameters = parameterTypes(executable);
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(typeName(parameters.get(i)));
        }
        return builder.toString();
    }

    private List<CtTypeReference<?>> parameterTypes(CtExecutable<?> executable) {
        List<CtTypeReference<?>> types = new ArrayList<>();
        for (var parameter : executable.getParameters()) {
            types.add(parameter.getType());
        }
        return types;
    }

    static CtType<?> declaringType(CtExecutable<?> executable) {
        return executable instanceof CtTypeMember member ? member.getDeclaringType() : null;
    }

    static Set<ModifierKind> modifiersOf(CtExecutable<?> executable) {
        return executable instanceof spoon.reflect.declaration.CtModifiable modifiable
                ? modifiable.getModifiers() : Set.of();
    }

    static boolean isConstructor(CtExecutable<?> executable) {
        return executable instanceof CtConstructor<?>;
    }

    static boolean isVarArgs(CtExecutable<?> executable) {
        List<spoon.reflect.declaration.CtParameter<?>> parameters = executable.getParameters();
        return !parameters.isEmpty() && parameters.get(parameters.size() - 1).isVarArgs();
    }

    static String typeName(CtTypeReference<?> reference) {
        if (reference == null) {
            return "?";
        }
        try {
            String name = reference.getQualifiedName();
            return name == null || name.isBlank() ? "?" : name;
        } catch (RuntimeException e) {
            return "?";
        }
    }

    String fieldKey(CtField<?> field) {
        CtType<?> owner = field.getDeclaringType();
        if (owner == null) {
            return null;
        }
        return "FIELD:" + owner.getQualifiedName() + "#" + field.getSimpleName();
    }

    String file(CtType<?> type) {
        if (type == null) {
            return "";
        }
        String file = typeFiles.get(type.getQualifiedName());
        return file == null ? "" : file;
    }

    String file(io.moheng.core.model.SymbolKey key, CtType<?> fallback) {
        if (key == null) {
            return file(fallback);
        }
        CtType<?> owner = type(key.owner());
        return file(owner != null ? owner : fallback);
    }

    int line(spoon.reflect.declaration.CtElement element) {
        if (element == null) {
            return 0;
        }
        SourcePosition position = element.getPosition();
        return position != null && position.isValidPosition() ? position.getLine() : 0;
    }

    int endLine(spoon.reflect.declaration.CtElement element) {
        if (element == null) {
            return 0;
        }
        SourcePosition position = element.getPosition();
        if (position == null || !position.isValidPosition()) {
            return line(element);
        }
        int end = position.getEndLine();
        return end < position.getLine() ? position.getLine() : end;
    }

    String typeFile(CtType<?> type) {
        return file(type);
    }

    boolean isPublicDto(CtType<?> type) {
        if (type == null || !type.getModifiers().contains(ModifierKind.PUBLIC)) {
            return false;
        }
        String pkg = packageName(type);
        for (String segment : pkg.split("\\.")) {
            String lower = segment.toLowerCase(Locale.ROOT);
            if (lower.equals("dto") || lower.equals("vo") || lower.equals("form")
                    || lower.equals("request") || lower.equals("response")) {
                return true;
            }
        }
        String simple = type.getSimpleName();
        return simple.endsWith("DTO") || simple.endsWith("Dto")
                || simple.endsWith("VO") || simple.endsWith("Vo")
                || simple.endsWith("Form") || simple.endsWith("Request")
                || simple.endsWith("Response");
    }

    static String packageName(CtType<?> type) {
        if (type == null || type.getPackage() == null) {
            return "";
        }
        String name = type.getPackage().getQualifiedName();
        return name == null ? "" : name;
    }

    static String modifiers(Set<ModifierKind> modifiers) {
        TreeSet<String> values = new TreeSet<>();
        if (modifiers != null) {
            for (ModifierKind modifier : modifiers) {
                values.add(modifier.toString());
            }
        }
        return String.join(" ", values);
    }

    static String annotationsFingerprint(Collection<CtAnnotation<?>> annotations) {
        TreeSet<String> values = new TreeSet<>();
        if (annotations != null) {
            for (CtAnnotation<?> annotation : annotations) {
                StringBuilder value = new StringBuilder(annotation.getAnnotationType().getSimpleName());
                Map<String, spoon.reflect.code.CtExpression> arguments = annotation.getValues();
                if (arguments != null && !arguments.isEmpty()) {
                    TreeSet<String> pairs = new TreeSet<>();
                    arguments.forEach((key, expression) -> pairs.add(
                            key + "=" + normalize(expression == null ? "" : expression.toString())));
                    value.append('(').append(String.join(",", pairs)).append(')');
                }
                values.add(value.toString());
            }
        }
        return String.join(";", values);
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    static boolean hasAnnotationSimple(spoon.reflect.declaration.CtElement element, String simpleName) {
        if (element == null) {
            return false;
        }
        for (CtAnnotation<?> annotation : element.getAnnotations()) {
            if (annotation.getAnnotationType().getSimpleName().equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    static CtAnnotation<?> annotationSimple(spoon.reflect.declaration.CtElement element, String simpleName) {
        if (element == null) {
            return null;
        }
        for (CtAnnotation<?> annotation : element.getAnnotations()) {
            if (annotation.getAnnotationType().getSimpleName().equals(simpleName)) {
                return annotation;
            }
        }
        return null;
    }

    private static final class SourcePaths {
        private final Map<String, String> exact = new HashMap<>();
        private final Map<String, List<String>> byBaseName = new HashMap<>();

        private SourcePaths(SourceSet sources) {
            for (String path : sources.sortedPaths()) {
                String normalized = path.replace('\\', '/');
                exact.put(normalized, normalized);
                byBaseName.computeIfAbsent(baseName(normalized), key -> new ArrayList<>()).add(normalized);
            }
            byBaseName.replaceAll((key, value) -> {
                value.sort(String::compareTo);
                return List.copyOf(value);
            });
        }

        private String resolve(spoon.reflect.declaration.CtElement element, CtType<?> fallback) {
            String raw = rawPath(element);
            if (raw != null) {
                String matched = match(raw);
                if (matched != null) {
                    return matched;
                }
            }
            return byType(fallback);
        }

        private String byType(CtType<?> type) {
            if (type == null) {
                return "";
            }
            CtType<?> top = type;
            while (top.getDeclaringType() != null) {
                top = top.getDeclaringType();
            }
            String pkg = packageName(top);
            String suffix = (pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/")
                    + top.getSimpleName() + ".java";
            List<String> matches = new ArrayList<>();
            for (String path : exact.keySet()) {
                if (path.endsWith(suffix)) {
                    matches.add(path);
                }
            }
            if (matches.size() == 1) {
                return matches.get(0);
            }
            if (!matches.isEmpty()) {
                matches.sort(String::compareTo);
                return matches.get(0);
            }
            return "";
        }

        private String match(String raw) {
            String normalized = raw.replace('\\', '/');
            while (normalized.startsWith("./")) {
                normalized = normalized.substring(2);
            }
            if (exact.containsKey(normalized)) {
                return normalized;
            }
            List<String> matches = new ArrayList<>();
            for (String path : exact.keySet()) {
                if (normalized.endsWith("/" + path) || path.endsWith("/" + normalized)) {
                    matches.add(path);
                }
            }
            if (matches.size() == 1) {
                return matches.get(0);
            }
            List<String> byName = byBaseName.get(baseName(normalized));
            if (byName != null && byName.size() == 1) {
                return byName.get(0);
            }
            return null;
        }

        private String rawPath(spoon.reflect.declaration.CtElement element) {
            if (element == null) {
                return null;
            }
            SourcePosition position = element.getPosition();
            if (position == null || !position.isValidPosition()) {
                return null;
            }
            File file = position.getFile();
            if (file == null) {
                return null;
            }
            return file.getPath();
        }

        private static String baseName(String path) {
            int slash = path.lastIndexOf('/');
            return slash < 0 ? path : path.substring(slash + 1);
        }
    }
}
