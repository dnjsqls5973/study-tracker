package com.wonbin.study_tracker.domain.state.controller;


import com.wonbin.study_tracker.domain.state.dto.StatsResponse;
import com.wonbin.study_tracker.domain.state.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statSService;

    @GetMapping("/today")
    public ResponseEntity<StatsResponse.TodaySummary> getToday(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(statSService.getTodaySummary(userId));
    }

    @GetMapping("sessions")
    public ResponseEntity<List<StatsResponse.SessionSummary>> getSessions(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(statSService.getSessions(userId, date));
    }

    // 달력용 월별 순공 시간 (날짜: 초 단위 순공 시간, 공부한 날만 포함)
    @GetMapping("/calendar")
    public ResponseEntity<java.util.Map<String, Integer>> getCalendar(
            @AuthenticationPrincipal Long userId,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(statSService.getCalendar(userId, year, month));
    }

    @GetMapping("/weekly")
    public ResponseEntity<List<StatsResponse.DailyStat>> getWeekly(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        return ResponseEntity.ok(statSService.getWeeklyStats(userId, startDate));
    }

    @GetMapping("/monthly")
    public ResponseEntity<List<StatsResponse.DailyStat>> getMonthly(
            @AuthenticationPrincipal Long userId,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(statSService.getMonthlyStats(userId, year, month));
    }

    @GetMapping("/weekly-notes")
    public ResponseEntity<List<StatsResponse.NoteDailySummary>> getWeeklyNotes(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        return ResponseEntity.ok(statSService.getWeeklyNotes(userId, startDate));
    }

    @GetMapping("/monthly-notes")
    public ResponseEntity<List<StatsResponse.NoteDailySummary>> getMonthlyNotes(
            @AuthenticationPrincipal Long userId,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(statSService.getMonthlyNotes(userId, year, month));
    }
}
