package com.example.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@Configuration
public class DataSourceConfig {
    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${aws.region:ap-south-1}")
    private String awsRegion;

    @Value("${application.schema.secret-name}")
    private String secretName;

    // Example: you can keep URL in properties, or read host/port/dbname from secret if needed.
    @Value("${spring.datasource.jdbc-url:jdbc:oracle:thin:@myhost:1521/ORCLPDB1}")
    private String jdbcUrl;

    @Bean(destroyMethod = "close")
    public DataSource dataSource() {
        String username = null;
        String password = null;
        SecretsManagerClient smClient = null;
        try {
            Region region = Region.of(awsRegion);
            smClient = SecretsManagerClient.builder().region(region).build();

            GetSecretValueRequest req = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse resp = smClient.getSecretValue(req);
            String secretString = resp.secretString();
            if (secretString == null) {
                throw new IllegalStateException("Secret " + secretName + " returned no secretString");
            }

            JsonNode root = MAPPER.readTree(secretString);
            username = firstNonNullText(root, "username", "user", "db_username");
            password = firstNonNullText(root, "password", "pass", "db_password");

            if (username == null || password == null) {
                throw new IllegalStateException("Secret JSON missing username or password: " + secretString);
            }

            // compute fingerprint and masked representation for logging
            String hash = sha256Hex(password);
            String masked = maskExceptLast(password, 4);

            log.info("Loaded APPLICATION_SCHEMA secret. username='{}', passwordMasked='{}', passwordSHA256='{}'",
                    username, masked, hash);

            // Build Hikari config and datasource
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(jdbcUrl);
            cfg.setUsername(username);
            cfg.setPassword(password);

            // Additional Hikari tuning - tune for your workload
            cfg.setMaximumPoolSize(20);
            cfg.setMinimumIdle(2);
            cfg.setConnectionTimeout(30000);
            cfg.setValidationTimeout(5000);
            cfg.setLeakDetectionThreshold(20000);
            cfg.setPoolName("app-schema-pool");

            HikariDataSource ds = new HikariDataSource(cfg);

            // minimize time password variable lives: clear local var and secretString char data
            zeroOutString(password);
            // Don't rely on zeroing; JVM Strings are immutable — this is best-effort for local references.
            password = null;

            return ds;
        } catch (Exception e) {
            log.error("Failed to create DataSource from Secrets Manager secret '{}': {}", secretName, e.getMessage(), e);
            throw new IllegalStateException("Unable to initialize DataSource", e);
        } finally {
            if (smClient != null) {
                smClient.close();
            }
        }
    }

    private static String firstNonNullText(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String k : keys) {
            if (node.has(k) && !node.get(k).isNull()) {
                return node.get(k).asText();
            }
        }
        return null;
    }

    private static String maskExceptLast(String s, int lastChars) {
        if (s == null) return null;
        int len = s.length();
        if (len <= lastChars) {
            char[] a = new char[Math.max(4, len)];
            Arrays.fill(a, '*');
            return new String(a);
        }
        char[] masked = new char[len];
        Arrays.fill(masked, 0, len - lastChars, '*');
        System.arraycopy(s.toCharArray(), len - lastChars, masked, len - lastChars, lastChars);
        return new String(masked);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            log.warn("Unable to compute SHA-256 fingerprint: {}", ex.getMessage());
            return "<unavailable>";
        }
    }

    /**
     * Best-effort attempt to overwrite the contents of a String by creating a char[] copy and
     * zeroing it. JVM Strings are immutable and may still exist in other memory locations;
     * this is only a local minimization step.
     */
    private static void zeroOutString(String s) {
        if (s == null) return;
        try {
            char[] copy = s.toCharArray();
            Arrays.fill(copy, '\0');
        } catch (Exception ignored) { }
    }
}
