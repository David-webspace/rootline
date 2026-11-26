package dev.rootline.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 測試用的假下游服務：
 * <ul>
 *   <li>{@code /jwks}：回傳目前的測試公鑰（模擬 auth-service 的 JWKS）</li>
 *   <li>{@code /api/departments/boom}：回 500，驗證下游錯誤原樣傳回</li>
 *   <li>其他路徑：把收到的 method、path 與 header 以 JSON 回傳，讓測試檢查 Gateway 轉了什麼</li>
 * </ul>
 */
final class FakeDownstream {

    private final HttpServer server;
    private final List<String> hits = new CopyOnWriteArrayList<>();
    private volatile List<SigningKey> keys = new ArrayList<>();

    FakeDownstream() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", ex -> respond(ex, 200, jwksJson()));
        server.createContext("/", this::echo);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<String> hits() {
        return hits;
    }

    SigningKey addKey(String kid) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        SigningKey key = new SigningKey(kid, generator.generateKeyPair());
        List<SigningKey> updated = new ArrayList<>(keys);
        updated.add(key);
        keys = updated;
        return key;
    }

    void stop() {
        server.stop(0);
    }

    private void echo(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getRawPath();
        hits.add(path);
        if (path.equals("/api/departments/boom")) {
            respond(ex, 500, "{\"code\":\"DOWNSTREAM_BUG\",\"message\":\"boom\",\"data\":null}");
            return;
        }
        Map<String, String> headers = new TreeMap<>();
        ex.getRequestHeaders().forEach((name, values) ->
                headers.put(name.toLowerCase(Locale.ROOT), String.join("|", values)));
        StringBuilder json = new StringBuilder("{\"method\":\"").append(ex.getRequestMethod())
                .append("\",\"path\":\"").append(path).append("\",\"headers\":{");
        headers.forEach((k, v) -> json.append('"').append(k).append("\":\"")
                .append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append("\","));
        if (!headers.isEmpty()) {
            json.setLength(json.length() - 1);
        }
        json.append("}}");
        respond(ex, 200, json.toString());
    }

    private String jwksJson() {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        StringBuilder json = new StringBuilder("{\"keys\":[");
        for (SigningKey key : keys) {
            RSAPublicKey pub = (RSAPublicKey) key.pair().getPublic();
            json.append("{\"kty\":\"RSA\",\"alg\":\"RS256\",\"use\":\"sig\",\"kid\":\"").append(key.kid())
                    .append("\",\"n\":\"").append(enc.encodeToString(unsigned(pub.getModulus())))
                    .append("\",\"e\":\"").append(enc.encodeToString(unsigned(pub.getPublicExponent())))
                    .append("\"},");
        }
        if (!keys.isEmpty()) {
            json.setLength(json.length() - 1);
        }
        return json.append("]}").toString();
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    record SigningKey(String kid, KeyPair pair) {
    }
}
