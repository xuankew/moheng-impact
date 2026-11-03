package io.moheng.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(MohengProperties properties) {
        Path dataDir = properties.dataDirectory();
        try {
            Files.createDirectories(dataDir);
            Files.createDirectories(properties.temporaryDirectory());
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建数据目录: " + dataDir, e);
        }
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setBusyTimeout(5_000);
        config.enforceForeignKeys(true);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + dataDir.resolve("moheng.db"));
        return dataSource;
    }
}
