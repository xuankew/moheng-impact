package io.moheng.cli;

final class CliException extends RuntimeException {

    private final int exitCode;

    CliException(int exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    CliException(int exitCode, String message, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    int exitCode() {
        return exitCode;
    }
}
