package io.moheng.core.change;

import io.moheng.core.model.ApiContractChange;
import io.moheng.core.model.ApiInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ApiDiff {

    public List<ApiContractChange> diff(List<ApiInfo> baseApis, List<ApiInfo> headApis) {
        Map<String, List<ApiInfo>> baseByKey = groupByKey(baseApis);
        Map<String, List<ApiInfo>> headByKey = groupByKey(headApis);
        Set<String> keys = new TreeSet<>();
        keys.addAll(baseByKey.keySet());
        keys.addAll(headByKey.keySet());

        List<ApiContractChange> changes = new ArrayList<>();
        for (String key : keys) {
            List<ApiInfo> before = baseByKey.getOrDefault(key, List.of());
            List<ApiInfo> after = headByKey.getOrDefault(key, List.of());
            if (before.isEmpty()) {
                changes.add(new ApiContractChange(ApiContractChange.Type.ADDED, key,
                        null, after.get(0), "endpoint added"));
                continue;
            }
            if (after.isEmpty()) {
                changes.add(new ApiContractChange(ApiContractChange.Type.REMOVED, key,
                        before.get(0), null, "endpoint removed"));
                continue;
            }
            if (!signatures(before).equals(signatures(after))) {
                String detail = describe(before, after);
                changes.add(new ApiContractChange(ApiContractChange.Type.CHANGED, key,
                        before.get(0), after.get(0), detail));
            }
        }
        changes.sort((left, right) -> {
            int byKey = left.apiKey().compareTo(right.apiKey());
            return byKey != 0 ? byKey : left.type().compareTo(right.type());
        });
        return changes;
    }

    private String describe(List<ApiInfo> before, List<ApiInfo> after) {
        Set<String> beforeHandlers = new TreeSet<>();
        Set<String> afterHandlers = new TreeSet<>();
        before.forEach(api -> beforeHandlers.add(api.handlerKey()));
        after.forEach(api -> afterHandlers.add(api.handlerKey()));
        if (!beforeHandlers.equals(afterHandlers)) {
            return "handler changed: " + String.join(",", beforeHandlers)
                    + " -> " + String.join(",", afterHandlers);
        }
        return "conditions changed";
    }

    private Set<String> signatures(List<ApiInfo> apis) {
        Set<String> signatures = new LinkedHashSet<>();
        for (ApiInfo api : apis) {
            signatures.add(api.handlerKey() + "|" + canonicalConditions(api));
        }
        return signatures;
    }

    private String canonicalConditions(ApiInfo api) {
        List<String> parts = new ArrayList<>();
        api.conditions().keySet().stream().sorted().forEach(key -> {
            List<String> values = new ArrayList<>(api.conditions().get(key));
            values.sort(String::compareTo);
            parts.add(key + "=" + String.join(",", values));
        });
        return String.join(";", parts);
    }

    private Map<String, List<ApiInfo>> groupByKey(List<ApiInfo> apis) {
        Map<String, List<ApiInfo>> grouped = new LinkedHashMap<>();
        for (ApiInfo api : apis) {
            grouped.computeIfAbsent(api.key(), key -> new ArrayList<>()).add(api);
        }
        return grouped;
    }
}
