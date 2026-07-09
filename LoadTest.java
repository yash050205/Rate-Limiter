import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTest {
    public static void main(String[] args) throws InterruptedException {
        int totalRequests = 500;
        ExecutorService executor = Executors.newFixedThreadPool(50); // 50 parallel users
        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger denied = new AtomicInteger(0);

        System.out.println("Firing 500 concurrent requests...");
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            executor.execute(() -> {
                try {
                    URL url = new URL("http://localhost:8080/api/check?client_key=load_test_user");
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod("GET");
                    int status = con.getResponseCode();
                    
                    if (status == 200) allowed.incrementAndGet();
                    else if (status == 429) denied.incrementAndGet();
                    
                } catch (Exception e) {
                    System.out.println("Request failed");
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        System.out.println("--- Load Test Complete ---");
        System.out.println("Time taken: " + (endTime - startTime) + " ms");
        System.out.println("Allowed (200): " + allowed.get());
        System.out.println("Denied (429): " + denied.get());
    }
}
