package com.handsoap.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BadRequestExceptionTest {

    @Test
    @DisplayName("應保留傳入的錯誤訊息")
    void shouldKeepMessage() {
        BadRequestException ex = new BadRequestException("Category with this name already exists");

        assertThat(ex.getMessage()).isEqualTo("Category with this name already exists");
    }

    @Test
    @DisplayName("應為 RuntimeException，可在不宣告的情況下被拋出")
    void shouldBeRuntimeException() {
        BadRequestException ex = new BadRequestException("invalid");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}