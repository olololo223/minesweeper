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

        // 1) Пытаемся найти db.properties рядом с рабочим каталогом
        //    (обычно это корень проекта, где ты запускаешь java -jar)
        Path external = Paths.get("db.properties").toAbsolutePath();
        if (Files.exists(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                p.load(in);
                log.info("Config downloaded from {}", external);
            } catch (IOException e) {
                throw new RuntimeException("Не удалось прочитать " + external, e);
            }
        } else {
            // 2) Fallback: из classpath (внутри JAR)
            try (InputStream in = DataSourceProvider.class
                    .getClassLoader().getResourceAsStream("db.properties")) {
                if (in == null) {
                    throw new IllegalStateException(
                            "db.properties не найден ни рядом с JAR, ни в classpath. " +
                                    "Положи файл в D:\\game\\db.properties");
                }
                p.load(in);
                log.info("Config downloaded from classpath (inside JAR)");
            } catch (IOException e) {
                throw new RuntimeException("Не удалось прочитать db.properties из classpath", e);
            }
        }

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(p.getProperty("db.url"));
        cfg.setUsername(p.getProperty("db.user"));
        cfg.setPassword(p.getProperty("db.password"));
        cfg.setMaximumPoolSize(
                Integer.parseInt(p.getProperty("db.pool.size", "10")));
        cfg.setPoolName("minesweeper-pool");

        ds = new HikariDataSource(cfg);
        return ds;
    }

    public static synchronized void shutdown() {
        if (ds != null) {
            ds.close();
            ds = null;
        }
    }
}