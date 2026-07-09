# Distributed Rate Limiter Service

A framework-free, highly concurrent HTTP rate-limiting microservice built from scratch in Core Java. This project implements the **Token Bucket algorithm** to manage API traffic, prevent abuse, and handle massive concurrency without race conditions. 

It includes an asynchronous persistence layer for crash recovery and a real-time developer dashboard to monitor global traffic spikes.

## 🚀 Key Features

* **Token Bucket Algorithm:** Provides smooth traffic flow and handles sudden bursts with O(1) time complexity per request.
* **Thread-Safe Architecture:** Utilizes `ReentrantLock`, `AtomicInteger`, and `ConcurrentHashMap` to guarantee zero synchronization faults or "double-spends" during high-concurrency spikes.
* **No External Frameworks:** Built entirely using Java's built-in `com.sun.net.httpserver.HttpServer` and concurrency utilities to demonstrate deep understanding of core network and threading concepts.
* **State Persistence:** Implements asynchronous file I/O to periodically snapshot client token states to a CSV, enabling seamless recovery after a server restart.
* **Real-Time Monitoring UI:** A vanilla JavaScript and Chart.js dashboard that polls the server's metrics endpoint to visualize global API traffic (Allowed vs. Denied) in real-time.
* **Built-In Load Tester:** Includes a dedicated multi-threaded Java client that spawns 50 parallel threads to blast the server with 500 concurrent requests to prove thread safety.

## 🛠️ Tech Stack

* **Backend:** Java 11+ (Core Concurrency, `HttpServer`)
* **Frontend:** HTML5, Vanilla JavaScript, CSS, Bootstrap
* **Visualization:** Chart.js (Stacked Bar Charts for real-time RPS metrics)
* **Architecture:** Microservices, Distributed Systems, Event Polling

## ⚙️ How to Run Locally

### 1. Start the Server
Compile and run the main server file. It will spin up on `localhost:8080`.
```bash
javac RateLimiterServer.java
java RateLimiterServer
2. Open the Dashboard
Navigate to the project folder and double-click index.html to open it in any modern web browser. The dashboard will automatically begin polling the server for live metrics.

3. Run the Stress Test
To see the concurrency engine in action, compile and run the provided load tester while watching the web dashboard.

Bash
javac LoadTest.java
java LoadTest
You will immediately see a massive traffic spike on the dashboard, correctly categorizing the 5 allowed requests and 495 dropped requests.

📡 API Endpoints
GET /api/check?client_key={id}
Checks if the client has enough tokens to proceed.

200 OK: { "status": "ALLOW", "remaining": 4.00 }

429 Too Many Requests: { "status": "DENY", "message": "Too Many Requests" }

GET /api/metrics
Exposes the global state of the server for the monitoring dashboard.

200 OK: { "allowed": 105, "denied": 843 }

🧠 System Design Highlights
Lock Granularity: Instead of locking the entire server for every request, ReentrantLocks are applied per client bucket. This means Client A pulling a token never blocks Client B, maximizing overall throughput.

Atomic Counters: Global metrics are tracked using AtomicInteger, ensuring that 50 threads updating the dashboard analytics at the exact same millisecond never overwrite each other.

Decoupled Monitoring: The web UI operates independently of the request pipeline, preventing dashboard rendering from slowing down core API response times.
