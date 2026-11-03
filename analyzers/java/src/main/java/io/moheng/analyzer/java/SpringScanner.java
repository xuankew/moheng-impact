package io.moheng.analyzer.java;

import io.moheng.core.model.ApiInfo;
import io.moheng.core.model.Warning;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class SpringScanner {

    record Result(List<ApiInfo> apis, Set<String> controllerTypes, Set<String> feignTypes,
                  Set<String> handlerKeys, Set<String> apiSourcePaths,
                  List<Warning> warnings, Map<String, Object> stats) {
        Result {
            apis = apis == null ? List.of() : List.copyOf(apis);
            controllerTypes = controllerTypes == null ? Set.of() : Set.copyOf(controllerTypes);
            feignTypes = feignTypes == null ? Set.of() : Set.copyOf(feignTypes);
            handlerKeys = handlerKeys == null ? Set.of() : Set.copyOf(handlerKeys);
            apiSourcePaths = apiSourcePaths == null ? Set.of() : Set.copyOf(apiSourcePaths);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            stats = stats == null ? Map.of() : Map.copyOf(stats);
        }
    }

    private record PathValues(List<String> values, boolean unresolved, boolean constantUsed, boolean present) {
    }

    private final SpoonContext ctx;

    SpringScanner(SpoonContext ctx) {
        this.ctx = ctx;
    }

    Result scan() {
        Set<String> feignTypes = new TreeSet<>();
        Set<String> controllerTypes = new TreeSet<>();
        for (CtType<?> type : ctx.types()) {
            if (Annotations.findOnType(type, Annotations.FEIGN_FQNS, Annotations.FEIGN_SIMPLE,
                    ctx.projectAnnotationNames()) != null) {
                feignTypes.add(type.getQualifiedName());
            } else if (Annotations.findOnType(type, Annotations.CONTROLLER_FQNS,
                    Annotations.CONTROLLER_SIMPLE, ctx.projectAnnotationNames()) != null) {
                controllerTypes.add(type.getQualifiedName());
            }
        }

        Map<String, ApiInfo> apis = new LinkedHashMap<>();
        Set<String> handlerKeys = new LinkedHashSet<>();
        Set<String> apiSourcePaths = new LinkedHashSet<>();
        List<Warning> warnings = new ArrayList<>();
        int mappingMethods = 0;
        int inheritedApis = 0;
        int duplicateApis = 0;
        int unresolvedPaths = 0;
        int constantPaths = 0;
        int fallbackAnnotations = 0;
        int feignMappingMethods = 0;

        for (String controllerFqn : controllerTypes) {
            CtType<?> controller = ctx.type(controllerFqn);
            Annotations.Match classMatch = classRequestMapping(controller);
            PathValues classPaths = classMatch == null
                    ? new PathValues(List.of(""), false, false, false)
                    : pathValues(classMatch.annotation());
            int classFallback = classMatch != null && classMatch.fallback() ? 1 : 0;
            if (classPaths.unresolved() && classPaths.values().isEmpty()) {
                unresolvedPaths++;
                warnings.add(new Warning("MAPPING_PATH_UNRESOLVED",
                        "Controller 类级 Mapping 路径无法静态确定，相关 API 已跳过",
                        Map.of("controller", controllerFqn)));
                continue;
            }
            if (classPaths.constantUsed()) {
                constantPaths++;
            }

            List<CtMethod<?>> methods = controller.getAllMethods().stream()
                    .sorted(Comparator.comparing((CtMethod<?> method) -> method.getDeclaringType().getQualifiedName())
                            .thenComparing(CtMethod::getSimpleName)
                            .thenComparing(method -> ctx.descriptor(method)))
                    .toList();
            for (CtMethod<?> method : methods) {
                boolean mappingInherited = false;
                Annotations.Match mapping = Annotations.find(method, Annotations.MAPPING_FQNS,
                        Annotations.MAPPING_SIMPLE, ctx.projectAnnotationNames(),
                        SpoonContext.packageName(method.getDeclaringType()));
                if (mapping == null) {
                    mapping = inheritedMethodMapping(method);
                    mappingInherited = mapping != null;
                }
                if (mapping == null) {
                    continue;
                }
                mappingMethods++;
                String handlerKey = ctx.executableKey(method);
                if (handlerKey != null) {
                    handlerKeys.add(handlerKey);
                }
                PathValues methodPaths = pathValues(mapping.annotation());
                if (methodPaths.constantUsed()) {
                    constantPaths++;
                }
                if (methodPaths.unresolved()) {
                    unresolvedPaths++;
                    if (methodPaths.values().isEmpty()) {
                        warnings.add(new Warning("MAPPING_PATH_UNRESOLVED",
                                "方法级 Mapping 路径无法静态确定，该映射已跳过",
                                Map.of("controller", controllerFqn, "method", method.getSimpleName())));
                        continue;
                    }
                }
                List<String> verbs = verbs(mapping.annotation());
                Map<String, List<String>> conditions = conditions(mapping.annotation());
                boolean fallback = classFallback > 0 || mapping.fallback();
                for (String classPath : classPaths.values()) {
                    for (String methodPath : methodPaths.values()) {
                        String path = joinPath(ctx.options().apiPrefix(), classPath, methodPath);
                        for (String verb : verbs) {
                            String key = ApiInfo.apiKey(verb, path);
                            ApiInfo info = new ApiInfo(
                                    ApiInfo.apiId(key), key, verb, path, controllerFqn,
                                    handlerKey == null ? "" : handlerKey,
                                    method.getDeclaringType().getQualifiedName(),
                                    method.getSimpleName(), ctx.file(method.getDeclaringType()),
                                    ctx.line(method), classPaths.values(), methodPaths.values(),
                                    conditions, fallback ? "SIMPLE_NAME_FALLBACK" : "FQN",
                                    mappingInherited
                                            || !method.getDeclaringType().getQualifiedName().equals(controllerFqn));
                            ApiInfo existing = apis.get(key);
                            if (existing == null) {
                                apis.put(key, info);
                                apiSourcePaths.add(info.file());
                                if (info.inherited() || mappingInherited) {
                                    inheritedApis++;
                                }
                                if (fallback) {
                                    fallbackAnnotations++;
                                }
                            } else if (!existing.handlerKey().equals(info.handlerKey())
                                    || !existing.conditions().equals(info.conditions())) {
                                duplicateApis++;
                            }
                        }
                    }
                }
            }
        }

        for (String feignFqn : feignTypes) {
            CtType<?> feign = ctx.type(feignFqn);
            if (feign == null) {
                continue;
            }
            for (CtMethod<?> method : feign.getAllMethods()) {
                if (Annotations.find(method, Annotations.MAPPING_FQNS, Annotations.MAPPING_SIMPLE,
                        ctx.projectAnnotationNames(), SpoonContext.packageName(feign)) != null) {
                    feignMappingMethods++;
                }
            }
        }

        if (duplicateApis > 0) {
            warnings.add(new Warning("DUPLICATE_API",
                    "存在相同 HTTP 方法与路径但 handler/conditions 不同的映射，已保留首个",
                    Map.of("count", duplicateApis)));
        }

        List<ApiInfo> apiList = new ArrayList<>(apis.values());
        apiList.sort(Comparator.comparing(ApiInfo::key).thenComparing(ApiInfo::handlerKey));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("controllers", controllerTypes.size());
        stats.put("feignClients", feignTypes.size());
        stats.put("feignMappingMethods", feignMappingMethods);
        stats.put("mappingMethods", mappingMethods);
        stats.put("apis", apiList.size());
        stats.put("inheritedApis", inheritedApis);
        stats.put("duplicateApis", duplicateApis);
        stats.put("unresolvedMappingPaths", unresolvedPaths);
        stats.put("constantMappingPaths", constantPaths);
        stats.put("simpleNameFallbackApis", fallbackAnnotations);
        return new Result(apiList, controllerTypes, feignTypes, handlerKeys, apiSourcePaths,
                warnings, stats);
    }

    private Annotations.Match classRequestMapping(CtType<?> type) {
        Annotations.Match own = Annotations.findOnType(type, Annotations.REQUEST_MAPPING_FQNS,
                Annotations.REQUEST_MAPPING_SIMPLE, ctx.projectAnnotationNames());
        if (own != null) {
            return own;
        }
        for (String ancestor : ctx.ancestors(type.getQualifiedName())) {
            CtType<?> parent = ctx.type(ancestor);
            if (parent == null) {
                continue;
            }
            Annotations.Match match = Annotations.findOnType(parent, Annotations.REQUEST_MAPPING_FQNS,
                    Annotations.REQUEST_MAPPING_SIMPLE, ctx.projectAnnotationNames());
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private Annotations.Match inheritedMethodMapping(CtMethod<?> method) {
        CtType<?> owner = method.getDeclaringType();
        String signature = ctx.signature(method);
        for (String ancestor : ctx.ancestors(owner.getQualifiedName())) {
            CtType<?> parent = ctx.type(ancestor);
            if (parent == null) {
                continue;
            }
            for (var executable : ctx.ownExecutables(ancestor)) {
                if (!(executable instanceof CtMethod<?> candidate)
                        || !signature.equals(ctx.signature(candidate))) {
                    continue;
                }
                Annotations.Match match = Annotations.find(candidate, Annotations.MAPPING_FQNS,
                        Annotations.MAPPING_SIMPLE, ctx.projectAnnotationNames(),
                        SpoonContext.packageName(parent));
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private PathValues pathValues(CtAnnotation<?> annotation) {
        Map<String, CtExpression> values = annotation.getValues();
        List<String> paths = new ArrayList<>();
        boolean[] unresolved = {false};
        boolean[] constant = {false};
        boolean present = false;
        for (String key : List.of("value", "path")) {
            CtExpression<?> expression = values.get(key);
            if (expression == null) {
                continue;
            }
            present = true;
            int before = paths.size();
            collectStrings(expression, paths, unresolved, constant);
            if (paths.size() == before) {
                unresolved[0] = true;
            }
        }
        if (!present) {
            return new PathValues(List.of(""), false, false, false);
        }
        return new PathValues(List.copyOf(new LinkedHashSet<>(paths)), unresolved[0], constant[0], true);
    }

    private void collectStrings(CtExpression<?> expression, List<String> result,
                                boolean[] unresolved, boolean[] constant) {
        if (expression == null) {
            unresolved[0] = true;
            return;
        }
        if (expression instanceof CtLiteral<?> literal) {
            Object value = literal.getValue();
            if (value instanceof String string) {
                result.add(string);
            } else {
                unresolved[0] = true;
            }
            return;
        }
        if (expression instanceof CtNewArray<?> array) {
            for (CtExpression<?> element : array.getElements()) {
                collectStrings(element, result, unresolved, constant);
            }
            return;
        }
        if (expression instanceof CtFieldRead<?> fieldRead) {
            constant[0] = true;
            try {
                if (fieldRead.getVariable().getDeclaration() instanceof CtField<?> field
                        && field.getDefaultExpression() != null) {
                    collectStrings(field.getDefaultExpression(), result, unresolved, constant);
                    return;
                }
            } catch (Exception ignored) {
            }
            unresolved[0] = true;
            return;
        }
        if (expression instanceof CtBinaryOperator<?> binary) {
            constant[0] = true;
            if (binary.getKind() != BinaryOperatorKind.PLUS) {
                unresolved[0] = true;
                return;
            }
            List<String> left = new ArrayList<>();
            List<String> right = new ArrayList<>();
            collectStrings(binary.getLeftHandOperand(), left, unresolved, constant);
            collectStrings(binary.getRightHandOperand(), right, unresolved, constant);
            if (left.size() == 1 && right.size() == 1) {
                result.add(left.get(0) + right.get(0));
            } else {
                result.addAll(left);
                result.addAll(right);
            }
            return;
        }
        unresolved[0] = true;
    }

    private List<String> verbs(CtAnnotation<?> annotation) {
        String simple = annotation.getAnnotationType().getSimpleName();
        switch (simple) {
            case "GetMapping":
                return List.of("GET");
            case "PostMapping":
                return List.of("POST");
            case "PutMapping":
                return List.of("PUT");
            case "DeleteMapping":
                return List.of("DELETE");
            case "PatchMapping":
                return List.of("PATCH");
            default:
                break;
        }
        Set<String> verbs = new TreeSet<>();
        CtExpression<?> expression = annotation.getValues().get("method");
        collectVerbs(expression, verbs);
        return verbs.isEmpty() ? List.of("ANY") : List.copyOf(verbs);
    }

    private void collectVerbs(CtExpression<?> expression, Set<String> verbs) {
        if (expression == null) {
            return;
        }
        if (expression instanceof CtNewArray<?> array) {
            for (CtExpression<?> element : array.getElements()) {
                collectVerbs(element, verbs);
            }
            return;
        }
        if (expression instanceof CtFieldRead<?> fieldRead) {
            String name = fieldRead.getVariable().getSimpleName();
            if (name != null && !name.isBlank()) {
                verbs.add(name.toUpperCase());
            }
        }
    }

    private Map<String, List<String>> conditions(CtAnnotation<?> annotation) {
        Map<String, List<String>> conditions = new LinkedHashMap<>();
        for (String key : List.of("params", "consumes", "produces", "headers")) {
            CtExpression<?> expression = annotation.getValues().get(key);
            if (expression == null) {
                continue;
            }
            List<String> values = new ArrayList<>();
            boolean[] unresolved = {false};
            boolean[] constant = {false};
            collectStrings(expression, values, unresolved, constant);
            if (!values.isEmpty()) {
                conditions.put(key, List.copyOf(new LinkedHashSet<>(values)));
            }
        }
        return conditions;
    }

    private String joinPath(String prefix, String classPath, String methodPath) {
        StringBuilder builder = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            builder.append(prefix.trim());
        }
        if (classPath != null && !classPath.isBlank()) {
            builder.append('/').append(classPath);
        }
        if (methodPath != null && !methodPath.isBlank()) {
            builder.append('/').append(methodPath);
        }
        String path = builder.toString().replaceAll("/+", "/");
        if (path.isBlank()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
