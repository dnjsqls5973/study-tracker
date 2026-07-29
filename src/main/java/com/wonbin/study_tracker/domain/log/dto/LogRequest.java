package com.wonbin.study_tracker.domain.log.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

public class LogRequest {

    @Getter
    public static class ActivityBatch {
        @NotNull
        private Long sessionId;

        @NotNull
        private Long deviceId;

        @NotNull
        private List<ActivityItem> logs;

    }

    @Getter
    public static class ActivityItem {
        @NotBlank
        private String appName;

        private String windowTitle;

        @NotNull
        private LocalDateTime startedAt;

        private int durationSec;
        private boolean isIdle;
    }

    @Getter
    public static class BrowserBatch {
        @NotNull
        private Long sessionId;

        @NotNull
        private Long deviceId;

        @NotNull
        private List<BrowserItem> logs;
    }

    @Getter
    public static class BrowserItem {
        @NotBlank
        private String domain;

        private String pageTitle;

        @NotNull
        private LocalDateTime startedAt;

        private int durationSec;
    }

    @Getter
    public static class FocusEvent {
        @NotNull
        private Long deviceId;

        @NotBlank
        private String event; // FOCUS_START or FOCUS_END

        @NotNull
        private LocalDateTime timestamp;
    }

}
