package com.wonbin.study_tracker.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void IllegalArgumentException은_400으로_변환된다() {
        ResponseEntity<Map<String, String>> response = handler.handleIllegalArgument(new IllegalArgumentException("에러 메시지"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "에러 메시지");
    }

    @Test
    void IllegalStateException은_409로_변환된다() {
        ResponseEntity<Map<String, String>> response = handler.handleIllegalState(new IllegalStateException("이미 종료된 세션입니다."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", "이미 종료된 세션입니다.");
    }

    @Test
    void MethodArgumentNotValidException은_400으로_변환된다() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "dayChangeHour", "0에서 23 사이여야 합니다"));
        MethodArgumentNotValidException e = new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleMethodArgumentNotValid(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "0에서 23 사이여야 합니다");
    }
}
