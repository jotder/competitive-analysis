package com.antigravity.companalysis.api;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal liveness endpoint for the walking skeleton. Real comparison endpoints
 * (see architecture_v2_prudent.md §9) arrive in later delivery steps.
 */
@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "service", "comp-analysis",
                "status", "ok",
                "time", Instant.now().toString());
    }
}
