package com.example.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public final class AppSchemaCredentials {
    private static final Logger log = LoggerFactory.getLogger(AppSchemaCredentials.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String USERNAME;
    public static final String PASSWORD; // keep in memory only if necessary
    public static final String PASSWORD_HASH; // hex SHA-256

    static {
        String user = null;
        String pass = null;
        try {
            Region region = Region.of(System.getenv().getOrDefault("AWS_REGION", "ap-south-1"));
            try (SecretsManagerClient sm = SecretsManagerClient.builder().region(region).build()) {
                String secretName = System.getenv().getOrDefault(
                        "APPLICATION_SCHEMA_SECRET_NAME", "rds/application_schema/creds");
                GetSecretValueRequest req = GetSecretValueRequest.builder().secretId(secretName).build();
                GetSecretValueResponse resp = sm.getSecretValue(req);
                String raw = resp.secretString();
                if (raw == null) throw new IllegalStateException("Secret has no secretString");

                JsonNode root = MAPPER.readTree(raw);
                user = root.has("username") ? root.get("username").asText() :
                       root.has("user") ? root.get("user").asText() : null;
                pass = root.has("password") ? root.get("password").asText() :
                       root.has("pass") ? root.get("pass").asText() : null;

                if (user == null || pass == null) {
                    throw new IllegalStateException("Secret JSON missing username/password");
                }
            }

            // Compute a fingerprint (sha256) — safe to log for correlation
            String hash = sha256Hex(pass);

            // Mask password for logs (only show last 4 characters)
            String masked = maskExceptLast(pass, 4);

            // Log safely
            log.info("Loaded APPLICATION_SCHEMA credentials. username='{}', passwordMasked='{}', passwordSHA256='{}'",
                    user, masked, hash);

            USERNAME = user;
            PASSWORD = pass;
            PASSWORD_HASH = hash;
        } catch (Exception e) {
            // Don't log the password here either — log the error and fail fast or fallback safely
            log.error("Failed to load APPLICATION_SCHEMA credentials from Secrets Manager: {}", e.getMessage(), e);
            throw new ExceptionInInitializerError(e);
        }
    }

    private AppSchemaCredentials() { /* utility */ }

    private static String maskExceptLast(String s, int lastChars) {
        if (s == null) return null;
        int len = s.length();
        if (len <= lastChars) {
            // do not expose short secrets; return all masked
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
            // If hashing fails, return a safe placeholder (don't log raw password)
            log.warn("Unable to compute password SHA-256: {}", ex.getMessage());
            return "<unavailable>";
        }
    }
}


<dependency>
  <groupId>software.amazon.awssdk</groupId>
  <artifactId>secretsmanager</artifactId>
  <version>2.20.x</version> <!-- pick a recent 2.x -->
</dependency>
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
  <version>2.15.x</version>
</dependency>
