package com.handsoap.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceNotFoundExceptionTest {

    @Test
    @DisplayName("應保留傳入的錯誤訊息")
    void shouldKeepMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Essential oil not found with id: 99");

        assertThat(ex.getMessage()).isEqualTo("Essential oil not found with id: 99");
    }

    @Test
    @DisplayName("應為 RuntimeException，可在不宣告的情況下被拋出")
    void shouldBeRuntimeException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("missing");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}