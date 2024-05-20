package com.caching.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class DynamicTTLCacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;


    private static final long INITIAL_TTL = 3600; // Initial TTL in seconds
    private static final long MAX_TTL = 10800; // Maximum TTL in seconds (3 hours)


    private static final String SORTED_SET_KEY = "expirationTimes";

    public void cacheValue(String key, Object value, Priority priority) {
        boolean keyExists = redisTemplate.hasKey(key);
        if (keyExists) {
            Map<String, Object> cacheObject = (Map<String, Object>) redisTemplate.opsForValue().get(key);
            adjustTTL(key, cacheObject);
        } else {
            Map<String, Object> cacheObject = new HashMap<>();
            cacheObject.put("data", value);
            cacheObject.put("metadata", initializeMetadata(priority));
            redisTemplate.opsForValue().set(key, cacheObject);
            long ttl = calculatePriorityBasedTTL(priority);
            redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
            updateSortedSet(key, ttl + System.currentTimeMillis()/1000);
        }
    }

    public Object getValue(String key) {
        Map<String, Object> cacheObject = (Map<String, Object>) redisTemplate.opsForValue().get(key);
        return cacheObject != null ? cacheObject.get("data") : null;
    }

    public void deleteValue(String expiredKey) {
        redisTemplate.delete(expiredKey);
        deleteFromSortedSet(expiredKey);
    }

    private Map<String, Object> initializeMetadata(Priority priority) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("initialAccessTime", System.currentTimeMillis());
        metadata.put("lastAccessTime", System.currentTimeMillis());
        metadata.put("usageCount", 0L);
        metadata.put("priority", priority);
        return metadata;
    }

    private long calculatePriorityBasedTTL(Priority priority) {
        // Example: higher priority gets longer TTL
        // The priority and the base TTL change depending on the cases and needs
        switch (priority) {
            case LOW:
                return INITIAL_TTL;
            case MEDIUM:
                return INITIAL_TTL * 2;
            case HIGH:
                return INITIAL_TTL * 3;
            default:
                return INITIAL_TTL;
        }
    }

    private void adjustTTL(String key, Map<String, Object> cacheObject) {
        String lockKey = key + ":lock";
        int maxRetries = 3;
        int retryCount = 0;
        while (retryCount < maxRetries) {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
            if (locked != null && locked) {
                try {
                    Map<String, Object> metadata = (Map<String, Object>) cacheObject.get("metadata");
                    long currentTime = System.currentTimeMillis();

                    double accessFrequencyRatio = calculateAccessFrequencyRatio(metadata, currentTime);
                    Priority priority = (Priority) metadata.get("priority");

                    // Calculate new TTL based on frequency ratio and priority
                    long newTTL = calculateTTLBasedOnFrequencyRatioAndPriority(accessFrequencyRatio, priority);

                    redisTemplate.expire(key, newTTL, TimeUnit.SECONDS);
                    updateSortedSet(key, newTTL + System.currentTimeMillis()/1000);

                    updateUsageMetadata(metadata, currentTime);
                    redisTemplate.opsForValue().set(key, cacheObject);
                } finally {
                    // Release lock
                    redisTemplate.delete(lockKey);
                }
                return;
            } else {
                exponentialBackoff(retryCount);
                retryCount++;
            }
        }
        // Handle contention after maximum retries
        throw new RuntimeException("Failed to acquire lock after maximum retries");

    }

    private double calculateAccessFrequencyRatio(Map<String, Object> metadata, long currentTime) {

        //It should be adjusted based on the case
        final double recentFrequencyWeight = 0.5;
        final double overallFrequencyWeight = 0.5;

        long initialAccessTime = (Long) metadata.get("initialAccessTime");
        long usageCount = (Long) metadata.get("usageCount") + 1;
        long lastAccessTime = (Long) metadata.get("lastAccessTime");

        long elapsedTime = (currentTime - initialAccessTime) / 1000;
        long recentAccessTime = (currentTime - lastAccessTime) / 1000;

        double recentFrequency = 1.0 / recentAccessTime;
        double overallFrequency = (double) usageCount / elapsedTime;
        return (overallFrequency * overallFrequencyWeight + recentFrequency * recentFrequencyWeight) / 2;
    }

    private void updateUsageMetadata(Map<String, Object> metadata, long currentTime) {
        metadata.put("lastAccessTime", currentTime);
        metadata.put("usageCount", (Long) metadata.get("usageCount") + 1);
    }

    private long calculateTTLBasedOnFrequencyRatioAndPriority(double accessFrequencyRatio, Priority priority) {
        long baseTTL = calculatePriorityBasedTTL(priority);
        // Extend TTL proportionally to the access frequency ratio (the formula depend on the desired adjustment depending on the case)
        //For simplicity
        long extendedTTL = (long) (baseTTL * (accessFrequencyRatio +1));
        return Math.min(extendedTTL, MAX_TTL);
    }

    public void updateSortedSet(String key, long expirationTimeMillis) {
        redisTemplate.opsForZSet().add(SORTED_SET_KEY, key, expirationTimeMillis);
    }

    public void deleteFromSortedSet(String key) {
        redisTemplate.opsForZSet().remove(SORTED_SET_KEY, key);
    }

    // Exponential backoff (100ms, 200ms, 400ms...)
    private void exponentialBackoff(int retryCount) {
        try {
            long waitTime = (long) Math.pow(2, retryCount) * 100;
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public enum Priority {
        LOW, MEDIUM, HIGH
    }
}
