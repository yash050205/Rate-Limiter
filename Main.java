public class Main {
    public static void main(String[] args) throws InterruptedException {
        // Bucket with capacity of 5, refilling at 1 token per second
        TokenBucket bucket = new TokenBucket(5, 1);

        System.out.println("--- Initial Burst ---");
        for (int i = 0; i < 6; i++) {
            System.out.println("Request " + (i+1) + ": " + (bucket.tryConsume() ? "ALLOW" : "DENY"));
        }
        
        System.out.println("\nWaiting 3 seconds...");
        Thread.sleep(3000);
        
        System.out.println("\n--- After Wait ---");
        for (int i = 0; i < 4; i++) {
            System.out.println("Request " + (i+1) + ": " + (bucket.tryConsume() ? "ALLOW" : "DENY"));
        }
    }
}