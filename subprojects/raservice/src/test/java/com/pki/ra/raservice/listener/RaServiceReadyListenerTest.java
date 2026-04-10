package com.pki.ra.raservice.listener;

import com.pki.ra.common.util.HelloTest;
import com.pki.ra.raservice.RaServiceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RaServiceReadyListener}.
 *
 * <p>Verifies that the listener correctly invokes the {@link HelloTest}
 * bean from the {@code common} module during the ApplicationReadyEvent phase.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RaServiceReadyListener")
class RaServiceReadyListenerTest {

    @Mock
    private ApplicationContext context;

    @Mock
    private Environment environment;

    @Mock
    private HelloTest helloTest;

    @Mock
    private ApplicationReadyEvent event;

    @Mock
    private SpringApplication springApplication;

    @InjectMocks
    private RaServiceReadyListener listener;

    @BeforeEach
    void setUp() {
        when(environment.getProperty("server.port",                 "8083"))         .thenReturn("8083");
        when(environment.getProperty("server.servlet.context-path", "/ra-api"))      .thenReturn("/ra-api");
        when(environment.getProperty("spring.application.name",     "pki-ra-raservice")).thenReturn("pki-ra-raservice");
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(context.getBeanDefinitionCount()).thenReturn(5);
        when(context.getBeanDefinitionNames()).thenReturn(new String[]{});
        when(event.getSpringApplication()).thenReturn(springApplication);
        when(springApplication.getMainApplicationClass()).thenReturn(RaServiceApplication.class);
        when(helloTest.sayHello()).thenReturn("Hello from common module HelloTest bean!");
    }

    @Test
    @DisplayName("onApplicationEvent() calls sayHello() on the HelloTest bean")
    void onApplicationEvent_invokesHelloTestSayHello() {
        listener.onApplicationEvent(event);

        verify(helloTest).sayHello();
    }

    @Test
    @DisplayName("onApplicationEvent() uses the message returned by sayHello()")
    void onApplicationEvent_usesHelloTestMessage() {
        String expected = "Hello from common module HelloTest bean!";
        when(helloTest.sayHello()).thenReturn(expected);

        listener.onApplicationEvent(event);

        // sayHello() must have been called exactly once to obtain the message
        verify(helloTest).sayHello();
    }

    @Test
    @DisplayName("onApplicationEvent() queries the total bean count from ApplicationContext")
    void onApplicationEvent_querysBeanCount() {
        listener.onApplicationEvent(event);

        verify(context).getBeanDefinitionCount();
    }

    @Test
    @DisplayName("onApplicationEvent() reads server.port from Environment")
    void onApplicationEvent_readsServerPort() {
        listener.onApplicationEvent(event);

        verify(environment).getProperty("server.port", "8083");
    }
}
