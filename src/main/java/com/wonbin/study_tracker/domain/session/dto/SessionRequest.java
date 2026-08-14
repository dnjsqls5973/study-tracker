package com.wonbin.study_tracker.domain.session.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

public class SessionRequest {

    @Getter
    public static class Start {
        @NotBlank
        private String studyType; //OFFLINE or ONLINE

        @Min(60)
        private Integer targetSec; // 선택 기능 (null 가능)
    }

    @Getter
    public static class Extend {
        @Min(60)
        private int additionalSec;
    }

    @Getter
    public static class LogNoteItem {
        @NotBlank
        private String logType;   // APP / DOMAIN

        @NotBlank
        private String logValue;  // idea64.exe / youtube.com

        @NotBlank
        private String category;  // STUDY / DISTRACT / NEUTRAL

        private String memo;      // STUDY일 때만 필수 (백엔드에서 검증)
    }

    @Getter
    public static class Finalize {
        @NotNull
        private List<LogNoteItem> notes;
    }
}
