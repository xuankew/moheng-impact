package io.moheng.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "mh",
        aliases = {"moheng"},
        mixinStandardHelpOptions = true,
        version = "墨衡·见微 0.1.0",
        description = "墨衡·见微：本地优先的变更影响分析工具",
        subcommands = {ImpactCommand.class, VersionCommand.class}
)
public final class MohengCli implements Runnable {

    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine(new MohengCli());
        commandLine.setExecutionExceptionHandler((ex, cmd, parseResult) -> {
            if (ex instanceof CliException cliException) {
                if (cliException.getMessage() != null && !cliException.getMessage().isBlank()) {
                    System.err.println("错误: " + cliException.getMessage());
                }
                return cliException.exitCode();
            }
            System.err.println("错误: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            return ExitCodes.ANALYSIS_FAILED;
        });
        System.exit(commandLine.execute(args));
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
