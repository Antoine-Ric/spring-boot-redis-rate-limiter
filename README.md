# Spring Boot Redis Rate Limiter

A **token-bucket rate limiter** for Spring Boot, backed by Redis. A servlet
filter checks every API request against a per-client token bucket; over the
limit and you get a `429 Too Many Requests` with a `Retry-After` header.

## Why this project matters (career-wise)

- **Redis depth** — one of your stated gaps. This moves you past "I use Redis
  a little" to owning caching-adjacent patterns: atomic Lua scripts, TTL
  design, key naming.
- **System design** — "design a rate limiter" is one of the most-asked system
  design interview questions. Building one for real beats memorizing the
  answer.
- **Distributed systems thinking** — the interesting part isn't the Java, it's
  *why* the check has to live in Redis + Lua instead of in-memory.

## Token bucket vs. leaky bucket (plain English)

- **Token bucket:** each client has a bucket holding tokens. A request costs
  one token; tokens drip back in over time. Burst-friendly: if the bucket is
  full you can fire off 10 requests at once, then you're throttled to the
  refill rate. This is what most real APIs (Stripe, GitHub, AWS) do.
- **Leaky bucket:** requests queue up and leak out at a fixed rate — smooth
  output, but bursts get queued or dropped. Better for shaping traffic to a
  fixed downstream capacity.

We implement **token bucket** because it's the industry default for APIs.

## Why Redis + Lua?

If the bucket lived in your app's memory, two app instances would each have
their own bucket and a client could double their limit. Redis is the shared
source of truth. The **Lua script** matters because Redis runs it
**atomically**: the "check tokens, subtract, save" steps happen as one
uninterruptible unit, so two servers can never both spend the last token.

## How to run locally

Requirements: Java 21, Maven 3.9+, Docker.

```bash
# 1. Start Redis
docker compose up -d

# 2. Run the app
./mvnw spring-boot:run        # or: mvn spring-boot:run
```

## Try it (curl)

The default config allows a burst of 10, then 1 request/second:

```bash
# First 10 succeed...
for i in $(seq 1 10); do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/hello; done
# ...the 11th gets a 429:
curl -i http://localhost:8080/api/hello
# HTTP/1.1 429 + Retry-After: 1 + {"error":"rate limit exceeded, try again shortly"}

# Watch the bucket in Redis:
docker exec -it ratelimiter-redis redis-cli HGETALL ratelimit:127.0.0.1
```

## Configuration

`src/main/resources/application.yml`:

```yaml
ratelimiter:
  capacity: 10          # max burst
  refill-per-second: 1  # sustained rate
```

Override with env vars: `REDIS_HOST`, `REDIS_PORT`.

## Project layout

```
src/main/java/com/ricardi/ratelimiter/
├── RateLimiterApplication.java
├── service/RateLimiterService.java  # token bucket via Redis Lua
├── filter/RateLimitFilter.java       # 429s over-limit requests
└── controller/DemoController.java    # demo endpoints
src/main/resources/
├── application.yml
└── rate-limit.lua                   # atomic token-bucket script
```

## Next steps checklist

- [ ] **Per-user / per-API-key limits** — key the bucket by authenticated user
      or API key instead of IP (IPs are shared behind NAT).
- [ ] **Tiered limits** — different capacity/refill per plan (free vs. pro),
      loaded from config or a database.
- [ ] **Sliding-window counter** — implement the alternative algorithm and
      compare its behavior (smoother, no burst).
- [ ] **Distributed test** — run two app instances against one Redis and prove
      the combined limit holds (this is the interview story).
- [ ] **Metrics** — expose rejected-request counts via Micrometer/Prometheus.
- [ ] **Testcontainers** — replace the localhost-dependent test with a
      Testcontainers Redis so tests run anywhere.
