package com.wonbin.study_tracker.domain.session.controller;

import com.wonbin.study_tracker.domain.session.dto.SessionRequest;
import com.wonbin.study_tracker.domain.session.dto.SessionResponse;
import com.wonbin.study_tracker.domain.session.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    // 세션 시작
    @PostMapping
    public ResponseEntity<SessionResponse.Detail> start(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SessionRequest.Start request) {
        return ResponseEntity.ok(sessionService.start(userId, request));
    }

    // 세션 종료
    @PatchMapping("/{sessionId}/end")
    public ResponseEntity<SessionResponse.Detail> end(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.end(userId, sessionId, false));
    }

    // 일시정지
    @PatchMapping("/{sessionId}/pause")
    public ResponseEntity<SessionResponse.Detail> pause(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.pause(userId, sessionId));
    }

    // 재개
    @PatchMapping("/{sessionId}/resume")
    public ResponseEntity<SessionResponse.Detail> resume(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @RequestParam int pausedSec) {
        return ResponseEntity.ok(sessionService.resume(userId, sessionId, pausedSec));
    }

    // 목표 시간 연장
    @PatchMapping("/{sessionId}/extend")
    public ResponseEntity<SessionResponse.Detail> extend(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody SessionRequest.Extend request) {
        return ResponseEntity.ok(sessionService.extend(userId, sessionId, request));
    }

    // 진행 중인 세션 조회
    @GetMapping("/active")
    public ResponseEntity<SessionResponse.Detail> getActive(
            @AuthenticationPrincipal Long userId) {
        return sessionService.getActiveSessionOrEmpty(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 특정 세션 조회
    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse.Detail> getSession(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.getSession(userId, sessionId));
    }

    // 세션 노트 조회
    @GetMapping("/{sessionId}/notes")
    public ResponseEntity<List<SessionResponse.LogNote>> getNotes(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.getNotes(userId, sessionId));
    }

    // 세션 로그 요약 조회 (팝업 데이터)
    @GetMapping("/{sessionId}/log-summary")
    public ResponseEntity<List<SessionResponse.LogSummaryItem>> getLogSummary(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.getLogSummary(userId, sessionId));
    }

    // 팝업 완료 (최종 저장)
    @PostMapping("/{sessionId}/finalize")
    public ResponseEntity<SessionResponse.Detail> finalize(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody SessionRequest.Finalize request) {
        return ResponseEntity.ok(sessionService.finalizeSession(userId, sessionId, request));
    }
}
