package io.moheng.core.risk;

import io.moheng.core.impact.ImpactEngine;
import io.moheng.core.model.ApiContractChange;
import io.moheng.core.model.ApiImpact;
import io.moheng.core.model.ApiInfo;
import io.moheng.core.model.ImpactPath;
import io.moheng.core.model.RiskReason;
import io.moheng.core.model.RiskReport;
import io.moheng.core.model.SafeMode;
import io.moheng.core.model.SymbolChange;
import io.moheng.core.model.SymbolKey;
import io.moheng.core.source.AnalyzedProject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RiskEngine {

    private static final int MAX_PATHS_PER_API = 10;

    private static final String[] CORE_KEYWORDS = {
            "order", "pay", "payment", "auth", "coupon", "login", "token",
            "refund", "settle", "wallet", "balance", "stock", "price"
    };

    public record Result(List<ApiImpact> apiImpacts, RiskReport report) {
        public Result {
            apiImpacts = apiImpacts == null ? List.of() : List.copyOf(apiImpacts);
        }
    }

    public Result evaluate(AnalyzedProject head, List<SymbolChange> symbolChanges,
                           List<ApiContractChange> contractChanges,
                           ImpactEngine.Result impact, SafeMode safeMode) {
        Map<String, SymbolChange> changesByKey = new LinkedHashMap<>();
        for (SymbolChange change : symbolChanges) {
            changesByKey.putIfAbsent(change.key().id(), change);
        }
        Map<String, ApiInfo> apiById = new LinkedHashMap<>();
        for (ApiInfo api : head.apis()) {
            apiById.put(api.id(), api);
        }
        Map<String, ApiContractChange> contractByKey = new LinkedHashMap<>();
        for (ApiContractChange change : contractChanges) {
            contractByKey.put(change.apiKey(), change);
        }
        Map<String, ImpactPath> bestPathByApi = new LinkedHashMap<>();
        Map<String, List<ImpactPath>> pathsByApi = new LinkedHashMap<>();
        for (ImpactPath path : impact.paths()) {
            pathsByApi.computeIfAbsent(path.apiId(), key -> new ArrayList<>()).add(path);
            ImpactPath existing = bestPathByApi.get(path.apiId());
            if (existing == null || better(path, existing)) {
                bestPathByApi.put(path.apiId(), path);
            }
        }
        for (List<ImpactPath> paths : pathsByApi.values()) {
            paths.sort(Comparator.comparingDouble(ImpactPath::confidence).reversed()
                    .thenComparingInt(ImpactPath::depth)
                    .thenComparing(ImpactPath::changedKey));
            if (paths.size() > MAX_PATHS_PER_API) {
                paths.subList(MAX_PATHS_PER_API, paths.size()).clear();
            }
        }
        Map<String, Integer> fanIn = head.graph().inDegree();

        List<ApiImpact> impacts = new ArrayList<>();
        for (ApiInfo api : head.apis()) {
            ApiContractChange contract = contractByKey.get(api.key());
            ImpactPath path = bestPathByApi.get(api.id());
            List<ImpactPath> apiPaths = pathsByApi.getOrDefault(api.id(), List.of());
            ApiImpact evaluated = evaluateApi(api, path, apiPaths, contract, changesByKey, fanIn);
            if (evaluated != null) {
                impacts.add(evaluated);
            }
        }
        for (ApiContractChange contract : contractChanges) {
            if (contract.type() != ApiContractChange.Type.REMOVED || contract.before() == null) {
                continue;
            }
            if (apiById.values().stream().anyMatch(api -> api.key().equals(contract.apiKey()))) {
                continue;
            }
            impacts.add(removedApiImpact(contract));
        }
        impacts.sort(Comparator.comparingInt(ApiImpact::score).reversed()
                .thenComparing(impacted -> impacted.api().key()));

        RiskReport report = buildReport(impacts, contractChanges, safeMode);
        return new Result(impacts, report);
    }

    private ApiImpact evaluateApi(ApiInfo api, ImpactPath path, List<ImpactPath> apiPaths,
                                  ApiContractChange contract,
                                  Map<String, SymbolChange> changesByKey,
                                  Map<String, Integer> fanIn) {
        Map<String, RiskReason> reasons = new LinkedHashMap<>();
        int score = 0;

        SymbolChange handlerChange = changesByKey.get(api.handlerKey());
        boolean affected = handlerChange != null || path != null || contract != null;
        if (!affected) {
            return null;
        }
        if (handlerChange != null) {
            add(reasons, "DIRECT_HANDLER_CHANGED", 30, "直接修改 Controller handler",
                    Map.of("symbol", handlerChange.key().id(), "change", handlerChange.changeType().name()));
            score += 30;
        }

        if (path != null) {
            int depth = path.depth();
            if (depth <= 2) {
                add(reasons, "PATH_DEPTH_NEAR", 10, "影响路径深度 1-2", Map.of("depth", depth));
                score += 10;
            } else if (depth <= 4) {
                add(reasons, "PATH_DEPTH_MEDIUM", 20, "影响路径深度 3-4", Map.of("depth", depth));
                score += 20;
            } else {
                add(reasons, "PATH_DEPTH_FAR", 25, "影响路径深度 ≥5", Map.of("depth", depth));
                score += 25;
            }
            if (path.confidence() < 0.8) {
                add(reasons, "LOW_CONFIDENCE_PATH", 5, "影响路径置信度低于 0.8",
                        Map.of("confidence", round(path.confidence())));
                score += 5;
            }
            SymbolChange seed = seedChange(changesByKey, path.changedKey());
            if (seed != null) {
                int incoming = fanIn.getOrDefault(seed.key().id(), 0);
                if (incoming >= 10) {
                    add(reasons, "HIGH_FAN_IN", 10, "变更符号 fan-in ≥10",
                            Map.of("symbol", seed.key().id(), "fanIn", incoming));
                    score += 10;
                }
                if (seed.changeType() == SymbolChange.ChangeType.SIGNATURE_CHANGED) {
                    add(reasons, "SIGNATURE_CHANGED", 15, "变更符号签名变化",
                            Map.of("symbol", seed.key().id()));
                    score += 15;
                }
                if (seed.publicDto() && isPublicDtoContractChange(seed)) {
                    add(reasons, "PUBLIC_DTO_CHANGED", 20, "公共 DTO 契约变化",
                            Map.of("symbol", seed.key().id(), "change", seed.changeType().name()));
                    score += 20;
                }
            }
        }

        if (contract != null) {
            if (contract.type() == ApiContractChange.Type.CHANGED
                    || contract.type() == ApiContractChange.Type.REMOVED) {
                add(reasons, "API_CONTRACT_CHANGED", 30, "API 契约删除或变更",
                        Map.of("api", api.key(), "changeType", contract.type().name()));
                score += 30;
            } else if (contract.type() == ApiContractChange.Type.ADDED) {
                add(reasons, "API_ADDED", 5, "新增 API", Map.of("api", api.key()));
                score += 5;
            }
        }

        if (containsCoreKeyword(api.path())) {
            add(reasons, "CORE_API_KEYWORD", 10, "核心业务 API 关键字",
                    Map.of("api", api.key()));
            score += 10;
        }

        if (reasons.isEmpty()) {
            return null;
        }
        score = Math.min(score, 100);
        double confidence = path == null ? 1.0 : path.confidence();
        int depth = path == null ? 0 : path.depth();
        return new ApiImpact(api, level(score), score, confidence, depth,
                new ArrayList<>(reasons.values()), apiPaths);
    }

    private ApiImpact removedApiImpact(ApiContractChange contract) {
        List<RiskReason> reasons = new ArrayList<>();
        reasons.add(new RiskReason("API_CONTRACT_REMOVED", 30, "API 契约删除",
                Map.of("api", contract.apiKey())));
        int score = 30;
        if (containsCoreKeyword(contract.before().path())) {
            reasons.add(new RiskReason("CORE_API_KEYWORD", 10, "核心业务 API 关键字",
                    Map.of("api", contract.apiKey())));
            score += 10;
        }
        return new ApiImpact(contract.before(), level(score), score, 1.0, 0, reasons, List.of());
    }

    private RiskReport buildReport(List<ApiImpact> impacts, List<ApiContractChange> contracts,
                                   SafeMode safeMode) {
        int maxApiScore = 0;
        ApiImpact top = null;
        for (ApiImpact impact : impacts) {
            if (impact.score() > maxApiScore) {
                maxApiScore = impact.score();
                top = impact;
            }
        }
        int score = maxApiScore;
        List<RiskReason> reasons = new ArrayList<>();

        if (safeMode.enabled()) {
            reasons.addAll(safeMode.triggers());
        }
        for (ApiContractChange contract : contracts) {
            String code = switch (contract.type()) {
                case ADDED -> "API_ADDED";
                case REMOVED -> "API_CONTRACT_REMOVED";
                case CHANGED -> "API_CONTRACT_CHANGED";
            };
            int weight = contract.type() == ApiContractChange.Type.ADDED ? 5 : 30;
            reasons.add(new RiskReason(code, weight,
                    switch (contract.type()) {
                        case ADDED -> "新增 API";
                        case REMOVED -> "API 被删除";
                        case CHANGED -> "API 契约变更";
                    },
                    Map.of("api", contract.apiKey())));
            if (contract.type() == ApiContractChange.Type.ADDED) {
                score = Math.max(score, 5);
            } else {
                score = Math.max(score, 30);
            }
        }
        if (top != null) {
            for (RiskReason reason : top.reasons()) {
                Map<String, Object> evidence = new LinkedHashMap<>(reason.evidence());
                evidence.put("api", top.api().key());
                reasons.add(new RiskReason(reason.code(), reason.weight(), reason.message(), evidence));
            }
        }

        if (safeMode.enabled()) {
            score = Math.max(score, 60);
            if (safeMode.triggers().size() >= 3 || impacts.size() >= 20) {
                score = Math.max(score, 80);
            }
        }
        score = Math.min(score, 100);
        return new RiskReport(level(score), score, dedupe(reasons), safeMode);
    }

    private List<RiskReason> dedupe(List<RiskReason> reasons) {
        Map<String, RiskReason> unique = new LinkedHashMap<>();
        for (RiskReason reason : reasons) {
            String key = reason.code() + "|" + reason.evidence().getOrDefault("file", "")
                    + "|" + reason.evidence().getOrDefault("api", "");
            unique.putIfAbsent(key, reason);
        }
        return new ArrayList<>(unique.values());
    }

    private SymbolChange seedChange(Map<String, SymbolChange> changes, String key) {
        SymbolChange direct = changes.get(key);
        if (direct != null || key == null || !key.startsWith("CLASS:")) {
            return direct;
        }
        String owner = key.substring("CLASS:".length());
        for (SymbolChange change : changes.values()) {
            if (change.key().kind() == SymbolKey.Kind.FIELD
                    && change.key().owner().equals(owner)
                    && change.publicDto()) {
                return change;
            }
        }
        return null;
    }

    private boolean better(ImpactPath candidate, ImpactPath existing) {
        if (Math.abs(candidate.confidence() - existing.confidence()) > 1e-9) {
            return candidate.confidence() > existing.confidence();
        }
        return candidate.depth() < existing.depth();
    }

    private boolean isPublicDtoContractChange(SymbolChange change) {
        return switch (change.key().kind()) {
            case FIELD -> change.changeType() == SymbolChange.ChangeType.ADDED
                    || change.changeType() == SymbolChange.ChangeType.REMOVED
                    || change.changeType() == SymbolChange.ChangeType.STRUCTURE_CHANGED
                    || change.changeType() == SymbolChange.ChangeType.CONTRACT_CHANGED;
            case CLASS -> change.changeType() == SymbolChange.ChangeType.STRUCTURE_CHANGED
                    || change.changeType() == SymbolChange.ChangeType.CONTRACT_CHANGED;
            case METHOD -> false;
        };
    }

    private boolean containsCoreKeyword(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        for (String keyword : CORE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void add(Map<String, RiskReason> reasons, String code, int weight,
                     String message, Map<String, Object> evidence) {
        reasons.putIfAbsent(code, new RiskReason(code, weight, message, evidence));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static String level(int score) {
        if (score >= 60) {
            return "CRITICAL";
        }
        if (score >= 40) {
            return "HIGH";
        }
        if (score >= 20) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
