package com.pki.ra.common.util;

import org.springframework.stereotype.Component;

/**
 * Simple shared bean demonstrating cross-module bean usage.
 *
 * <p>Registered in the {@code common} module and consumed by any
 * module that includes {@code common} as a dependency (e.g. raservice).
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Component
public class HelloTest {

    /**
     * Returns a greeting message.
     *
     * @return greeting string
     */
    public String sayHello() {
        return "Hello from common module HelloTest bean!";
    }
}
