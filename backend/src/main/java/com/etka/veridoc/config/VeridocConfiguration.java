package com.etka.veridoc.config;

import com.etka.veridoc.mrz.MrzParser;
import com.etka.veridoc.mrz.MrzParserRegistry;
import com.etka.veridoc.mrz.Td3Parser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the framework-free domain classes into the Spring context.
 *
 * <p>Keeping the annotations here rather than on the domain classes means
 * {@code mrz} and {@code ocr} stay usable — and unit-testable — without Spring
 * on the classpath.
 */
@Configuration
public class VeridocConfiguration {

    /**
     * Permits the Vite dev server to call the API during development.
     *
     * <p>Deliberately narrow: a specific origin, the methods actually used, and
     * no credentials. A wildcard origin on a service that processes identity
     * documents would let any site on the internet submit uploads through a
     * visitor's browser.
     */
    @Bean
    public org.springframework.web.servlet.config.annotation.WebMvcConfigurer corsConfigurer() {
        return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
            @Override
            public void addCorsMappings(
                    org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("POST")
                        .allowCredentials(false);
            }
        };
    }

    /** Registered as a bean so Spring discovers it for the registry below. */
    @Bean
    public Td3Parser td3Parser() {
        return new Td3Parser();
    }

    /**
     * Spring injects every {@link MrzParser} bean into this list, so adding
     * support for a new layout means adding a parser bean and nothing else.
     */
    @Bean
    public MrzParserRegistry mrzParserRegistry(List<MrzParser> parsers) {
        return new MrzParserRegistry(parsers);
    }
}