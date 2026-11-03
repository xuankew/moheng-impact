package io.moheng.analyzer.java;

import io.moheng.core.model.ApiInfo;
import io.moheng.core.model.Edge;
import io.moheng.core.model.Evidence;
import io.moheng.core.model.ImpactGraph;
import io.moheng.core.model.Node;
import io.moheng.core.model.SymbolInfo;
import io.moheng.core.model.SymbolKey;
import io.moheng.core.model.Warning;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CallGraphBuilder {

    record Result(ImpactGraph graph, List<Warning> warnings, Map<String, Object> stats) {
        Result {
            graph = graph == null ? ImpactGraph.empty() : graph;
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            stats = stats == null ? Map.of() : Map.copyOf(stats);
        }
    }

    private final SpoonContext ctx;
    private final SymbolExtractor.Result symbols;
    private final SpringScanner.Result spring;
    private final LombokIndex lombok;
    private final ImpactGraph.Builder graph = ImpactGraph.builder();
    private final Map<String, Object> stats = new LinkedHashMap<>();
    private final List<Warning> warnings = new ArrayList<>();

    private int invocations;
    private int constructorCalls;
    private int resolvedProject;
    private int dispatchEdges;
    private int lombokResolved;
    private int heuristicResolved;
    private int ambiguous;
    private int externalBoundary;
    private int unresolvedProject;
    private int fieldReads;
    private int fieldWrites;
    private int builderCalls;
    private int typeUsageEdges;
    private int highFanout;
    private int missingHandlers;
    private final Set<String> highFanoutOwners = new LinkedHashSet<>();

    CallGraphBuilder(SpoonContext ctx, SymbolExtractor.Result symbols,
                     SpringScanner.Result spring, LombokIndex lombok) {
        this.ctx = ctx;
        this.symbols = symbols;
        this.spring = spring;
        this.lombok = lombok;
    }

    Result build() {
        addSymbolNodes();
        addInheritanceEdges();
        addOverrideEdges();
        addTypeUsageEdges();
        for (CtExecutable<?> executable : symbols.executables().values()) {
            scanExecutable(executable);
        }
        addApiNodes();
        addStats();
        return new Result(graph.build(), warnings, stats);
    }

    private void addSymbolNodes() {
        for (SymbolInfo symbol : symbols.symbols().values()) {
            String owner = symbol.key().owner();
            CtType<?> type = ctx.type(owner);
            switch (symbol.key().kind()) {
                case CLASS -> graph.node(new Node(symbol.key().id(), Node.Kind.CLASS,
                        type == null ? simpleName(owner) : type.getSimpleName(),
                        symbol.file(), symbol.line(), false, Map.of()));
                case METHOD -> {
                    String name = methodName(symbol.key());
                    graph.node(new Node(symbol.key().id(), Node.Kind.METHOD, name,
                            symbol.file(), symbol.line(), false, Map.of()));
                    addDefines(owner, symbol.key().id(), symbol.file(), symbol.line(), name);
                }
                case FIELD -> {
                    graph.node(new Node(symbol.key().id(), Node.Kind.FIELD, symbol.key().name(),
                            symbol.file(), symbol.line(), false, Map.of()));
                    addDefines(owner, symbol.key().id(), symbol.file(), symbol.line(), symbol.key().name());
                }
            }
        }
    }

    private void addDefines(String owner, String memberKey, String file, int line, String name) {
        String classKey = SymbolKey.type(owner).id();
        if (graph.nodes().containsKey(classKey)) {
            graph.edge(new Edge(classKey, memberKey, Edge.Type.DEFINES, 1.0, "defines",
                    new Evidence("SPOON", file, line, "", "FQN", name)));
        }
    }

    private void addInheritanceEdges() {
        for (CtType<?> type : ctx.types()) {
            String owner = type.getQualifiedName();
            String classKey = SymbolKey.type(owner).id();
            if (!graph.nodes().containsKey(classKey)) {
                continue;
            }
            String file = ctx.file(type);
            int line = ctx.line(type);
            CtTypeReference<?> superclass = type.getSuperclass();
            if (superclass != null) {
                String parent = superclass.getQualifiedName();
                if (ctx.isProjectType(parent)) {
                    graph.edge(new Edge(classKey, SymbolKey.type(parent).id(), Edge.Type.EXTENDS,
                            1.0, "extends", new Evidence("SPOON", file, line, parent, "FQN", "")));
                }
            }
            for (CtTypeReference<?> reference : type.getSuperInterfaces()) {
                String parent = reference.getQualifiedName();
                if (ctx.isProjectType(parent)) {
                    graph.edge(new Edge(classKey, SymbolKey.type(parent).id(), Edge.Type.IMPLEMENTS,
                            1.0, "implements", new Evidence("SPOON", file, line, parent, "FQN", "")));
                }
            }
        }
    }

    private void addOverrideEdges() {
        for (Map.Entry<String, List<String>> entry : ctx.overrides().entrySet()) {
            String parentKey = entry.getKey();
            SymbolInfo parent = symbols.symbols().get(parentKey);
            for (String childKey : entry.getValue()) {
                SymbolInfo child = symbols.symbols().get(childKey);
                if (child == null || parent == null) {
                    continue;
                }
                graph.edge(new Edge(childKey, parentKey, Edge.Type.OVERRIDES, 1.0, "override",
                        new Evidence("SPOON", child.file(), child.line(), "", "FQN", "")));
            }
        }
    }

    private void addTypeUsageEdges() {
        for (Map.Entry<String, CtExecutable<?>> entry : symbols.executables().entrySet()) {
            String callerKey = entry.getKey();
            if (!graph.nodes().containsKey(callerKey)) {
                continue;
            }
            CtExecutable<?> executable = entry.getValue();
            Set<String> used = new LinkedHashSet<>();
            collectSignatureTypes(executable.getType(), used, 0);
            for (CtParameter<?> parameter : executable.getParameters()) {
                collectSignatureTypes(parameter.getType(), used, 0);
            }
            for (CtTypeReference<? extends Throwable> thrown : executable.getThrownTypes()) {
                collectSignatureTypes(thrown, used, 0);
            }
            if (used.isEmpty()) {
                continue;
            }
            String owner = ownerOf(callerKey);
            CtType<?> declaring = SpoonContext.declaringType(executable);
            String file = declaring == null ? "" : ctx.file(declaring);
            int line = declaring == null ? 0 : ctx.line(declaring);
            for (String target : used) {
                if (target.equals(owner)) {
                    continue;
                }
                String classKey = SymbolKey.type(target).id();
                if (!graph.nodes().containsKey(classKey)) {
                    continue;
                }
                graph.edge(new Edge(callerKey, classKey, Edge.Type.USES_TYPE, 1.0,
                        "signature uses " + target,
                        new Evidence("SPOON", file, line, target, "FQN", "signature")));
                typeUsageEdges++;
            }
        }
    }

    private void collectSignatureTypes(CtTypeReference<?> reference, Set<String> used, int depth) {
        if (reference == null || depth > 3) {
            return;
        }
        if (reference instanceof CtArrayTypeReference<?> array) {
            collectSignatureTypes(array.getComponentType(), used, depth + 1);
            return;
        }
        if (reference instanceof CtTypeParameterReference || reference instanceof CtWildcardReference) {
            return;
        }
        try {
            String fqn = reference.getQualifiedName();
            if (fqn != null && ctx.isProjectType(fqn)) {
                used.add(fqn);
            }
            for (CtTypeReference<?> argument : reference.getActualTypeArguments()) {
                collectSignatureTypes(argument, used, depth + 1);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private String ownerOf(String executableKey) {
        int colon = executableKey.indexOf(':');
        int hash = executableKey.indexOf('#');
        return colon < 0 || hash < colon ? "" : executableKey.substring(colon + 1, hash);
    }

    private void addApiNodes() {
        for (ApiInfo api : spring.apis()) {
            if (api.id() == null || api.key() == null) {
                continue;
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("apiId", api.id());
            meta.put("verb", api.verb());
            meta.put("path", api.path());
            meta.put("controller", api.controller());
            meta.put("handler", api.handlerKey());
            meta.put("annotationResolution", api.annotationResolution());
            graph.node(new Node(api.key(), Node.Kind.API, api.verb() + " " + api.path(),
                    api.file(), api.line(), false, meta));
            if (api.handlerKey() == null || api.handlerKey().isBlank()
                    || !graph.nodes().containsKey(api.handlerKey())) {
                missingHandlers++;
                continue;
            }
            graph.edge(new Edge(api.handlerKey(), api.key(), Edge.Type.EXPOSES_API, 1.0, "exposes",
                    new Evidence("SPOON", api.file(), api.line(), api.controller(),
                            api.annotationResolution(), "")));
        }
    }

    private void scanExecutable(CtExecutable<?> executable) {
        String callerKey = ctx.executableKey(executable);
        if (callerKey == null || !graph.nodes().containsKey(callerKey)) {
            return;
        }
        String callerFile = ctx.file(SpoonContext.declaringType(executable));
        scanFieldAccess(executable, callerKey, callerFile, false);
        scanFieldAccess(executable, callerKey, callerFile, true);
        for (CtInvocation<?> invocation : executable.getElements(new TypeFilter<>(CtInvocation.class))) {
            invocations++;
            scanInvocation(executable, callerKey, callerFile, invocation);
        }
        for (CtConstructorCall<?> call : executable.getElements(new TypeFilter<>(CtConstructorCall.class))) {
            constructorCalls++;
            scanConstructorCall(callerKey, callerFile, call);
        }
    }

    private void scanFieldAccess(CtExecutable<?> executable, String callerKey, String callerFile,
                                 boolean write) {
        if (write) {
            for (CtFieldWrite<?> access : executable.getElements(new TypeFilter<>(CtFieldWrite.class))) {
                addFieldAccess(callerKey, callerFile, access, true);
            }
        } else {
            for (CtFieldRead<?> access : executable.getElements(new TypeFilter<>(CtFieldRead.class))) {
                addFieldAccess(callerKey, callerFile, access, false);
            }
        }
    }

    private void addFieldAccess(String callerKey, String callerFile,
                                spoon.reflect.code.CtFieldAccess<?> access, boolean write) {
        try {
            CtVariableReference<?> variable = access.getVariable();
            if (!(variable.getDeclaration() instanceof CtField<?> field)) {
                return;
            }
            String fieldKey = ctx.fieldKeys().get(field);
            if (fieldKey == null) {
                fieldKey = ctx.fieldKey(field);
            }
            if (fieldKey == null || !graph.nodes().containsKey(fieldKey)) {
                return;
            }
            if (write) {
                fieldWrites++;
            } else {
                fieldReads++;
            }
            int line = ctx.line(access);
            graph.edge(new Edge(callerKey, fieldKey, Edge.Type.REFERENCES, 1.0,
                    write ? "field-write" : "field-read",
                    new Evidence("SPOON", callerFile, line, "", "FQN", "")));
        } catch (RuntimeException ignored) {
        }
    }

    private void scanInvocation(CtExecutable<?> executable, String callerKey, String callerFile,
                                CtInvocation<?> invocation) {
        int line = ctx.line(invocation);
        String receiver = receiverType(executable, invocation);
        CtExecutableReference<?> reference = invocation.getExecutable();
        CtExecutable<?> declaration = declaration(reference);
        if (declaration != null) {
            String calleeKey = ctx.executableKey(declaration);
            if (calleeKey != null && ctx.executablesByKey().containsKey(calleeKey)
                    && graph.nodes().containsKey(calleeKey)) {
                resolvedProject++;
                graph.edge(new Edge(callerKey, calleeKey, Edge.Type.CALLS, 1.0, "direct",
                        new Evidence("SPOON", callerFile, line, receiver, "FQN", "")));
                addDispatch(callerKey, declaration, callerFile, line, receiver);
                return;
            }
            externalBoundary++;
            return;
        }

        String name = reference.getSimpleName();
        int arity = invocation.getArguments().size();
        String receiverFqn = projectReceiver(reference, receiver);
        LombokIndex.Accessor accessor = lombok.find(receiverFqn, name, arity, ctx);
        if (accessor != null && graph.nodes().containsKey(accessor.fieldKey())) {
            lombokResolved++;
            graph.edge(new Edge(callerKey, accessor.fieldKey(), Edge.Type.REFERENCES, 0.95,
                    accessor.getter() && arity == 0 ? "lombok-getter" : "lombok-setter",
                    new Evidence("SPOON", callerFile, line, receiver, "FQN", "lombok")));
            return;
        }
        if (receiverFqn != null && name != null && name.equals("builder") && arity == 0
                && lombok.hasBuilder(receiverFqn)) {
            builderCalls++;
            graph.edge(new Edge(callerKey, SymbolKey.type(receiverFqn).id(), Edge.Type.REFERENCES,
                    0.8, "lombok-builder",
                    new Evidence("SPOON", callerFile, line, receiver, "FQN", "lombok")));
            return;
        }
        if (receiverFqn != null) {
            List<String> candidates = heuristicCandidates(receiverFqn, name, arity);
            if (!candidates.isEmpty() && candidates.size() <= 3) {
                heuristicResolved++;
                for (String candidate : candidates) {
                    graph.edge(new Edge(callerKey, candidate, Edge.Type.CALLS, 0.6, "name-arity",
                            new Evidence("SPOON", callerFile, line, receiver, "FQN", "")));
                }
                return;
            }
            if (candidates.size() > 3) {
                ambiguous++;
                return;
            }
            unresolvedProject++;
            return;
        }
        externalBoundary++;
    }

    private void scanConstructorCall(String callerKey, String callerFile, CtConstructorCall<?> call) {
        int line = ctx.line(call);
        CtExecutableReference<?> reference = call.getExecutable();
        CtExecutable<?> declaration = declaration(reference);
        if (declaration == null) {
            externalBoundary++;
            return;
        }
        String calleeKey = ctx.executableKey(declaration);
        if (calleeKey == null || !ctx.executablesByKey().containsKey(calleeKey)
                || !graph.nodes().containsKey(calleeKey)) {
            externalBoundary++;
            return;
        }
        resolvedProject++;
        String receiver = reference.getDeclaringType() == null
                ? "" : reference.getDeclaringType().getQualifiedName();
        graph.edge(new Edge(callerKey, calleeKey, Edge.Type.CALLS, 1.0, "constructor",
                new Evidence("SPOON", callerFile, line, receiver, "FQN", "")));
    }

    private void addDispatch(String callerKey, CtExecutable<?> declaration,
                             String file, int line, String receiver) {
        String calleeKey = ctx.executableKey(declaration);
        if (calleeKey == null) {
            return;
        }
        List<String> implementations = ctx.overrides().get(calleeKey);
        if (implementations == null || implementations.isEmpty()) {
            return;
        }
        CtType<?> owner = SpoonContext.declaringType(declaration);
        if (owner == null) {
            return;
        }
        boolean interfaceOwner = ctx.isInterface(owner.getQualifiedName());
        int limit = interfaceOwner ? 5 : 20;
        if (implementations.size() > limit) {
            highFanout++;
            highFanoutOwners.add(owner.getQualifiedName());
            return;
        }
        double confidence = interfaceOwner ? (implementations.size() == 1 ? 0.9 : 0.85) : 0.85;
        for (String implementation : implementations) {
            if (!graph.nodes().containsKey(implementation)) {
                continue;
            }
            dispatchEdges++;
            graph.edge(new Edge(callerKey, implementation, Edge.Type.CALLS, confidence, "dispatch",
                    new Evidence("SPOON", file, line, receiver, "FQN", "")));
        }
    }

    private List<String> heuristicCandidates(String receiverFqn, String name, int arity) {
        if (name == null) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        for (String owner : ctx.selfAndAncestors(receiverFqn)) {
            for (CtExecutable<?> executable : ctx.ownExecutables(owner)) {
                if (SpoonContext.isConstructor(executable) || !name.equals(executable.getSimpleName())) {
                    continue;
                }
                int parameters = executable.getParameters().size();
                boolean matches = parameters == arity
                        || (SpoonContext.isVarArgs(executable) && arity >= parameters - 1);
                if (!matches) {
                    continue;
                }
                String key = ctx.keysByExecutable().get(executable);
                if (key == null) {
                    key = ctx.executableKey(executable);
                }
                if (key != null && graph.nodes().containsKey(key)) {
                    candidates.add(key);
                }
                if (candidates.size() > 3) {
                    return candidates;
                }
            }
        }
        return candidates;
    }

    private String receiverType(CtExecutable<?> executable, CtInvocation<?> invocation) {
        if (invocation.getTarget() != null && invocation.getTarget().getType() != null) {
            return SpoonContext.typeName(invocation.getTarget().getType());
        }
        if (invocation.getExecutable().getDeclaringType() != null) {
            return SpoonContext.typeName(invocation.getExecutable().getDeclaringType());
        }
        CtType<?> declaring = SpoonContext.declaringType(executable);
        return declaring == null ? "" : declaring.getQualifiedName();
    }

    private String projectReceiver(CtExecutableReference<?> reference, String receiver) {
        CtTypeReference<?> declaring = reference == null ? null : reference.getDeclaringType();
        if (declaring != null) {
            try {
                String declared = declaring.getQualifiedName();
                if (ctx.isProjectType(declared)) {
                    return declared;
                }
            } catch (RuntimeException ignored) {
            }
            if (!(declaring instanceof CtTypeParameterReference)) {
                try {
                    CtTypeReference<?> erasure = declaring.getTypeErasure();
                    if (erasure != null && ctx.isProjectType(erasure.getQualifiedName())) {
                        return erasure.getQualifiedName();
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
        if (receiver != null && ctx.isProjectType(receiver)) {
            return receiver;
        }
        return null;
    }

    private CtExecutable<?> declaration(CtExecutableReference<?> reference) {
        try {
            return reference == null ? null : reference.getDeclaration();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void addStats() {
        stats.put("invocations", invocations);
        stats.put("constructorCalls", constructorCalls);
        stats.put("resolvedProjectCalls", resolvedProject);
        stats.put("dispatchEdges", dispatchEdges);
        stats.put("lombokResolvedCalls", lombokResolved);
        stats.put("heuristicResolvedCalls", heuristicResolved);
        stats.put("ambiguousCalls", ambiguous);
        stats.put("externalBoundaryCalls", externalBoundary);
        stats.put("unresolvedProjectCalls", unresolvedProject);
        stats.put("fieldReads", fieldReads);
        stats.put("fieldWrites", fieldWrites);
        stats.put("builderCalls", builderCalls);
        stats.put("typeUsageEdges", typeUsageEdges);
        stats.put("highFanoutDispatch", highFanout);
        stats.put("missingHandlerNodes", missingHandlers);
        stats.put("graphNodes", graph.nodes().size());
        stats.put("graphEdges", graph.edges().size());
        int total = invocations + constructorCalls;
        double covered = total == 0 ? 1.0
                : (resolvedProject + lombokResolved + heuristicResolved + externalBoundary) * 1.0 / total;
        stats.put("callCoverage", Math.round(covered * 1000.0) / 1000.0);

        if (highFanout > 0) {
            warnings.add(new Warning("HIGH_FANOUT_INTERFACE",
                    "存在实现类过多的接口分派，已跳过展开以避免噪声",
                    Map.of("count", highFanout, "owners", highFanoutOwners.size())));
        }
        if (ambiguous > 0) {
            warnings.add(new Warning("AMBIGUOUS_UNRESOLVED_CALLS",
                    "存在同名同参数数量候选过多的未解析调用，已跳过",
                    Map.of("count", ambiguous)));
        }
        if (covered < 0.6) {
            warnings.add(new Warning("LOW_CALL_COVERAGE",
                    "静态调用覆盖率较低，影响结果可能存在漏报",
                    Map.of("coverage", stats.get("callCoverage"))));
        }
    }

    private String methodName(SymbolKey key) {
        if (key.kind() != SymbolKey.Kind.METHOD) {
            return key.name();
        }
        return key.name().isBlank() ? "<init>" : key.name();
    }

    private String simpleName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        String simple = dot < 0 ? fqn : fqn.substring(dot + 1);
        int dollar = simple.lastIndexOf('$');
        return dollar < 0 ? simple : simple.substring(dollar + 1);
    }
}
