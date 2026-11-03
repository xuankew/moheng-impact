package io.moheng.cli;

import picocli.CommandLine.Command;

@Command(name = "version", description = "显示版本信息")
final class VersionCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("墨衡·见微 (Moheng Impact) 0.1.0");
        System.out.println("JSON schemaVersion: 1 / worker protocolVersion: 1");
    }
}
