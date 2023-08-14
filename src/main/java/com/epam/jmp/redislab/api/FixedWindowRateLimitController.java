package com.epam.jmp.redislab.api;

import com.epam.jmp.redislab.service.JedisRateLimitService;
import com.epam.jmp.redislab.service.RateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ratelimit/fixedwindow")
public class FixedWindowRateLimitController {

    private final JedisRateLimitService rateLimitService;

    @Autowired
    public FixedWindowRateLimitController( JedisRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public ResponseEntity<Void> shouldRateLimit(@RequestBody RateLimitRequest rateLimitRequest) {
        if (rateLimitService.shouldLimit(rateLimitRequest.getDescriptors())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Void> get() {
        return ResponseEntity.ok().build();
    }

}
