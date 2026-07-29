package com.wonbin.study_tracker.domain.state.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class StatsResponse {

    // 오늘 요약
    @Getter
    @Builder
    public static class TodaySummary {
        private int totalStudySec;
        private int totalDistractSec;
        private int sessionCount;
        private List<DistractItem> topDistracts;
    }

    // 딴짓 앱/도메인 항목
    @Getter
    @Builder
    public static class DistractItem {
        private String name;
        private int totalSec;
    }

    // 타임라인 블록
    @Getter
    @Builder
    public static class TimelineBlock {
        private LocalDateTime blockStart;
        private String category;
        private int studySec;
        private int distractSec;
        private int idleSec;
        private boolean isManualEdited;
    }

    // 세션 요약
    @Getter
    @Builder
    public static class SessionSummary {
        private Long sessionId;
        private String studyType;
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;
        private int studySec;
        private int distractSec;
        private int totalSec;
    }

    // 일별 통계 (주간/월간용)
    @Getter
    @Builder
    public static class DailyStat {
        private LocalDate date;
        private int totalStudySec;
        private int totalDistractSec;
        private int sessionCount;
    }
}
