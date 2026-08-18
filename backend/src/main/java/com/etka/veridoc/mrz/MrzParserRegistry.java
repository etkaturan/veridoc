package com.etka.veridoc.mrz;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes normalised MRZ lines to the parser that handles their layout.
 *
 * <p>Constructed from whatever parsers are supplied, so adding support for a
 * new layout requires only a new {@link MrzParser} implementation. When
 * wired into Spring, all implementations are discovered automatically.
 */
public final class MrzParserRegistry {

    private final Map<MrzFormat, MrzParser> parsersByFormat;

    /**
     * @param parsers the available parsers; at most one per format
     * @throws IllegalArgumentException if two parsers claim the same format
     */
    public MrzParserRegistry(List<MrzParser> parsers) {
        Objects.requireNonNull(parsers, "parsers must not be null");

        this.parsersByFormat = parsers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        MrzParser::supportedFormat,
                        Function.identity(),
                        (first, second) -> {
                            throw new IllegalArgumentException(
                                    "Two parsers registered for format %s: %s and %s"
                                            .formatted(first.supportedFormat(),
                                                    first.getClass().getSimpleName(),
                                                    second.getClass().getSimpleName()));
                        }));
    }

    /** The formats this registry can currently handle. */
    public java.util.Set<MrzFormat> supportedFormats() {
        return parsersByFormat.keySet();
    }

    /** The parser for a format, if one is registered. */
    public Optional<MrzParser> parserFor(MrzFormat format) {
        return Optional.ofNullable(parsersByFormat.get(format));
    }

    /**
     * Detects the layout of already-normalised lines and returns its parser.
     *
     * @throws MrzParseException if the lines match no known layout, or if the
     *                           layout is recognised but unsupported
     */
    public MrzParser parserFor(List<String> lines) {
        MrzFormat format = MrzFormat.detect(lines)
                .orElseThrow(() -> new MrzParseException(
                        "Input does not match any ICAO 9303 layout: %d line(s) of length %s"
                                .formatted(lines.size(), lineLengthsOf(lines))));

        return parserFor(format)
                .orElseThrow(() -> new MrzParseException(
                        "Layout %s (%s) is recognised but not yet supported"
                                .formatted(format, format.description())));
    }

    private static String lineLengthsOf(List<String> lines) {
        return lines.stream()
                .map(line -> String.valueOf(line.length()))
                .collect(Collectors.joining(", ", "[", "]"));
    }
}