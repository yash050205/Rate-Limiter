import java.util.concurrent.locks.ReentrantLock;

public class TokenBucket {
    private final long capacity;
    private final double refillRatePerSecond;
    private double currentTokens;
    private long lastRefillTimestamp;
    
    // We use a ReentrantLock to prevent race conditions when multiple
    // requests hit the same bucket at the exact same millisecond.
    private final ReentrantLock lock = new ReentrantLock();
    // 1. Add this new constructor to restore a bucket from a saved state
    public TokenBucket(long capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.currentTokens = capacity; // Start full
        this.lastRefillTimestamp = System.nanoTime();
    }
    public TokenBucket(long capacity, double refillRatePerSecond, double currentTokens, long lastRefillTimestamp) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.currentTokens = currentTokens;
        this.lastRefillTimestamp = lastRefillTimestamp;
    }

    // 2. Add these getters so we can save the data
    public long getCapacity() { return capacity; }
    public double getRefillRatePerSecond() { return refillRatePerSecond; }
    public long getLastRefillTimestamp() { return lastRefillTimestamp; }

    public boolean tryConsume() {
        // Lock the bucket so only one thread can modify it at a time
        lock.lock();
        try {
            refill();

            if (this.currentTokens >= 1.0) {
                this.currentTokens -= 1.0;
                return true; // ALLOW
            }
            return false; // DENY
            
        } finally {
            // Always unlock in a finally block to prevent deadlocks if an error occurs
            lock.unlock();
        }
    }

    private void refill() {
        long now = System.nanoTime();
        
        // Calculate how much time has passed in seconds
        // nanoTime is 10^9 times smaller than a second
        double timeElapsedInSeconds = (now - lastRefillTimestamp) / 1_000_000_000.0;
        
        // Calculate tokens to add
        double tokensToAdd = timeElapsedInSeconds * refillRatePerSecond;
        
        if (tokensToAdd > 0) {
            // Add tokens, but cap it at the maximum capacity
            this.currentTokens = Math.min(this.capacity, this.currentTokens + tokensToAdd);
            this.lastRefillTimestamp = now;
        }
    }
    
    public double getCurrentTokens() {
        return this.currentTokens;
    }
}