package com.etka.veridoc.ocr;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.tesseract.TessBaseAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;

/**
 * Verifies that the native OpenCV and Tesseract libraries load and are
 * callable. This test exists purely to fail fast and loudly if the native
 * toolchain is broken, rather than letting an UnsatisfiedLinkError surface
 * from somewhere confusing deep in the pipeline.
 */
@DisplayName("Native library loading")
class NativeLibraryLoadTest {

    @Test
    @DisplayName("OpenCV loads and can allocate a matrix")
    void openCvLoads() {
        try (Mat matrix = new Mat(10, 20, CV_8UC1)) {
            assertThat(matrix.rows()).isEqualTo(10);
            assertThat(matrix.cols()).isEqualTo(20);
            assertThat(matrix.empty()).isFalse();
        }
    }

    @Test
    @DisplayName("Tesseract loads and reports its version")
    void tesseractLoads() {
        try (TessBaseAPI api = new TessBaseAPI()) {
            String version = api.Version().getString();

            System.out.println("Tesseract version: " + version);
            assertThat(version).isNotBlank();
        }
    }
}