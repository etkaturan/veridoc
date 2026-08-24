package com.etka.veridoc.cli;

import com.etka.veridoc.ocr.MrzBandLocator;
import com.etka.veridoc.ocr.OcrHints;
import com.etka.veridoc.ocr.TesseractOcrEngine;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * The simplest possible test: locate the MRZ band, then hand the WHOLE band
 * (not split into individual lines) directly to Tesseract with page
 * segmentation mode set to "single uniform block of text". No custom
 * splitting, no template matching, no confidence search — just ask Tesseract
 * to read what's there.
 */
public final class SimpleTesseractCli {
    public static void main(String[] args) throws Exception {
        BufferedImage image = ImageIO.read(new File(args[0]));

        BufferedImage band = MrzBandLocator.locate(image).orElse(image);
        System.out.println("Band: " + band.getWidth() + "x" + band.getHeight());

        ImageIO.write(band, "png", new File("../samples/debug/simple-band.png"));

        try (TesseractOcrEngine engine = TesseractOcrEngine.english()) {
            var result = engine.read(band, OcrHints.forMrz());
            System.out.println("Confidence: " + result.meanConfidence());
            System.out.println("Text:");
            System.out.println(result.text());
        }
    }
}