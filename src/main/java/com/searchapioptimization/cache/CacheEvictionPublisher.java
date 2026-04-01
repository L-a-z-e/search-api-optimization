package com.searchapioptimization.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictionPublisher {

    private final StringRedisTemplate redisTemplate;

    public static final String CHANNEL = "cache:eviction";

    public void publish(String cacheKey) {
        redisTemplate.convertAndSend(CHANNEL, cacheKey);
        log.debug("Cache eviction published: {}", cacheKey);
    }
}
