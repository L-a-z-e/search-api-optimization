package com.searchapioptimization.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictionSubscriber implements MessageListener {

    private final MultiLayerCacheService cacheService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String cacheKey = new String(message.getBody());
        cacheService.evictL1(cacheKey);
        log.debug("L1 evicted via Pub/Sub: {}", cacheKey);
    }
}
