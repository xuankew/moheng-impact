package io.moheng.analyzer.java;

import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;

import java.util.HashMap;
import java.util.Map;

final class LombokIndex {

    record Accessor(String fieldKey, boolean getter, boolean setter) {
    }

    private final Map<String, Map<String, Accessor>> accessors = new HashMap<>();
    private final Map<String, Boolean> builders = new HashMap<>();

    LombokIndex(SpoonContext ctx) {
        for (CtType<?> type : ctx.types()) {
            boolean data = SpoonContext.hasAnnotationSimple(type, "Data");
            boolean value = SpoonContext.hasAnnotationSimple(type, "Value");
            boolean classGetter = data || value || SpoonContext.hasAnnotationSimple(type, "Getter");
            boolean classSetter = data || SpoonContext.hasAnnotationSimple(type, "Setter");
            boolean fluent = fluent(type);
            builders.put(type.getQualifiedName(), SpoonContext.hasAnnotationSimple(type, "Builder"));

            for (CtField<?> field : type.getFields()) {
                if (field.getModifiers().contains(ModifierKind.STATIC)) {
                    continue;
                }
                String name = field.getSimpleName();
                boolean getter = classGetter || SpoonContext.hasAnnotationSimple(field, "Getter");
                boolean setter = (classSetter || SpoonContext.hasAnnotationSimple(field, "Setter"))
                        && !field.getModifiers().contains(ModifierKind.FINAL)
                        && !value;
                boolean fieldFluent = fluent(field) || fluent;
                String fieldKey = ctx.fieldKeys().get(field);
                if (fieldKey == null) {
                    fieldKey = ctx.fieldKey(field);
                }
                if (fieldKey == null) {
                    continue;
                }
                Map<String, Accessor> byName = accessors.computeIfAbsent(
                        type.getQualifiedName(), key -> new HashMap<>());
                if (getter) {
                    String getterName = fieldFluent ? name : getterName(name, isBoolean(field));
                    if (getterName != null && type.getMethodsByName(getterName).isEmpty()) {
                        byName.putIfAbsent(getterName, new Accessor(fieldKey, true, fieldFluent && setter));
                    }
                }
                if (setter) {
                    String setterName = fieldFluent ? name : setterName(name);
                    if (setterName != null && type.getMethodsByName(setterName).isEmpty()) {
                        Accessor existing = byName.get(setterName);
                        if (existing != null) {
                            byName.put(setterName, new Accessor(fieldKey, existing.getter(), true));
                        } else {
                            byName.putIfAbsent(setterName, new Accessor(fieldKey, false, true));
                        }
                    }
                }
            }
        }
    }

    Accessor find(String receiverFqn, String methodName, int arity, SpoonContext ctx) {
        if (receiverFqn == null || methodName == null) {
            return null;
        }
        for (String owner : ctx.selfAndAncestors(receiverFqn)) {
            Map<String, Accessor> byName = accessors.get(owner);
            if (byName == null) {
                continue;
            }
            Accessor accessor = byName.get(methodName);
            if (accessor == null) {
                continue;
            }
            if (arity == 0 && accessor.getter()) {
                return accessor;
            }
            if (arity == 1 && accessor.setter()) {
                return accessor;
            }
        }
        return null;
    }

    boolean hasBuilder(String fqn) {
        return builders.getOrDefault(fqn, false);
    }

    private boolean fluent(CtType<?> type) {
        return fluentAnnotation(type);
    }

    private boolean fluent(CtField<?> field) {
        return fluentAnnotation(field);
    }

    private boolean fluentAnnotation(spoon.reflect.declaration.CtElement element) {
        CtAnnotation<?> annotation = SpoonContext.annotationSimple(element, "Accessors");
        if (annotation == null) {
            return false;
        }
        var expression = annotation.getValues().get("fluent");
        return expression instanceof CtLiteral<?> literal && Boolean.TRUE.equals(literal.getValue());
    }

    private boolean isBoolean(CtField<?> field) {
        return field.getType() != null && "boolean".equals(field.getType().getQualifiedName());
    }

    private String getterName(String fieldName, boolean isBoolean) {
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        String prefix = isBoolean ? "is" : "get";
        return prefix + capitalize(fieldName);
    }

    private String setterName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        return "set" + capitalize(fieldName);
    }

    private String capitalize(String name) {
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
