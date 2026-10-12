package com.railmind.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

/**
 * Cache-aside caching, deliberately scoped to data that is safe to serve
 * stale for a bounded window:
 *
 * - "stations": station search results. Demo seed data never changes at
 *   runtime, so a long TTL (24h) is safe.
 * - "trainDetails": route/stop information (distances, offsets). Static per
 *   train in this schema (no route-editing endpoint exists yet), so a 1h TTL
 *   is generous-but-safe.
 *
 * Deliberately NOT cached: train search results and seat availability. Both
 * embed live seat counts that change on every booking/cancellation/expiry -
 * caching them would silently oversell or undersell seats, which is exactly
 * the "do not cache mutable seat availability incorrectly" failure mode this
 * config is written to avoid. Fare calculation is cheap and deterministic, so
 * caching it would save nothing worth the complexity.
 *
 * Values are JSON-serialized (GenericJackson2JsonRedisSerializer) rather than
 * Java-serialized, because the cached DTOs are records and records are not
 * Serializable by default - JSON also keeps cached entries human-readable in
 * Redis, which is useful when debugging cache behavior.
 */
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> perCacheConfig = Map.of(
                "stations", defaultConfig.entryTtl(Duration.ofHours(24)),
                "trainDetails", defaultConfig.entryTtl(Duration.ofHours(1))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheConfig)
                .build();
    }

    /**
     * Without this, a Redis outage would make @Cacheable/@CacheEvict throw and
     * fail the whole request - including requests (like admin station/train
     * writes) where caching is an optimization, not something the operation
     * should depend on. This degrades to "no caching, just hit the DB" on any
     * Redis error instead, which is the correct blast radius for a cache.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.warn("Redis cache GET failed for cache '{}': {} - falling back to the real data source.",
                        cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, Object value) {
                log.warn("Redis cache PUT failed for cache '{}': {}", cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.warn("Redis cache EVICT failed for cache '{}': {}", cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
                log.warn("Redis cache CLEAR failed for cache '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
