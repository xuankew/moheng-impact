package io.moheng.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "impact",
        aliases = {"jw"},
        mixinStandardHelpOptions = true,
        description = "变更影响分析",
        subcommands = {AnalyzeCommand.class, InitCommand.class, ShowCommand.class, ServeCommand.class}
)
final class ImpactCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
