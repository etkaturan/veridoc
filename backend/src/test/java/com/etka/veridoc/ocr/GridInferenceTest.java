package com.etka.veridoc.ocr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("Grid inference")
class GridInferenceTest {

    private static final File IMAGE = new File("../samples/specimen-td3.png");
    private static final File SIDECAR = new File("../samples/specimen-td3.grid");

    @Test
    @DisplayName("matches the geometry the generator actually used")
    void matchesGroundTruth() throws Exception {
        assumeTrue(IMAGE.isFile() && SIDECAR.isFile(),
                "Generated specimen not present; run MrzImageGenerator first");

        Properties truth = new Properties();
        try (var stream = new java.io.FileInputStream(SIDECAR)) {
            truth.load(stream);
        }
        int expectedLeft = Integer.parseInt(truth.getProperty("left"));
        int expectedPitch = Integer.parseInt(truth.getProperty("pitch"));

        BufferedImage image = ImageIO.read(IMAGE);
        List<BufferedImage> lines = LineSplitter.split(image);

        Optional<GridInference> inferred = GridInference.infer(lines, 44);

        assertThat(inferred).isPresent();

        // Sub-pixel accuracy matters: an error of one pixel per cell accumulates
        // to a full character over 44 cells.
        assertThat(inferred.get().pitch())
                .isCloseTo(expectedPitch, org.assertj.core.data.Offset.offset(0.5));
        assertThat(inferred.get().left())
                .isCloseTo(expectedLeft, org.assertj.core.data.Offset.offset(3));
    }
}