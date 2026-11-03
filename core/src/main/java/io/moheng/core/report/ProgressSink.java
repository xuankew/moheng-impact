package io.moheng.core.report;

import io.moheng.core.model.Warning;

public interface ProgressSink {

    void progress(String phase, int percent, String message);

    default void warning(Warning warning) {
    }

    ProgressSink NOOP = (phase, percent, message) -> {
    };
}
