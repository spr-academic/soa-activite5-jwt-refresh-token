package integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests HTTP sur le WAR réellement construit, sans simuler les ressources REST. */
class RestApiIT {
    private static Server server;
    private static String base;
    private static final String TEST_KEY = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString();
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void deployWar() throws Exception {
        System.setProperty("soa.jwt.secret", TEST_KEY);
        File war = new File(System.getProperty("warFile"));
        assertTrue(war.isFile(), "Le WAR doit être construit avant les tests d'intégration.");
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        server.addConnector(connector);
        Configuration.ClassList.setServerDefault(server).addBefore(
                "org.eclipse.jetty.webapp.JettyWebXmlConfiguration",
                "org.eclipse.jetty.annotations.AnnotationConfiguration");
        WebAppContext app = new WebAppContext();
        app.setContextPath("/soa-activite5-jwt-refresh-token");
        app.setWar(war.getAbsolutePath());
        app.setTempDirectory(Files.createTempDirectory("soa-activite4-war-").toFile());
        app.setPersistTempDirectory(false);
        app.setThrowUnavailableOnStartupException(true);
        server.setHandler(app);
        server.start();
        base = "http://127.0.0.1:" + connector.getLocalPort() + app.getContextPath() + "/rest";
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) { server.stop(); server.destroy(); }
    }

    private static JsonNode login() throws Exception {
        Reply r = call("POST", "/authentication", "{\"username\":\"I001\",\"password\":\"pwd001\"}");
        assertEquals(200, r.status);
        return r.json();
    }

    private static Reply authorized(String method, String path, String body, String token) throws Exception {
        return call(method, path, body, "application/json", "application/json", "Bearer " + token);
    }

    private static Reply refresh(String token) throws Exception {
        return call("POST", "/authentication/refresh", "refreshToken=" + URLEncoder.encode(token, "UTF-8"),
                "application/json", "application/x-www-form-urlencoded");
    }

    private static Reply logout(String token) throws Exception {
        return call("POST", "/authentication/logout", "refreshToken=" + URLEncoder.encode(token, "UTF-8"),
                "application/json", "application/x-www-form-urlencoded");
    }

    private static JsonNode claims(String token) throws Exception {
        return JSON.readTree(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    }

    private static String signed(String type, long expiration, String issuer, String key) {
        return io.jsonwebtoken.Jwts.builder().setSubject("I001").setIssuer(issuer)
                .setIssuedAt(new java.util.Date(System.currentTimeMillis() - 10000))
                .setExpiration(new java.util.Date(expiration)).setId(java.util.UUID.randomUUID().toString())
                .claim("type", type).signWith(io.jsonwebtoken.SignatureAlgorithm.HS512,
                        key.getBytes(StandardCharsets.UTF_8)).compact();
    }

    @Test void loginReturnsPairAndLifetimeInSeconds() throws Exception {
        JsonNode pair = login();
        assertEquals("Bearer", pair.get("tokenType").asText());
        assertEquals(900, pair.get("expiresIn").asInt());
        assertEquals(4, pair.size());
        assertNotEquals(pair.get("accessToken").asText(), pair.get("refreshToken").asText());
    }

    @Test void tokenClaimsMatchSubjectIssuerTypeAndDurations() throws Exception {
        JsonNode pair = login();
        for (String name : new String[]{"accessToken", "refreshToken"}) {
            JsonNode c = claims(pair.get(name).asText());
            assertEquals("I001", c.get("sub").asText());
            assertEquals(base + "/", c.get("iss").asText());
            assertFalse(c.get("jti").asText().isEmpty());
            assertEquals(name.equals("accessToken") ? "access" : "refresh", c.get("type").asText());
            assertEquals(name.equals("accessToken") ? 900L : 604800L, c.get("exp").asLong() - c.get("iat").asLong());
        }
    }

    @Test void tokenResponsesDisableCaching() throws Exception {
        // Le contrat de réponse est contrôlé par une connexion HTTP réelle.
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/authentication").openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(5000); c.setReadTimeout(10000);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = c.getOutputStream()) { out.write("{\"username\":\"I001\",\"password\":\"pwd001\"}".getBytes(StandardCharsets.UTF_8)); }
        try { assertEquals(200, c.getResponseCode()); assertEquals("no-store", c.getHeaderField("Cache-Control")); }
        finally { c.disconnect(); }
    }

    @Test void invalidCredentialsAreForbidden() throws Exception {
        for (String body : new String[]{"null", "{}", "{\"username\":\" \",\"password\":\"pwd\"}", "{\"username\":\"I001\",\"password\":\"\"}"})
            assertEquals(403, call("POST", "/authentication", body).status);
    }

    @Test void malformedLoginJsonIsBadRequest() throws Exception {
        assertEquals(400, call("POST", "/authentication", "{").status);
    }

    @Test void allBusinessRoutesRequireAccessToken() throws Exception {
        String[][] routes = {{"GET","/options"},{"GET","/options/1"},{"POST","/options"},
                {"PUT","/options/1"},{"DELETE","/options/1"},{"GET","/etudiants"},
                {"GET","/etudiants/I001"},{"POST","/etudiants"},{"PUT","/etudiants/I001"},{"DELETE","/etudiants/I001"}};
        for (String[] r : routes) assertEquals(401, call(r[0], r[1], null).status, r[0] + " " + r[1]);
        for (String path : new String[]{"/etudiants/getAll", "/etudiants/option?codeOption=1"})
            assertEquals(401, call("GET", path, null, "application/xml", "application/json").status);
    }

    @Test void validAccessTokenReadsBothResources() throws Exception {
        String access = login().get("accessToken").asText();
        assertEquals(200, authorized("GET", "/options", null, access).status);
        assertEquals(200, authorized("GET", "/etudiants", null, access).status);
        assertEquals(200, authorized("GET", "/etudiants/I001", null, access).status);
        Reply xml = call("GET", "/etudiants/option?codeOption=1", null,
                "application/xml", "application/json", "Bearer " + access);
        assertEquals(200, xml.status);
        assertEquals("etudiants", parseXml(xml.body).getDocumentElement().getTagName());
        assertEquals(0, parseXml(xml.body).getElementsByTagName("option").getLength());
    }

    @Test void authorizedCreationReturns201AndPersists() throws Exception {
        String access = login().get("accessToken").asText();
        assertEquals(201, authorized("POST", "/options", option(501, "Robotique", 30), access).status);
        assertEquals(200, authorized("GET", "/etudiants", null, access).status);
        assertEquals("Robotique", authorized("GET", "/options/501", null, access).json().get("libelle").asText());
        assertEquals(201, authorized("POST", "/etudiants", student("JWT501", 501, "Test"), access).status);
        assertEquals(200, authorized("GET", "/etudiants/JWT501", null, access).status);
    }

    @Test void malformedAuthorizationIsUnauthorized() throws Exception {
        for (String auth : new String[]{"Basic abc", "Bearer ", "Bearer invalid", "Bearer a.b.c"})
            assertEquals(401, call("GET", "/options", null, "application/json", "application/json", auth).status);
    }

    @Test void expiredAccessTokenIsUnauthorized() throws Exception {
        String token = signed("access", System.currentTimeMillis() - 5000, base + "/", TEST_KEY);
        assertEquals(401, authorized("GET", "/options", null, token).status);
    }

    @Test void wrongSignatureAndIssuerAreUnauthorized() throws Exception {
        assertEquals(401, authorized("GET", "/options", null,
                signed("access", System.currentTimeMillis() + 60000, base + "/", TEST_KEY + "wrong")).status);
        assertEquals(401, authorized("GET", "/options", null,
                signed("access", System.currentTimeMillis() + 60000, "https://other.invalid/", TEST_KEY)).status);
    }

    @Test void refreshTokenCannotAccessBusinessResources() throws Exception {
        assertEquals(401, authorized("GET", "/options", null, login().get("refreshToken").asText()).status);
    }

    @Test void accessTokenCannotBeUsedForRefresh() throws Exception {
        assertEquals(401, refresh(login().get("accessToken").asText()).status);
    }

    @Test void refreshRotatesBothTokensAndRejectsReplay() throws Exception {
        JsonNode first = login(); String old = first.get("refreshToken").asText();
        Reply next = refresh(old); assertEquals(200, next.status);
        assertNotEquals(first.get("accessToken").asText(), next.json().get("accessToken").asText());
        assertNotEquals(old, next.json().get("refreshToken").asText());
        assertEquals(200, authorized("GET", "/options", null, next.json().get("accessToken").asText()).status);
        assertEquals(401, refresh(old).status);
        assertEquals(200, refresh(next.json().get("refreshToken").asText()).status);
    }

    @Test void unknownAndExpiredRefreshTokensAreUnauthorized() throws Exception {
        for (long exp : new long[]{System.currentTimeMillis() - 5000, System.currentTimeMillis() + 60000})
            assertEquals(401, refresh(signed("refresh", exp, base + "/", TEST_KEY)).status);
    }

    @Test void missingOrMalformedRefreshTokenIsUnauthorized() throws Exception {
        assertEquals(401, refresh("").status);
        assertEquals(401, refresh("invalid").status);
    }

    @Test void logoutRevokesRefreshButKeepsAccessUntilExpiration() throws Exception {
        JsonNode pair = login(); String token = pair.get("refreshToken").asText();
        Reply r = logout(token); assertEquals(204, r.status); assertEquals("", r.body);
        assertEquals(401, refresh(token).status);
        assertEquals(200, authorized("GET", "/options", null, pair.get("accessToken").asText()).status);
    }

    @Test void logoutIsIdempotentForInvalidAndRevokedTokens() throws Exception {
        String token = login().get("refreshToken").asText();
        assertEquals(204, logout(token).status); assertEquals(204, logout(token).status);
        assertEquals(204, logout("").status); assertEquals(204, logout("invalid").status);
    }

    @Test void concurrentRefreshOnlyAllowsOneConsumer() throws Exception {
        String token = login().get("refreshToken").asText();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> action = () -> { start.await(); return refresh(token).status; };
            java.util.concurrent.Future<Integer> a = pool.submit(action), b = pool.submit(action);
            start.countDown();
            List<Integer> statuses = java.util.Arrays.asList(a.get(10, java.util.concurrent.TimeUnit.SECONDS), b.get(10, java.util.concurrent.TimeUnit.SECONDS));
            java.util.Collections.sort(statuses); assertEquals(java.util.Arrays.asList(200, 401), statuses);
        } finally { pool.shutdownNow(); }
    }

    private static String option(int code, String label, int capacity) {
        return "{\"codeOption\":" + code + ",\"libelle\":\"" + label + "\",\"domaine\":\"Test\","
                + "\"responsable\":\"Responsable test\",\"credits\":30,\"semestre\":1,\"capacite\":" + capacity + "}";
    }

    private static String student(String id, int option, String name) {
        return "{\"identifiant\":\"" + id + "\",\"nom\":\"" + name + "\",\"prenom\":\"Test\","
                + "\"option\":{\"codeOption\":" + option + ",\"libelle\":\"Libellé ignoré\"},"
                + "\"anneeEtude\":2023,\"email\":\"test@example.com\"}";
    }

    private static Reply call(String method, String path, String body) throws Exception {
        return call(method, path, body, "application/json", "application/json");
    }

    private static Reply call(String method, String path, String body, String accept, String contentType) throws Exception {
        return call(method, path, body, accept, contentType, null);
    }

    private static Reply call(String method, String path, String body, String accept, String contentType, String auth) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setRequestMethod(method);
        if (auth != null) connection.setRequestProperty("Authorization", auth);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", accept);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType + "; charset=UTF-8");
            try (OutputStream out = connection.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int status = connection.getResponseCode();
        String type = connection.getContentType();
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (input != null) {
            try (InputStream stream = input) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            }
        }
        connection.disconnect();
        return new Reply(status, type == null ? "" : type, new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }

    private static Document parseXml(String text) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static class Reply {
        final int status;
        final String type;
        final String body;
        Reply(int status, String type, String body) { this.status = status; this.type = type; this.body = body; }
        JsonNode json() throws Exception { return JSON.readTree(body); }
    }
}
