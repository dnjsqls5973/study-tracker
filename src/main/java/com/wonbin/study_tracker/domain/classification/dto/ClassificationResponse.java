package com.wonbin.study_tracker.domain.classification.dto;

import com.wonbin.study_tracker.domain.classification.entity.AppClassification;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClassificationResponse {
    private Long id;
    private String type;
    private String value;
    private String category;

    public static ClassificationResponse from(AppClassification c) {
        return ClassificationResponse.builder()
                .id(c.getId())
                .type(c.getType())
                .value(c.getValue())
                .category(c.getCategory())
                .build();
    }

}
