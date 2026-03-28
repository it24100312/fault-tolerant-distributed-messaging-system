package com.ds.messaging.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import com.ds.messaging.server.MessagingServer;
import com.ds.messaging.server.ServerNode;
import com.ds.messaging.utils.Logger;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Lightweight built-in HTTP dashboard for cluster operations and monitoring.
 */
public class DashboardServer {
    private static final Logger logger = Logger.getInstance();
    private static final Gson gson = new Gson();

    private final MessagingServer messagingServer;
    private final int port;

    private HttpServer httpServer;
    private int boundPort;

    public DashboardServer(MessagingServer messagingServer, int port) {
        this.messagingServer = messagingServer;
        this.port = port;
    }

    public synchronized void start() {
        if (httpServer != null) {
            return;
        }

        try {
            try {
                httpServer = HttpServer.create(new InetSocketAddress("localhost", port), 0);
            } catch (IOException bindError) {
                logger.warn("Dashboard port {} unavailable, falling back to a free port", port);
                httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            }

            httpServer.setExecutor(Executors.newCachedThreadPool());

            httpServer.createContext("/", exchange -> serveResource(exchange, "ui/index.html", "text/html; charset=utf-8"));
            httpServer.createContext("/app.js", exchange -> serveResource(exchange, "ui/app.js", "application/javascript; charset=utf-8"));
            httpServer.createContext("/styles.css", exchange -> serveResource(exchange, "ui/styles.css", "text/css; charset=utf-8"));

            httpServer.createContext("/api/cluster", exchange -> {
                if (!isMethod(exchange, "GET")) {
                    sendJson(exchange, 405, error("Method not allowed"));
                    return;
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("running", messagingServer.isRunning());
                payload.put("nodeCount", messagingServer.getNodeCount());
                ServerNode leader = messagingServer.getLeader();
                payload.put("leader", leader == null ? null : leader.getNodeId());
                payload.put("term", messagingServer.getCurrentTerm());
                payload.put("nodes", messagingServer.getNodeStatuses());
                payload.put("events", messagingServer.getRecentEvents(80));
                payload.put("messageTable", messagingServer.getRecentMessages(150));
                payload.put("logicalClock", messagingServer.getLogicalClock());
                payload.put("requiredQuorum", messagingServer.getRequiredQuorum());
                payload.put("healthyNodes", messagingServer.getActiveNodeIds().size());
                payload.put("quorumSatisfied", messagingServer.isQuorumSatisfied());
                payload.put("activeNodeIds", messagingServer.getActiveNodeIds());
                payload.put("failedNodeIds", messagingServer.getFailedNodeIds());
                payload.put("explain", explanationBlock());

                sendJson(exchange, 200, payload);
            });

            httpServer.createContext("/api/send", new OperationHandler(params -> {
                String from = params.get("fromNodeId");
                String to = params.get("toNodeId");
                String message = params.get("message");
                require(from, "fromNodeId");
                require(to, "toNodeId");
                require(message, "message");

                boolean ok = messagingServer.sendMessage(from, to, message);
                if (!ok) {
                    return error("Failed to send message. Check node IDs and node state.");
                }
                return ok("Message sent successfully.");
            }));

            httpServer.createContext("/api/kill", new OperationHandler(params -> {
                String nodeId = params.get("nodeId");
                require(nodeId, "nodeId");
                boolean ok = messagingServer.killNode(nodeId);
                return ok ? ok("Node killed: " + nodeId) : error("Node not found: " + nodeId);
            }));

            httpServer.createContext("/api/recover", new OperationHandler(params -> {
                String nodeId = params.get("nodeId");
                require(nodeId, "nodeId");

                String host = params.get("host");
                String portText = params.get("port");
                boolean ok;
                if (host != null && !host.isBlank() && portText != null && !portText.isBlank()) {
                    int nodePort = Integer.parseInt(portText);
                    ok = messagingServer.recoverNode(nodeId, host, nodePort);
                } else {
                    ok = messagingServer.recoverNode(nodeId);
                }

                return ok ? ok("Node recovered: " + nodeId) : error("Could not recover node: " + nodeId);
            }));

            httpServer.createContext("/api/add-node", new OperationHandler(params -> {
                String nodeId = params.get("nodeId");
                String host = params.get("host");
                String portText = params.get("port");
                require(nodeId, "nodeId");
                require(host, "host");
                require(portText, "port");

                messagingServer.addNode(nodeId, host, Integer.parseInt(portText));
                return ok("Node added: " + nodeId);
            }));

            httpServer.createContext("/api/set-port", new OperationHandler(params -> {
                String nodeId = params.get("nodeId");
                String portText = params.get("port");
                require(nodeId, "nodeId");
                require(portText, "port");

                boolean ok = messagingServer.updateNodePort(nodeId, Integer.parseInt(portText));
                return ok ? ok("Port updated for " + nodeId) : error("Could not update port for " + nodeId);
            }));

            httpServer.createContext("/api/set-nodes", new OperationHandler(params -> {
                String countText = params.get("count");
                require(countText, "count");

                int count = Integer.parseInt(countText);
                messagingServer.resizeCluster(count);
                return ok("Cluster resized to " + count + " nodes.");
            }));

            httpServer.createContext("/api/init", new OperationHandler(params -> {
                String countText = params.get("count");
                require(countText, "count");

                int count = Integer.parseInt(countText);
                messagingServer.resizeCluster(count);
                return ok("Cluster initialized with " + count + " nodes.");
            }));

            httpServer.createContext("/api/scenario", new OperationHandler(params -> {
                String scenario = params.get("scenario");
                require(scenario, "scenario");
                Map<String, Object> out = messagingServer.runScenario(scenario);
                out.put("message", out.getOrDefault("summary", "Scenario completed"));
                return out;
            }));

            httpServer.start();
            boundPort = httpServer.getAddress().getPort();
            logger.info("Dashboard UI started at http://localhost:{}", boundPort);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start dashboard UI", e);
        }
    }

    public synchronized void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            boundPort = 0;
            logger.info("Dashboard UI stopped");
        }
    }

    public int getBoundPort() {
        return boundPort;
    }

    private void serveResource(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendJson(exchange, 405, error("Method not allowed"));
            return;
        }

        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                sendText(exchange, 404, "Resource not found: " + resourcePath, "text/plain; charset=utf-8");
                return;
            }

            byte[] body = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }

    private boolean isMethod(HttpExchange exchange, String expected) {
        return expected.equalsIgnoreCase(exchange.getRequestMethod());
    }

    private static void sendJson(HttpExchange exchange, int status, Map<String, Object> payload) throws IOException {
        byte[] body = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String payload, String contentType) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static Map<String, Object> ok(String message) {
        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("message", message);
        return out;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> out = new HashMap<>();
        out.put("ok", false);
        out.put("message", message);
        return out;
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
    }

    private static Map<String, String> parseRequestParams(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        Map<String, String> out = new HashMap<>();
        mergeParams(out, query);
        mergeParams(out, body);
        return out;
    }

    private static void mergeParams(Map<String, String> target, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }

        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            String key = decode(kv[0]);
            String value = kv.length > 1 ? decode(kv[1]) : "";
            target.put(key, value);
        }
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    private static class OperationHandler implements HttpHandler {
        private final Operation operation;

        private OperationHandler(Operation operation) {
            this.operation = operation;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, error("Method not allowed"));
                return;
            }

            try {
                Map<String, String> params = parseRequestParams(exchange);
                Map<String, Object> payload = operation.apply(params);
                sendJson(exchange, 200, payload);
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, error(e.getMessage()));
            } catch (RuntimeException e) {
                logger.error("Dashboard API operation failed", e);
                sendJson(exchange, 500, error("Internal error while processing request"));
            }
        }
    }

    private static Map<String, String> explanationBlock() {
        Map<String, String> out = new HashMap<>();
        out.put("leaderSelection", "Leader is selected deterministically from healthy nodes by highest nodeId.");
        out.put("leaderFailover", "When a leader is removed or fails, re-election runs and term is incremented.");
        out.put("messageDurability", "A sent message is delivered to target node and replicated as storage copies on other active nodes.");
        out.put("recovery", "Recovered nodes rejoin peers, restart listener, re-enter election, and continue accepting replicated messages.");
        out.put("faultTolerance", "Writes remain available while quorum N/2 + 1 is satisfied.");
        out.put("ordering", "Logical clock increases per message and ordering panel tracks ordered delivery.");
        return out;
    }

    @FunctionalInterface
    private interface Operation {
        Map<String, Object> apply(Map<String, String> params);
    }
}
