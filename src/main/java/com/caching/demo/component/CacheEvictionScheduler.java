package com.caching.demo.component;

import com.caching.demo.service.DynamicTTLCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.Set;

@Component
public class CacheEvictionScheduler {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private Jedis jedis;

    @Autowired
    private DynamicTTLCacheService cacheService;

    @Value("${spring.redis.max-memory}")
    private String maxMemoryConfig;

    private static final String SORTED_SET_KEY = "expirationTimes";

    private static final long EVICTION_THRESHOLD = 85;
    private static final long SAFE_THRESHOLD = 60;
    private static final int DATA_PERCENTAGE_TO_EVICT = 10;


    @Scheduled(fixedRate = 600000) // Run every 10 minutes
    public void checkCacheSpaceUsage() {
        double maxMemoryBytes = parseMaxMemoryConfig(maxMemoryConfig);
        double cacheUsagePercentage = (double) parseUsedMemory() / maxMemoryBytes * 100;
        redisTemplate.opsForZSet().removeRangeByScore(SORTED_SET_KEY, 0, System.currentTimeMillis());

        if (cacheUsagePercentage >= EVICTION_THRESHOLD) {
            evictObjects(10 * 60 * 1000);
            // Evict objects with less than 10 minutes left before expiration
            cacheUsagePercentage = (double) parseUsedMemory() / maxMemoryBytes * 100;
            if (cacheUsagePercentage >= SAFE_THRESHOLD) {
                long targetSpace = (long) (SAFE_THRESHOLD/100 * maxMemoryBytes);
                evictToFreeSpace(targetSpace);
            }
        }
    }

    private void evictObjects(long minRemainingTTL) {
        Set<Object> keys = redisTemplate.opsForZSet().rangeByScore(SORTED_SET_KEY, 0, -1);

        if (keys != null) {
            for (Object key : keys) {
                long remainingTTL = redisTemplate.opsForZSet().score(SORTED_SET_KEY, key).longValue();
                if (remainingTTL - System.currentTimeMillis() < minRemainingTTL) {
                    redisTemplate.delete((String)key);
                }
            }
        }
    }

    private void evictToFreeSpace(long targetSpace) {
        long currentCacheSize = parseUsedMemory();
        int percentage = DATA_PERCENTAGE_TO_EVICT;
        Set<Object> keys = redisTemplate.opsForZSet().rangeByScore(SORTED_SET_KEY, 0, -1);
        int numberOfKeysToProcess = (int) (keys.size() * percentage / 100.0);
        while (currentCacheSize > targetSpace) {

            int keysProcessed = 0;

            for (Object key : keys) {
                redisTemplate.delete((String) key);
                keysProcessed++;
                if (keysProcessed >= numberOfKeysToProcess) {
                    redisTemplate.opsForZSet().removeRangeByScore(SORTED_SET_KEY, 0, redisTemplate.opsForZSet().score(SORTED_SET_KEY, key));
                    break;
                }
            }

            currentCacheSize = parseUsedMemory();
            if (currentCacheSize <= targetSpace) {
                break;
            }

            percentage += 10;
            if (percentage > 100) {
                break;
            }
            keys = redisTemplate.opsForZSet().rangeByScore(SORTED_SET_KEY, 0, -1);
        }

    }

    private double parseMaxMemoryConfig(String maxMemoryConfig) {
        // For simplicity, we assume the value is specified in megabytes (MB)
        return Double.parseDouble(maxMemoryConfig) * 1024 * 1024;
    }

    private long parseUsedMemory() {
        String memoryInfo = jedis.info("memory");
        String[] lines = memoryInfo.split("\\r?\\n");

        for (String line : lines) {
            if (line.startsWith("used_memory:")) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    return Long.parseLong(parts[1].trim());
                }
            }
        }
        return -1;
    }

    //It helps to identify how much memory taken from objects and compare it to total used memory by redis
    public long calculateCurrentCacheSizeInBytes() {
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) redisTemplate.getDefaultSerializer();
        long totalSize = 0;
        for (Object key : redisTemplate.opsForZSet().range(SORTED_SET_KEY, 0, -1)) {
            Map<String, Object> cacheObject = (Map<String, Object>) redisTemplate.opsForValue().get(key);
            String serializedValue = new String(serializer.serialize(cacheObject));
            byte[] bytes = serializedValue.getBytes();
            totalSize += bytes.length;
        }
        return totalSize;
    }

}