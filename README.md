# High Traffic URL Shortener
**Stack**: Java 17 · Spring Boot 3.2 · Redis · Apache Kafka · MySQL 8 · Docker

---

## Architecture Overview

```
Client → Load Balancer → Spring Boot (x3 instances)
                               ├── Redis (cache, rate-limit)
                               ├── MySQL (primary store)
                               └── Kafka ──► Analytics Consumer ──► MySQL (click_events)
```

### Key Design Decisions

| Concern | Solution | Reason |
|---|---|---|
| Short code generation | Base62 random (7 chars) | 62^7 ≈ 3.5 trillion codes |
| Hot path caching | Redis with 24h TTL | Avoids DB on every redirect |
| Negative caching | Redis `__NOT_FOUND__` value | Prevents DB DDoS on invalid codes |
| Analytics | Kafka async + batch consumer | Redirect never blocked by DB writes |
| Click counting | Atomic SQL `UPDATE ... SET count + 1` | No optimistic lock contention |
| Rate limiting | Redis sliding window (per IP) | Stateless app, distributed safe |
| Expired URLs | Scheduled job 2AM daily | Low-overhead bulk deactivation |

---

## API Endpoints

### POST /api/v1/shorten — Create short URL
```bash
curl -X POST http://localhost:8080/api/v1/shorten \
  -u admin:admin123 \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/very/long/path?param=value",
    "title": "Example Page",
    "customAlias": "my-link",       
    "expiresAt": "2025-12-31T23:59:59"
  }'
```
Response:
```json
{
  "success": true,
  "data": {
    "shortUrl": "http://localhost:8080/my-link",
    "shortCode": "my-link",
    "originalUrl": "https://example.com/very/long/path?param=value",
    "expiresAt": "2025-12-31T23:59:59",
    "createdAt": "2024-01-15T10:30:00"
  }
}
```

### GET /{shortCode} — Redirect (301)
```bash
curl -L http://localhost:8080/my-link
```

### GET /api/v1/stats/{shortCode} — Analytics
```bash
curl http://localhost:8080/api/v1/stats/my-link
```
Response:
```json
{
  "success": true,
  "data": {
    "shortCode": "my-link",
    "totalClicks": 1428,
    "clicksByCountry": { "IN": 820, "US": 310, "GB": 120 },
    "clicksByDevice": { "mobile": 900, "desktop": 480, "tablet": 48 },
    "clicksByDay": { "2024-01-14": 230, "2024-01-15": 198 }
  }
}
```

### GET /api/v1/urls — List your URLs (auth required)
```bash
curl http://localhost:8080/api/v1/urls -u admin:admin123
```

### DELETE /api/v1/urls/{shortCode} — Deactivate URL
```bash
curl -X DELETE http://localhost:8080/api/v1/urls/my-link -u admin:admin123
```

---

## Local Development

### Prerequisites
- Java 17+, Maven 3.8+, Docker + Docker Compose

### 1. Start Infrastructure
```bash
docker-compose up -d mysql redis zookeeper kafka
```
Wait for services to be healthy (~30 seconds).

### 2. Run the App
```bash
mvn spring-boot:run
```
Or with custom config:
```bash
DB_HOST=localhost DB_USER=urluser DB_PASS=urlpass \
REDIS_HOST=localhost KAFKA_BROKERS=localhost:9092 \
mvn spring-boot:run
```

### 3. Run Full Stack
```bash
docker-compose up --build
```

### 4. Run Tests
```bash
mvn test
```

---

## Free Cloud Deployment Guide

### Option A: Railway.app (Recommended — easiest)

Railway provides MySQL, Redis, and app hosting for free (500 hrs/month).

```bash
# 1. Install Railway CLI
npm install -g @railway/cli

# 2. Login
railway login

# 3. Create project
railway init

# 4. Add MySQL plugin
railway add --plugin mysql

# 5. Add Redis plugin
railway add --plugin redis

# 6. Deploy app
railway up

# 7. Set environment variables
railway variables set BASE_URL=https://your-app.railway.app
railway variables set ADMIN_PASS=your_secure_password
```

**Note**: Kafka is not available on Railway free tier.  
For free Kafka, use **Upstash Kafka** (kafka.upstash.com) — 10,000 msgs/day free.
```bash
railway variables set KAFKA_BROKERS=your-upstash-bootstrap-url:9092
```

### Option B: Render.com + Upstash

1. **MySQL**: PlanetScale (planetscale.com) — 5GB free, serverless MySQL  
2. **Redis**: Upstash Redis (upstash.com) — 10,000 req/day free  
3. **Kafka**: Upstash Kafka — 10,000 msg/day free  
4. **App**: Render.com — free tier, auto-deploys from GitHub

**Render.com setup:**
- Connect your GitHub repo
- Select "Web Service" → Docker
- Set environment variables:
  ```
  DB_HOST=<planetscale-host>
  DB_USER=<planetscale-user>
  DB_PASS=<planetscale-pass>
  REDIS_HOST=<upstash-host>
  REDIS_PORT=<upstash-port>
  REDIS_PASS=<upstash-token>
  KAFKA_BROKERS=<upstash-kafka-bootstrap>
  BASE_URL=https://your-app.onrender.com
  ```

### Option C: Fly.io

```bash
# Install flyctl
curl -L https://fly.io/install.sh | sh

# Create app
fly launch --name my-url-shortener

# Create MySQL (via Fly Postgres)
fly postgres create --name url-db

# Attach DB
fly postgres attach url-db

# Deploy
fly deploy

# Set secrets
fly secrets set REDIS_HOST=upstash-host KAFKA_BROKERS=upstash-kafka
```

---

## Production Scaling Checklist

- [ ] Run 3+ Spring Boot instances behind a load balancer (Nginx / AWS ALB)
- [ ] Redis Cluster mode (3+ shards) for HA
- [ ] Kafka 3-node cluster with replication factor ≥ 2
- [ ] MySQL read replicas for stats queries
- [ ] CDN in front of the redirect endpoint (Cloudflare free tier)
- [ ] Replace HTTP Basic auth with JWT (Spring Security + JJWT)
- [ ] Add GeoIP lookup in the Kafka consumer (MaxMind GeoLite2)
- [ ] Prometheus + Grafana for metrics (`/actuator/prometheus`)
- [ ] Partition `click_events` table by month for large volumes
- [ ] Consider Bloom filter (Redis + RedisBloom) to replace negative caching

---

## Performance Benchmarks (expected)

| Scenario | Throughput | P99 Latency |
|---|---|---|
| Redirect (cache hit) | ~50,000 req/s | < 5ms |
| Redirect (cache miss) | ~5,000 req/s | < 50ms |
| Shorten | ~2,000 req/s | < 100ms |
| Stats query (cached) | ~10,000 req/s | < 10ms |
