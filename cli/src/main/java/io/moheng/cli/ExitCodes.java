package io.moheng.cli;

final class ExitCodes {

    static final int OK = 0;
    static final int USAGE = 2;
    static final int REPOSITORY = 3;
    static final int ANALYSIS_FAILED = 4;
    static final int RISK_THRESHOLD = 5;
    static final int WORKER_UNAVAILABLE = 6;

    private ExitCodes() {
    }
}
