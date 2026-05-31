package com.pki.ca.caservice.config;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Security;

@Configuration
@EnableConfigurationProperties(CaProperties.class)
public class BouncyCastleConfig {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
        }
    }

    @Bean
    public BouncyCastleProvider bouncyCastleProvider() {
        return (BouncyCastleProvider) Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
    }
}
