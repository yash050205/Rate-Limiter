import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.util.Map;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
public class RateLimiterServer {
    
    // Thread-safe map to hold a unique TokenBucket for every client
    private static final ConcurrentHashMap<String, TokenBucket> clientBuckets = new ConcurrentHashMap<>();
    
    // Default limits for new clients (e.g., 5 requests burst, 1 request per second)
    private static final long DEFAULT_CAPACITY = 5;
    private static final double DEFAULT_REFILL_RATE = 1.0;
    private static final String STATE_FILE = "limiter_state.csv";
    private static final AtomicInteger globalAllowed = new AtomicInteger(0);
    private static final AtomicInteger globalDenied = new AtomicInteger(0);
    public static void main(String[] args) throws IOException {
        // 1. Load previous state before starting the server
        loadState();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        
        server.createContext("/api/check", new RateLimitHandler());
        // --- ADD THIS LINE ---
        server.createContext("/api/metrics", new MetricsHandler());
        server.setExecutor(Executors.newFixedThreadPool(10)); 
        server.start();
        
        System.out.println("Rate Limiter API is running on http://localhost:8080");

        // 2. Start the background snapshot thread
        startSnapshotThread();
    }

    // --- NEW PERSISTENCE METHODS ---

    private static void startSnapshotThread() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // Run the saveState method every 5 seconds in the background
        scheduler.scheduleAtFixedRate(() -> {
            try {
                saveState();
            } catch (IOException e) {
                System.err.println("Failed to save state: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private static void saveState() throws IOException {
        // Write the map to a CSV file: clientKey,capacity,refillRate,currentTokens,lastRefillTimestamp
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(STATE_FILE))) {
            for (Map.Entry<String, TokenBucket> entry : clientBuckets.entrySet()) {
                String key = entry.getKey();
                TokenBucket b = entry.getValue();
                writer.write(String.format("%s,%d,%f,%f,%d\n", 
                    key, b.getCapacity(), b.getRefillRatePerSecond(), b.getCurrentTokens(), b.getLastRefillTimestamp()));
            }
        }
    }

    private static void loadState() {
        File file = new File(STATE_FILE);
        if (!file.exists()) return; // Nothing to load on first run

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 5) {
                    String key = parts[0];
                    long capacity = Long.parseLong(parts[1]);
                    double rate = Double.parseDouble(parts[2]);
                    double tokens = Double.parseDouble(parts[3]);
                    long timestamp = Long.parseLong(parts[4]);
                    
                    clientBuckets.put(key, new TokenBucket(capacity, rate, tokens, timestamp));
                }
            }
            System.out.println("Successfully restored " + clientBuckets.size() + " client buckets from disk.");
        } catch (IOException | NumberFormatException e) {
            System.err.println("Could not load previous state, starting fresh.");
        }
    }
    static class RateLimitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 1. Parse the URL query to get the client_key
            String query = exchange.getRequestURI().getQuery();
            String clientKey = getClientKeyFromQuery(query);

            if (clientKey == null || clientKey.isEmpty()) {
                sendResponse(exchange, 400, "Missing client_key parameter");
                return;
            }

            // 2. Fetch the bucket for this client, or create a new one if they don't exist
            TokenBucket bucket = clientBuckets.computeIfAbsent(
                clientKey, 
                k -> new TokenBucket(DEFAULT_CAPACITY, DEFAULT_REFILL_RATE)
            );

            // 3. Check the limit using your thread-safe engine
            boolean isAllowed = bucket.tryConsume();
            if (isAllowed) {
                globalAllowed.incrementAndGet();
                String response = String.format("{\"status\": \"ALLOW\", \"remaining\": %.2f}", bucket.getCurrentTokens());
                exchange.getResponseHeaders().add("X-RateLimit-Limit", String.valueOf(DEFAULT_CAPACITY));
                sendResponse(exchange, 200, response);
            } else {
                globalDenied.incrementAndGet();
                String response = "{\"status\": \"DENY\", \"message\": \"Too Many Requests\"}";
                sendResponse(exchange, 429, response);
            }
        }

        private String getClientKeyFromQuery(String query) {
            if (query == null) return null;
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length > 1 && pair[0].equals("client_key")) {
                    return pair[1];
                }
            }
            return null;
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            //exchange.getResponseHeaders().add("Content-Type", "application/json");
            // Add this line to allow your frontend to talk to your backend
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
    static class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Allow the frontend to read this endpoint
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            
            // Send the total lifetime counts as JSON
            String response = String.format("{\"allowed\": %d, \"denied\": %d}", 
                globalAllowed.get(), globalDenied.get());
                
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}