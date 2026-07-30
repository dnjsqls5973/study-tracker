package com.wonbin.study_tracker.domain.state.dto;

import com.wonbin.study_tracker.domain.session.dto.SessionResponse;
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
        private List<SessionResponse.LogNote> recentNotes;   // 가장 최근 종료 세션의 노트 목록
        private List<DistractItem> studyDetails;             // 오늘 앱/도메인별 순공 시간 상세
        private List<DistractItem> distractDetails;          // 오늘 앱/도메인별 딴짓 시간 상세
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

    // 날짜별 공부 메모 요약 항목
    @Getter
    @Builder
    public static class StudyNoteItem {
        private String logValue;
        private String category;
        private String memo;
    }

    // 세션별 공부 메모 그룹
    @Getter
    @Builder
    public static class SessionNoteGroup {
        private Long sessionId;
        private String studyType;
        private List<StudyNoteItem> notes;
    }

    // 날짜별 노트 요약 (주별/월별 노트 요약용)
    @Getter
    @Builder
    public static class NoteDailySummary {
        private LocalDate date;
        private int totalStudySec;
        private List<SessionNoteGroup> sessions;
    }
}
