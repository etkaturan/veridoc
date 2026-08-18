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