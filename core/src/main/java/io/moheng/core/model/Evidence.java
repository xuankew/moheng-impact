package io.moheng.core.model;

public record Evidence(
        String source,
        String file,
        int line,
        String receiverType,
        String annotationResolution,
        String message
) {

    public Evidence {
        source = source == null ? "STATIC" : source;
        file = file == null ? "" : file;
        receiverType = receiverType == null ? "" : receiverType;
        annotationResolution = annotationResolution == null ? "FQN" : annotationResolution;
        message = message == null ? "" : message;
    }

    public static Evidence at(String file, int line) {
        return new Evidence("STATIC", file, line, "", "FQN", "");
    }
}
