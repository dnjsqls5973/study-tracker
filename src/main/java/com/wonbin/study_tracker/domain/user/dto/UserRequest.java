package com.wonbin.study_tracker.domain.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;

public class UserRequest {

    @Getter
    public static class DayChangeHourUpdate {
        @Min(0)
        @Max(23)
        private int dayChangeHour;
    }
}
