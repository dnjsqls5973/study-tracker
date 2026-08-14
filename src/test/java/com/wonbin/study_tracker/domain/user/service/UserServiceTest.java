package com.wonbin.study_tracker.domain.user.service;

import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void getMe는_현재_사용자_정보를_반환한다() {
        User user = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(7).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var result = userService.getMe(1L);

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("a@a.com");
        assertThat(result.getName()).isEqualTo("A");
        assertThat(result.getDayChangeHour()).isEqualTo(7);
    }

    @Test
    void getMe는_존재하지_않는_사용자면_예외() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMe(999L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dayChangeHour를_갱신하면_반영된다() {
        User user = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(5).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.updateDayChangeHour(1L, 10);

        assertThat(user.getDayChangeHour()).isEqualTo(10);
    }

    @Test
    void 존재하지_않는_사용자면_예외() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateDayChangeHour(999L, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
