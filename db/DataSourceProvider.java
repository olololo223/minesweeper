package com.example.minesweeper.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class DataSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(DataSourceProvider.class);

    private static HikariDataSource ds;

    private DataSourceProvider() {}

    public static synchronized DataSource get() {
        if (ds != null) return ds;

        Properties p = new Properties();

        // 1. Сначала — внешний файл, если есть
        Path external = Paths.get("db.properties").toAbsolutePath();
        if (Files.exists(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                p.load(in);
                log.info("DB config loaded from {}", external);
            } catch (IOException e) {
                throw new RuntimeException("Cannot read " + external, e);
            }
        } else {
            // 2. Fallback — classpath
            try (InputStream in = DataSourceProvider.class
                    .getClassLoader().getResourceAsStream("db.properties")) {
                if (in != null) {
                    p.load(in);
                    log.info("DB config loaded from classpath");
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot read db.properties", e);
            }
        }

        // 3. Переменные окружения перекрывают всё
        String url  = envOrProp("MINESWEEPER_DB_URL",  p, "db.url");
        String user = envOrProp("MINESWEEPER_DB_USER", p, "db.user");
        String pass = envOrProp("MINESWEEPER_DB_PASS", p, "db.password");
        String sizeStr = envOrProp("MINESWEEPER_DB_POOL", p, "db.pool.size");

        if (url == null || user == null || pass == null) {
            throw new IllegalStateException(
                    "Не заданы параметры БД. Установи переменные окружения " +
                            "MINESWEEPER_DB_URL/USER/PASS или создай db.properties");
        }

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(sizeStr != null ? Integer.parseInt(sizeStr) : 10);
        cfg.setPoolName("minesweeper-pool");

        ds = new HikariDataSource(cfg);
        return ds;
    }

    private static String envOrProp(String env, Properties p, String key) {
        String v = System.getenv(env);
        return (v != null && !v.isBlank()) ? v : p.getProperty(key);
    }

    public static synchronized void shutdown() {
        if (ds != null) {
            ds.close();
            ds = null;
        }
    }
}