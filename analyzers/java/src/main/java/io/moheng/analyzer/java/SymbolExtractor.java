package io.moheng.analyzer.java;

import io.moheng.core.model.SymbolInfo;
import io.moheng.core.model.SymbolKey;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

final class SymbolExtractor {

    record Result(Map<String, SymbolInfo> symbols, Map<String, CtExecutable<?>> executables) {
        Result {
            symbols = symbols == null ? Map.of() : Map.copyOf(symbols);
            executables = executables == null ? Map.of() : Map.copyOf(executables);
        }
    }

    private SymbolExtractor() {
    }

    static Result extract(SpoonContext ctx, SpringScanner.Result spring) {
        Map<String, SymbolInfo> symbols = new LinkedHashMap<>();
        for (CtType<?> type : ctx.types()) {
            String fqn = type.getQualifiedName();
            boolean dto = ctx.isPublicDto(type);
            boolean controller = spring.controllerTypes().contains(fqn);
            String file = ctx.file(type);
            SymbolInfo classInfo = new SymbolInfo(
                    SymbolKey.type(fqn), file, ctx.line(type), ctx.endLine(type),
                    SpoonContext.modifiers(type.getModifiers()),
                    SpoonContext.annotationsFingerprint(type.getAnnotations()),
                    "",
                    classStructure(type),
                    dto, controller, false, type.isInterface(),
                    type.getModifiers().contains(ModifierKind.ABSTRACT));
            symbols.put(classInfo.key().id(), classInfo);

            for (CtField<?> field : type.getFields()) {
                if (field.isImplicit()) {
                    continue;
                }
                String key = ctx.fieldKeys().get(field);
                if (key == null) {
                    key = ctx.fieldKey(field);
                }
                if (key == null) {
                    continue;
                }
                SymbolKey symbolKey = new SymbolKey(SymbolKey.Kind.FIELD, fqn, field.getSimpleName(), "");
                if (!symbolKey.id().equals(key)) {
                    continue;
                }
                SymbolInfo fieldInfo = new SymbolInfo(
                        symbolKey, file, ctx.line(field), ctx.endLine(field),
                        SpoonContext.modifiers(field.getModifiers()),
                        SpoonContext.annotationsFingerprint(field.getAnnotations()),
                        "",
                        fieldStructure(field),
                        dto && !field.getModifiers().contains(ModifierKind.STATIC),
                        controller, false, false, false);
                symbols.put(fieldInfo.key().id(), fieldInfo);
            }

            for (CtExecutable<?> executable : ctx.ownExecutables(fqn)) {
                String key = ctx.keysByExecutable().get(executable);
                if (key == null) {
                    key = ctx.executableKey(executable);
                }
                if (key == null) {
                    continue;
                }
                String name = SpoonContext.isConstructor(executable) ? "<init>" : executable.getSimpleName();
                SymbolKey symbolKey = new SymbolKey(SymbolKey.Kind.METHOD, fqn, name,
                        ctx.descriptor(executable));
                SymbolInfo methodInfo = new SymbolInfo(
                        symbolKey, file, ctx.line(executable), ctx.endLine(executable),
                        SpoonContext.modifiers(SpoonContext.modifiersOf(executable)),
                        SpoonContext.annotationsFingerprint(executable.getAnnotations()),
                        bodyFingerprint(executable),
                        methodStructure(executable),
                        dto && SpoonContext.modifiersOf(executable).contains(ModifierKind.PUBLIC),
                        controller, spring.handlerKeys().contains(key),
                        false, false);
                symbols.put(methodInfo.key().id(), methodInfo);
            }
        }
        return new Result(symbols, ctx.executablesByKey());
    }

    private static String classStructure(CtType<?> type) {
        TreeSet<String> interfaces = new TreeSet<>();
        for (CtTypeReference<?> reference : type.getSuperInterfaces()) {
            interfaces.add(SpoonContext.typeName(reference));
        }
        CtTypeReference<?> superclass = type.getSuperclass();
        return SpoonContext.modifiers(type.getModifiers())
                + "|" + (superclass == null ? "" : SpoonContext.typeName(superclass))
                + "|" + String.join(",", interfaces);
    }

    private static String fieldStructure(CtField<?> field) {
        String init = field.getDefaultExpression() == null
                ? "" : SpoonContext.normalize(field.getDefaultExpression().toString());
        return SpoonContext.modifiers(field.getModifiers())
                + "|" + SpoonContext.typeName(field.getType())
                + "|" + init;
    }

    private static String methodStructure(CtExecutable<?> executable) {
        List<String> parameters = new ArrayList<>();
        for (CtParameter<?> parameter : executable.getParameters()) {
            parameters.add(SpoonContext.typeName(parameter.getType())
                    + " " + parameter.getSimpleName()
                    + " " + SpoonContext.annotationsFingerprint(parameter.getAnnotations()));
        }
        TreeSet<String> thrown = new TreeSet<>();
        for (CtTypeReference<? extends Throwable> reference : executable.getThrownTypes()) {
            thrown.add(SpoonContext.typeName(reference));
        }
        String returnType;
        if (SpoonContext.isConstructor(executable)) {
            returnType = "void";
        } else {
            returnType = SpoonContext.typeName(executable.getType());
        }
        return SpoonContext.modifiers(SpoonContext.modifiersOf(executable))
                + "|" + returnType
                + "|" + String.join(",", parameters)
                + "|" + String.join(",", thrown)
                + "|" + SpoonContext.isVarArgs(executable);
    }

    private static String bodyFingerprint(CtExecutable<?> executable) {
        if (executable instanceof CtMethod<?> method) {
            return method.getBody() == null ? "" : SpoonContext.normalize(method.getBody().toString());
        }
        if (executable instanceof CtConstructor<?> constructor) {
            return constructor.getBody() == null
                    ? "" : SpoonContext.normalize(constructor.getBody().toString());
        }
        return "";
    }

    static List<CtExecutable<?>> sorted(Map<String, CtExecutable<?>> executables) {
        List<CtExecutable<?>> result = new ArrayList<>(executables.values());
        result.sort(Comparator.comparing(executable -> {
            spoon.reflect.declaration.CtType<?> declaring = SpoonContext.declaringType(executable);
            return declaring == null ? "" : declaring.getQualifiedName();
        }));
        return result;
    }
}
