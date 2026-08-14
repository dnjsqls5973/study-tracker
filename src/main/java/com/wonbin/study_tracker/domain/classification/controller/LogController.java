package com.wonbin.study_tracker.domain.classification.controller;

import com.wonbin.study_tracker.domain.log.dto.LogRequest;
import com.wonbin.study_tracker.domain.log.service.LogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @PostMapping("/activity-logs")
    public ResponseEntity<Map<String, Integer>> saveActivityLogs(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody LogRequest.ActivityBatch request) {
        int count = logService.saveActivityLogs(userId, request);
        return ResponseEntity.ok(Map.of("saved", count));
    }

    @PostMapping("/browser-logs")
    public ResponseEntity<Map<String, Integer>> saveBrowserLogs(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody LogRequest.BrowserBatch request) {
        int count = logService.saveBrowserLogs(userId, request);
        return ResponseEntity.ok(Map.of("saved", count));
    }
}
