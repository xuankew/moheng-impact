package io.moheng.core;

public class AnalysisException extends RuntimeException {

    private final String code;

    public AnalysisException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AnalysisException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
