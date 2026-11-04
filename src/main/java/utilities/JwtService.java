package utilities;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JwtService {
    public static final long ACCESS_MINUTES = 15L;
    public static final long REFRESH_DAYS = 7L;
    private static final byte[] SECRET_KEY = loadKey();
    private static final Map<String, Long> REFRESH_STORE = new ConcurrentHashMap<>();

    private JwtService() { }

    private static byte[] loadKey() {
        String configured = System.getProperty("soa.jwt.secret", System.getenv("SOA_JWT_SECRET"));
        if (configured != null) {
            byte[] key = configured.getBytes(StandardCharsets.UTF_8);
            if (key.length < 64) throw new IllegalStateException("SOA_JWT_SECRET doit contenir au moins 64 octets UTF-8.");
            return key;
        }
        // Mode atelier : une clé aléatoire privée, renouvelée à chaque déploiement.
        byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);
        return key;
    }

    public static String generateAccessToken(String username, String issuer) {
        return buildToken(username, issuer, "access", ACCESS_MINUTES * 60_000L, UUID.randomUUID().toString());
    }

    public static String generateRefreshToken(String username, String issuer) {
        long now = System.currentTimeMillis();
        REFRESH_STORE.entrySet().removeIf(entry -> entry.getValue() <= now);
        String id = UUID.randomUUID().toString();
        long duration = REFRESH_DAYS * 86_400_000L;
        String token = buildToken(username, issuer, "refresh", duration, id);
        REFRESH_STORE.put(id, now + duration);
        return token;
    }

    private static String buildToken(String username, String issuer, String type, long duration, String id) {
        long now = System.currentTimeMillis();
        return Jwts.builder().setSubject(username).setIssuer(issuer).setId(id)
                .setIssuedAt(new Date(now)).setExpiration(new Date(now + duration))
                .claim("type", type).signWith(SignatureAlgorithm.HS512, SECRET_KEY).compact();
    }

    private static Claims parse(String token, String issuer, String type) {
        Jws<Claims> signed = Jwts.parser().setSigningKey(SECRET_KEY).requireIssuer(issuer)
                .require("type", type).parseClaimsJws(token);
        Claims claims = signed.getBody();
        if (!"HS512".equals(signed.getHeader().getAlgorithm()) || claims.getExpiration() == null
                || claims.getIssuedAt() == null || claims.getSubject() == null
                || claims.getSubject().trim().isEmpty() || claims.getId() == null) {
            throw new IllegalArgumentException("Jeton incomplet ou algorithme invalide.");
        }
        return claims;
    }

    public static Claims validateAccessToken(String token, String issuer) {
        return parse(token, issuer, "access");
    }

    public static String useRefreshToken(String token, String issuer) {
        Claims claims = parse(token, issuer, "refresh");
        // Retrait atomique : un seul appel concurrent peut consommer ce jeton.
        if (REFRESH_STORE.remove(claims.getId()) == null) {
            throw new IllegalArgumentException("Refresh token révoqué ou déjà utilisé.");
        }
        return claims.getSubject();
    }

    public static void revokeRefreshToken(String token, String issuer) {
        try {
            REFRESH_STORE.remove(parse(token, issuer, "refresh").getId());
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
            // Déconnexion idempotente, y compris pour un jeton déjà expiré.
        }
    }
}
