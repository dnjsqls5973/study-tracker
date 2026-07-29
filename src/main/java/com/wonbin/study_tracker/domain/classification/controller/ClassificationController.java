package com.wonbin.study_tracker.domain.classification.controller;

import com.wonbin.study_tracker.domain.classification.dto.ClassificationRequest;
import com.wonbin.study_tracker.domain.classification.dto.ClassificationResponse;
import com.wonbin.study_tracker.domain.classification.service.ClassificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classifications")
@RequiredArgsConstructor
public class ClassificationController {

    private final ClassificationService classificationService;

    @GetMapping
    public ResponseEntity<List<ClassificationResponse>> getList(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(classificationService.getList(userId));
    }

    @PostMapping
    public ResponseEntity<ClassificationResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ClassificationRequest.Create request) {
        return ResponseEntity.ok(classificationService.create(userId, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ClassificationResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ClassificationRequest.Update request) {
        return ResponseEntity.ok(classificationService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        classificationService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}