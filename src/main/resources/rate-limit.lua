-- rate-limit.lua
-- Token-bucket rate limiter. Redis executes this whole script atomically,
-- so concurrent app instances can never both spend the last token.
--
-- KEYS[1] = bucket key, e.g. "ratelimit:1.2.3.4"
-- ARGV[1] = capacity (max tokens)
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = now, in milliseconds
-- ARGV[4] = tokens requested (1 per request)
-- Returns 1 if allowed, 0 if rate limited.

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_per_sec = tonumber(ARGV[2])
local now_ms = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(data[1])
local ts = tonumber(data[2])

if tokens == nil then
  -- First time we see this client: bucket starts full.
  tokens = capacity
  ts = now_ms
else
  -- Refill based on time passed since last request.
  local elapsed_sec = math.max(0, now_ms - ts) / 1000.0
  tokens = math.min(capacity, tokens + elapsed_sec * refill_per_sec)
  ts = now_ms
end

local allowed = 0
if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
end

redis.call('HSET', key, 'tokens', tokens, 'ts', ts)
-- Let idle buckets expire once they would be full again, so Redis
-- doesn't fill up with keys for clients that went away.
local ttl_ms = math.ceil((capacity / refill_per_sec) * 1000) + 2000
redis.call('PEXPIRE', key, ttl_ms)

return allowed
