package com.wonbin.study_tracker.domain.user.controller;

import com.wonbin.study_tracker.domain.user.dto.UserRequest;
import com.wonbin.study_tracker.domain.user.dto.UserResponse;
import com.wonbin.study_tracker.domain.user.service.UserDeletionService;
import com.wonbin.study_tracker.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserDeletionService userDeletionService;
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse.Me> getMe(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userService.getMe(userId));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Long userId) {
        userDeletionService.deleteAccount(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/day-change-hour")
    public ResponseEntity<Void> updateDayChangeHour(@AuthenticationPrincipal Long userId,
                                                      @Valid @RequestBody UserRequest.DayChangeHourUpdate request) {
        userService.updateDayChangeHour(userId, request.getDayChangeHour());
        return ResponseEntity.noContent().build();
    }
}
