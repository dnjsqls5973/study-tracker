package com.wonbin.study_tracker.domain.classification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

public class ClassificationRequest {

    @Getter
    public static class Create {
        @NotBlank
        private String type;

        @NotBlank
        private String value;

        @NotBlank
        private String category;
    }

    @Getter
    public static class Update {
        @NotBlank
        private String category;
    }
}
