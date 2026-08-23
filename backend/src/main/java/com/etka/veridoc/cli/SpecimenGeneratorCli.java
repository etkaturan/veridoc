package com.etka.veridoc.cli;

import com.etka.veridoc.mrz.Td3Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * Generates a set of test specimens covering the scenarios the verification
 * rules distinguish between.
 */
public final class SpecimenGeneratorCli {

    public static void main(String[] args) throws Exception {
        String font = args.length > 0 ? args[0] : "Consolas";
        String outputDir = args.length > 1 ? args[1] : "../samples";

        LocalDate today = LocalDate.now();

        generate(font, outputDir, "specimen-valid",
                new Td3Builder()
                        .name("ERIKSSON", "ANNA", "MARIA")
                        .dateOfBirth(LocalDate.of(1974, 8, 12))
                        .expiryDate(today.plusYears(5)));

        generate(font, outputDir, "specimen-expired",
                new Td3Builder()
                        .name("ERIKSSON", "ANNA", "MARIA")
                        .dateOfBirth(LocalDate.of(1974, 8, 12))
                        .expiryDate(today.minusYears(2)));

        generate(font, outputDir, "specimen-minor",
                new Td3Builder()
                        .name("SCHMIDT", "LUKAS")
                        .documentNumber("P12345678")
                        .dateOfBirth(today.minusYears(15))
                        .sex('M')
                        .expiryDate(today.plusYears(5)));

        generate(font, outputDir, "specimen-just-eighteen",
                new Td3Builder()
                        .name("MUELLER", "HANS", "PETER")
                        .documentNumber("P98765432")
                        .dateOfBirth(today.minusYears(18))
                        .sex('M')
                        .expiryDate(today.plusYears(5)));
    }

    private static void generate(String font, String dir, String name, Td3Builder builder)
            throws Exception {
        List<String> lines = builder.build();

        System.out.printf("%n%s%n  |%s|%n  |%s|%n", name, lines.get(0), lines.get(1));

        MrzImageGenerator.main(new String[]{
                font, dir + "/" + name + ".png", lines.get(0), lines.get(1)
        });
    }
}