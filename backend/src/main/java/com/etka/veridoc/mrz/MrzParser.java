package com.etka.veridoc.mrz;

import java.time.LocalDate;
import java.util.List;

/**
 * Parses the lines of one MRZ layout into structured data.
 *
 * <p>Each ICAO 9303 layout places its fields at different offsets, so each
 * gets its own implementation. Support for a new layout is added by writing
 * a new implementation of this interface — no existing code changes.
 *
 * <p>Implementations may assume their input has already been normalised and
 * matches the dimensions of {@link #supportedFormat()}; the registry
 * guarantees both before dispatching.
 */
public interface MrzParser {

    /** The layout this parser handles. */
    MrzFormat supportedFormat();

    /**
     * Extracts the field contents.
     *
     * @param lines     normalised lines matching {@link #supportedFormat()}
     * @param today     reference date for interpreting two-digit years
     * @return the parsed contents
     * @throws MrzParseException if the lines are structurally unusable
     */
    MrzData parse(List<String> lines, LocalDate today);

    /**
     * Checks every check digit in the MRZ.
     *
     * @param lines normalised lines matching {@link #supportedFormat()}
     * @return per-field verification results
     */
    MrzVerification verify(List<String> lines);
}