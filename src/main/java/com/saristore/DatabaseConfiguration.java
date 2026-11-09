package com.saristore;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;

@Configuration
public class DatabaseConfiguration {
    @Bean
    DataSource dataSource(Environment environment,
                          @Value("${spring.datasource.hikari.maximum-pool-size:2}") int maxPoolSize) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw new IllegalStateException("Set DATABASE_URL to your Neon connection string.");
        }

        URI uri = URI.create(databaseUrl.trim());
        if (!"postgresql".equals(uri.getScheme()) && !"postgres".equals(uri.getScheme())) {
            throw new IllegalArgumentException("DATABASE_URL must use postgresql:// or postgres://.");
        }
        String[] credentials = uri.getRawUserInfo() == null ? new String[0] : uri.getRawUserInfo().split(":", 2);
        if (credentials.length != 2 || uri.getHost() == null) {
            throw new IllegalArgumentException("DATABASE_URL must include its host, username, and password.");
        }

        String query = uri.getRawQuery();
        if (query == null || !query.matches("(?i).*(^|&)sslmode=.*")) {
            query = query == null || query.isBlank() ? "sslmode=require" : query + "&sslmode=require";
        }
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort())
                + (uri.getRawPath() == null ? "/" : uri.getRawPath()) + "?" + query;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(decode(credentials[0]));
        config.setPassword(decode(credentials[1]));
        config.setMaximumPoolSize(Math.max(1, maxPoolSize));
        config.setMinimumIdle(0);
        config.setPoolName("sari-store-db");
        return new HikariDataSource(config);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
