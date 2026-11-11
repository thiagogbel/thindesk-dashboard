package com.pfc.thindesk.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Relogio injetavel: permite testar regras que dependem de "agora". */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
