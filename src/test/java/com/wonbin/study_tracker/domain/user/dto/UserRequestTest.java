package com.wonbin.study_tracker.domain.user.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void dayChangeHour가_0에서_23사이면_검증을_통과한다() throws Exception {
        UserRequest.DayChangeHourUpdate request = build(10);

        Set<ConstraintViolation<UserRequest.DayChangeHourUpdate>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void dayChangeHour가_24이면_검증에_실패한다() throws Exception {
        UserRequest.DayChangeHourUpdate request = build(24);

        Set<ConstraintViolation<UserRequest.DayChangeHourUpdate>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void dayChangeHour가_음수이면_검증에_실패한다() throws Exception {
        UserRequest.DayChangeHourUpdate request = build(-1);

        Set<ConstraintViolation<UserRequest.DayChangeHourUpdate>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    private UserRequest.DayChangeHourUpdate build(int dayChangeHour) throws Exception {
        UserRequest.DayChangeHourUpdate request = new UserRequest.DayChangeHourUpdate();
        Field field = UserRequest.DayChangeHourUpdate.class.getDeclaredField("dayChangeHour");
        field.setAccessible(true);
        field.set(request, dayChangeHour);
        return request;
    }
}
