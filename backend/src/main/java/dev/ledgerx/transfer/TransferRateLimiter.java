package dev.ledgerx.transfer;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Sliding window log in Redis, one sorted set per user scored by timestamp.
 * <p>
 * The whole trim-count-admit sequence runs as a Lua script so it is one atomic
 * step on the Redis side. Doing it as separate round trips would let two
 * concurrent requests both read a count below the limit and both be admitted,
 * which is exactly the case a rate limiter exists to stop.
 * <p>
 * A sliding window rather than a fixed bucket: fixed buckets allow a caller to
 * spend the whole allowance at the end of one window and again at the start of
 * the next, passing twice the limit through in an instant.
 */
@Component
public class TransferRateLimiter {

    private static final String SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowMillis = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]

            redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMillis)
            if redis.call('ZCARD', key) >= limit then
                return 0
            end
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, windowMillis)
            return 1
            """;

    private final StringRedisTemplate redis;
    private final TransferProperties properties;
    private final Clock clock;
    private final RedisScript<Long> admitScript;

    public TransferRateLimiter(StringRedisTemplate redis, TransferProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
        this.admitScript = new DefaultRedisScript<>(SCRIPT, Long.class);
    }

    /**
     * Consumes one slot for the user, or throws if the window is full. Consume
     * rather than peek: a check that does not reserve leaves a gap between the
     * check and the work in which the allowance can be spent by another request.
     */
    public void requireSlot(UUID userId) {
        long now = clock.millis();
        Long admitted = redis.execute(
                admitScript,
                List.of(key(userId)),
                Long.toString(now),
                Long.toString(properties.rateLimitWindow().toMillis()),
                Integer.toString(properties.rateLimitPerWindow()),
                UUID.randomUUID().toString());

        if (admitted == null || admitted == 0L) {
            throw new RateLimitExceededException();
        }
    }

    private static String key(UUID userId) {
        return "ledgerx:ratelimit:transfer:" + userId;
    }
}
