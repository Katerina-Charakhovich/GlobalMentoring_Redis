package com.epam.jmp.redislab.service;

import com.epam.jmp.redislab.api.RequestDescriptor;
import com.epam.jmp.redislab.configuration.ratelimit.RateLimitRule;
import com.epam.jmp.redislab.configuration.ratelimit.RateLimitTimeInterval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisCluster;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class JedisRateLimitService implements RateLimitService {

    private final Set<RateLimitRule> rateLimitRules;
    private final JedisCluster jedisCluster;
    private static final String COLON_DELIMITER = ":";
    private static final String ACCOUNT_ID = "accountId:";
    private static final String CLIENT_IP = "clientIp:";
    private static final String REQUEST_TYPE = "requestType:";
    private static final String DESCRIPTOR_TIME = "time:";
    private static final long SECONDS_IN_MINUTE = 60;
    private static final int SECONDS_IN_HOUR = 3600;

    @Autowired
    public JedisRateLimitService(Set<RateLimitRule> rateLimitRules, JedisCluster jedisCluster1) {
        this.rateLimitRules = rateLimitRules;
        this.jedisCluster = jedisCluster1;
    }

    @Override
    public boolean shouldLimit(Set<RequestDescriptor> requestDescriptors) {
        return requestDescriptors.stream().anyMatch(this::shouldLimitDescriptor);
    }

    private boolean shouldLimitDescriptor(RequestDescriptor requestDescriptor) {
        Optional<RateLimitRule> rateLimitRuleOptional = findRateLimitRule(requestDescriptor);
        return rateLimitRuleOptional.filter(rateLimitRule -> processDescriptor(requestDescriptor, rateLimitRule))
                .isPresent();
    }

    private boolean processDescriptor(RequestDescriptor requestDescriptor,
                                      RateLimitRule rateLimitRule) {

        boolean isShouldLimit = false;

        RateLimitTimeInterval ruleTimeInterval = rateLimitRule.getTimeInterval();
        String descriptorKey = getDescriptorKey(requestDescriptor, getTimeForDescriptorKey(ruleTimeInterval));

        String descriptorKeyValue = jedisCluster.get(descriptorKey);

        if (Objects.nonNull(descriptorKeyValue) && Integer.parseInt(descriptorKeyValue) >=
                rateLimitRule.getAllowedNumberOfRequests()) {
            isShouldLimit = true;
        } else {
            setDescriptorKeyValue(descriptorKey, ruleTimeInterval);
        }
        return isShouldLimit;
    }


    private Optional<RateLimitRule> findRateLimitRule(RequestDescriptor requestDescriptor) {
        return rateLimitRules.stream()
                .filter(s -> isDescriptorFieldAppropriateToRuleField(requestDescriptor.getAccountId(), s.getAccountId()))
                .filter(s -> isDescriptorFieldAppropriateToRuleField(requestDescriptor.getRequestType(), s.getRequestType()))
                .filter(s -> isDescriptorFieldAppropriateToRuleField(s.getClientIp(), s.getClientIp()))
                .findAny();
    }

    private boolean isDescriptorFieldAppropriateToRuleField(Optional<String> descriptorField, Optional<String> ruleField) {
        boolean descriptorFieldIsEmpty = descriptorField.equals(Optional.empty());
        boolean ruleFieldIsEmpty = ruleField.equals(Optional.empty());
        boolean isDescriptorFieldAppropriate = descriptorFieldIsEmpty == ruleFieldIsEmpty;

        return (isDescriptorFieldAppropriate && !descriptorFieldIsEmpty)
                ? Objects.equals(descriptorField.get(), ruleField.get()) || ruleField.get().equals("")
                : isDescriptorFieldAppropriate;
    }

    private String getTimeForDescriptorKey(RateLimitTimeInterval rateLimitTimeInterval) {
        StringBuilder currentTime = new StringBuilder();
        if (rateLimitTimeInterval == RateLimitTimeInterval.HOUR) {
            currentTime.append(DESCRIPTOR_TIME).append(LocalDateTime.now().getHour());
        } else {
            currentTime
                    .append(DESCRIPTOR_TIME)
                    .append(LocalDateTime.now().getHour())
                    .append(COLON_DELIMITER)
                    .append(LocalDateTime.now().getMinute());
        }
        return currentTime.toString();
    }

    private String getDescriptorKey(RequestDescriptor requestDescriptor,
                                    String currentTime
    ) {
        StringBuilder descriptorKey = new StringBuilder();
        if (!isBlank(requestDescriptor.getAccountId())) {
            descriptorKey
                    .append(ACCOUNT_ID)
                    .append(requestDescriptor.getAccountId().get())
                    .append(COLON_DELIMITER);
        }
        if (!isBlank(requestDescriptor.getClientIp())) {
            descriptorKey
                    .append(CLIENT_IP)
                    .append(requestDescriptor.getClientIp().get())
                    .append(COLON_DELIMITER);
        }
        if (!isBlank(requestDescriptor.getRequestType())) {
            descriptorKey
                    .append(REQUEST_TYPE)
                    .append(requestDescriptor.getRequestType().get())
                    .append(COLON_DELIMITER);
        }
        descriptorKey.append(currentTime);

        return descriptorKey.toString();
    }

    private boolean isBlank(Optional<String> descriptorField) {
        return descriptorField.equals(Optional.empty()) || descriptorField.get().isEmpty();
    }

    private void setDescriptorKeyValue(String key,
                                       RateLimitTimeInterval rateLimitTimeInterval) {
        if (jedisCluster.exists(key)) {
            jedisCluster.incr(key);
        } else {
            jedisCluster.setex(key, rateLimitTimeInterval != RateLimitTimeInterval.HOUR
                    ? SECONDS_IN_MINUTE
                    : SECONDS_IN_HOUR, "1");
        }
    }
}
