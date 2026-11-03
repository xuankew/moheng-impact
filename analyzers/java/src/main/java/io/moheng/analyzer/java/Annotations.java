package io.moheng.analyzer.java;

import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.util.Set;

final class Annotations {

    static final Set<String> CONTROLLER_FQNS = Set.of(
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController");

    static final Set<String> CONTROLLER_SIMPLE = Set.of("Controller", "RestController");

    static final Set<String> MAPPING_FQNS = Set.of(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping");

    static final Set<String> MAPPING_SIMPLE = Set.of(
            "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping");

    static final Set<String> REQUEST_MAPPING_FQNS = Set.of(
            "org.springframework.web.bind.annotation.RequestMapping");

    static final Set<String> REQUEST_MAPPING_SIMPLE = Set.of("RequestMapping");

    static final Set<String> FEIGN_FQNS = Set.of(
            "org.springframework.cloud.openfeign.FeignClient",
            "org.springframework.cloud.netflix.feign.FeignClient");

    static final Set<String> FEIGN_SIMPLE = Set.of("FeignClient");

    private Annotations() {
    }

    record Match(CtAnnotation<?> annotation, boolean fallback) {
    }

    static Match find(CtElement element, Set<String> fqns, Set<String> simpleNames,
                      Set<String> projectAnnotationNames, String elementPackage) {
        if (element == null) {
            return null;
        }
        for (CtAnnotation<?> annotation : element.getAnnotations()) {
            CtTypeReference<?> reference = annotation.getAnnotationType();
            if (reference == null) {
                continue;
            }
            String qualified = reference.getQualifiedName();
            String simple = reference.getSimpleName();
            if (qualified != null && fqns.contains(qualified)) {
                return new Match(annotation, false);
            }
            if (simple == null || !simpleNames.contains(simple)) {
                continue;
            }
            if (projectAnnotationNames.contains(simple)) {
                continue;
            }
            if (qualified == null || qualified.isBlank() || qualified.indexOf('.') < 0) {
                return new Match(annotation, true);
            }
            String pkg = qualified.substring(0, qualified.lastIndexOf('.'));
            if (pkg.equals(elementPackage)) {
                return new Match(annotation, true);
            }
        }
        return null;
    }

    static Match findOnType(CtType<?> type, Set<String> fqns, Set<String> simpleNames,
                            Set<String> projectAnnotationNames) {
        return find(type, fqns, simpleNames, projectAnnotationNames, SpoonContext.packageName(type));
    }
}
