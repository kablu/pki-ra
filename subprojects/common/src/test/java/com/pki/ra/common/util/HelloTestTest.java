package com.pki.ra.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HelloTest} bean.
 */
@DisplayName("HelloTest")
class HelloTestTest {

    private HelloTest helloTest;

    @BeforeEach
    void setUp() {
        helloTest = new HelloTest();
    }

    @Test
    @DisplayName("sayHello() returns the expected greeting message")
    void sayHello_returnsExpectedMessage() {
        String result = helloTest.sayHello();

        assertThat(result).isEqualTo("Hello from common module HelloTest bean!");
    }

    @Test
    @DisplayName("sayHello() returns a non-null value")
    void sayHello_returnsNonNull() {
        assertThat(helloTest.sayHello()).isNotNull();
    }

    @Test
    @DisplayName("sayHello() returns a non-empty string")
    void sayHello_returnsNonEmpty() {
        assertThat(helloTest.sayHello()).isNotBlank();
    }
}
