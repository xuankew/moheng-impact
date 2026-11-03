package io.moheng.cli;

import io.moheng.core.json.Json;
import io.moheng.core.model.AnalysisResult;
final class JsonOutput {

    private JsonOutput() {
    }

    static String render(AnalysisResult result) {
        return Json.write(result) + System.lineSeparator();
    }
}
