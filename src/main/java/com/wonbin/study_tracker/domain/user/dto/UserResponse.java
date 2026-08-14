package com.wonbin.study_tracker.domain.user.dto;

import com.wonbin.study_tracker.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

public class UserResponse {

    @Getter
    @Builder
    public static class Me {
        private Long userId;
        private String email;
        private String name;
        private int dayChangeHour;

        public static Me from(User user) {
            return Me.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .name(user.getName())
                    .dayChangeHour(user.getDayChangeHour())
                    .build();
        }
    }
}
