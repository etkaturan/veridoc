package com.etka.veridoc.ocr;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads a fixed-width line by matching each cell independently.
 *
 * <p>Because every cell is classified against the same closed alphabet, there
 * is no segmentation to get wrong and no need for grouping, fallbacks or
 * density heuristics — the filler is simply one of the 37 candidates.
 */
public final class TemplateLineReader {

    private final TemplateMatchingEngine engine;

    public TemplateLineReader(TemplateMatchingEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    public OcrResult read(BufferedImage lineImage, int characterCount) {
        double cellWidth = (double) lineImage.getWidth() / characterCount;

        StringBuilder line = new StringBuilder(characterCount);
        List<Float> confidences = new ArrayList<>(characterCount);

        for (int index = 0; index < characterCount; index++) {
            int from = (int) Math.round(index * cellWidth);
            int to = Math.min((int) Math.round((index + 1) * cellWidth), lineImage.getWidth());

            if (to <= from) {
                line.append('?');
                confidences.add(0.0f);
                continue;
            }

            OcrResult result = engine.read(
                    lineImage.getSubimage(from, 0, to - from, lineImage.getHeight()),
                    OcrHints.forMrz());

            line.append(result.text().charAt(0));
            confidences.add(result.meanConfidence());
        }

        float mean = (float) confidences.stream()
                .mapToDouble(Float::doubleValue).average().orElse(0.0);

        return new OcrResult(line.toString(), mean, confidences);
    }
}