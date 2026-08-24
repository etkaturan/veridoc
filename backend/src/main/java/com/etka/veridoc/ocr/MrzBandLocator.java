package com.etka.veridoc.ocr;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_imgproc.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_core.*;

/**
 * Finds the machine readable zone within a photo of a whole document.
 *
 * <p>An MRZ is visually distinctive: dense, evenly-spaced vertical edges
 * packed into a short horizontal band, usually in the lower third of the
 * document. A morphological gradient highlights those edges; horizontal
 * dilation merges each line's characters into one solid region; the widest
 * such region, scored by how band-shaped and how low in the image it is, is
 * the MRZ.
 *
 * <p>This is a heuristic, not a guarantee — a photo with no visible MRZ, or
 * one so heavily skewed the band isn't roughly horizontal, will not be found.
 * Both are surfaced as "not found" rather than a wrong crop.
 */
public final class MrzBandLocator {

    private MrzBandLocator() {
        throw new AssertionError("Utility class");
    }

    /**
     * @param image a photo of a document, any size, any orientation of content
     * @return the sub-image most likely to contain the MRZ, with a small
     *         margin, or empty if no plausible band was found
     */
    public static Optional<BufferedImage> locate(BufferedImage image) {
        System.err.println("[band] locate() called, image=" + image.getWidth() + "x" + image.getHeight());
        try {
            return locateInternal(image);
        } catch (Throwable t) {
            System.err.println("[band] EXCEPTION in locate(): " + t);
            t.printStackTrace();
            return Optional.empty();
        }
    }

    private static Optional<BufferedImage> locateInternal(BufferedImage image) {
        System.err.println("[band] locateInternal ENTERED");
        try (Mat source = toMatUnchecked(image);
             Mat gray = new Mat();
             Mat gradient = new Mat();
             Mat blackHat = new Mat();
             Mat gradX = new Mat();
             Mat blurred = new Mat();
             Mat threshold = new Mat();
             Mat closed = new Mat();
             Mat rectKernel = getStructuringElement(MORPH_RECT, new Size(25, 5));
             Mat squareKernel = getStructuringElement(MORPH_RECT, new Size(3, 3))) {

            System.err.println("[band] inside try block, about to cvtColor");
            boolean debug = System.getProperty("veridoc.debug.band") != null;
            System.err.println("[band] debug flag = " + debug);

            cvtColor(source, gray, COLOR_BGR2GRAY);
            if (debug) System.err.println("[band] step 1: cvtColor done");

            morphologyEx(gray, blackHat, MORPH_BLACKHAT, rectKernel);
            if (debug) System.err.println("[band] step 2: blackhat done");

            Sobel(blackHat, gradX, CV_32F, 1, 0, 3, 1.0, 0.0, BORDER_DEFAULT);
            if (debug) System.err.println("[band] step 3: sobel done");
            convertScaleAbs(gradX, gradX);
            if (debug) System.err.println("[band] step 4: convertScaleAbs done");
            normalize(gradX, gradX, 0, 255, NORM_MINMAX, -1, new Mat());
            if (debug) System.err.println("[band] step 5: normalize done");

            GaussianBlur(gradX, blurred, new Size(9, 9), 0);
            if (debug) System.err.println("[band] step 6: blur done");
            morphologyEx(blurred, closed, MORPH_CLOSE, rectKernel);
            if (debug) System.err.println("[band] step 7: close done");
            threshold(closed, threshold, 0, 255, THRESH_BINARY | THRESH_OTSU);
            if (debug) System.err.println("[band] step 8: threshold done");
            erode(threshold, threshold, squareKernel, new Point(-1, -1), 2, BORDER_CONSTANT, morphologyDefaultBorderValue());
            if (debug) System.err.println("[band] step 9: erode done");
            dilate(threshold, threshold, rectKernel, new Point(-1, -1), 2, BORDER_CONSTANT, morphologyDefaultBorderValue());
            if (debug) System.err.println("[band] step 10: dilate done");

            MatVector contours = new MatVector();
            findContours(threshold, contours, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
            if (debug) System.err.println("[band] step 11: findContours done, count=" + contours.size());

            // Collect every plausibly line-shaped candidate, not just the best
            // one. A TD3 MRZ is two such lines stacked directly on top of each
            // other, and the morphological closing above merges characters
            // *within* a line but leaves the gap *between* the two lines
            // intact — so each line surfaces as its own separate contour.
            java.util.List<Rect> candidates = new java.util.ArrayList<>();
            for (long i = 0; i < contours.size(); i++) {
                Rect box = boundingRect(contours.get(i));
                if (scoreCandidate(box, image.getWidth(), image.getHeight()) > 0) {
                    candidates.add(box);
                }
            }

            if (debug) {
                System.err.println("[band] plausible line candidates: " + candidates.size());
                for (Rect c : candidates) {
                    System.err.printf("[band]   %dx%d @ (%d,%d)%n",
                            c.width(), c.height(), c.x(), c.y());
                }
            }

            Rect merged = mergeAdjacentLines(candidates, image.getWidth(), image.getHeight());

            if (debug) {
                System.err.println("[band] merged region: "
                        + (merged == null ? "none" : "%dx%d @ (%d,%d)".formatted(
                                merged.width(), merged.height(), merged.x(), merged.y())));
            }

            if (merged == null) {
                return Optional.empty();
            }

            return Optional.of(cropWithMargin(image, merged));
        }
    }

    /**
     * Scores a candidate region by the properties an MRZ band actually has:
     * very wide relative to its height, positioned in the lower half of the
     * document, and spanning a meaningful fraction of the document's width.
     * Returns 0 (never selected) for anything that clearly is not a band.
     */
    private static double scoreCandidate(Rect box, int imageWidth, int imageHeight) {
        double aspectRatio = (double) box.width() / Math.max(1, box.height());
        double widthFraction = (double) box.width() / imageWidth;
        double verticalPosition = (double) box.y() / imageHeight;

        boolean plausibleShape = aspectRatio > 5.0 && widthFraction > 0.5;
        boolean plausiblePosition = verticalPosition > 0.4;

        if (!plausibleShape || !plausiblePosition) {
            return 0;
        }

        // Among plausible candidates, prefer wider and lower — real MRZs sit
        // at the very bottom of the document and span nearly its full width.
        return widthFraction + verticalPosition;
    }

    /**
     * Combines candidate line regions that sit directly above one another
     * with similar width into a single box spanning all of them.
     *
     * <p>A TD3 MRZ is two lines of identical width stacked with a small gap;
     * a TD1 MRZ is three. Rather than assume a fixed count, this groups any
     * run of vertically stacked, similarly-wide candidates and returns the
     * union of the largest such group — the individual line candidates are
     * discarded once merged, since the merged region is what actually gets
     * cropped and handed to line-splitting downstream.
     */
    private static Rect mergeAdjacentLines(
            java.util.List<Rect> candidates, int imageWidth, int imageHeight) {
        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(java.util.Comparator.comparingInt(Rect::y));

        java.util.List<java.util.List<Rect>> groups = new java.util.ArrayList<>();
        java.util.List<Rect> currentGroup = new java.util.ArrayList<>();
        currentGroup.add(candidates.get(0));

        for (int i = 1; i < candidates.size(); i++) {
            Rect previous = currentGroup.get(currentGroup.size() - 1);
            Rect current = candidates.get(i);

            double widthRatio = (double) Math.min(previous.width(), current.width())
                    / Math.max(previous.width(), current.width());
            int verticalGap = current.y() - (previous.y() + previous.height());

            // Lines of the same MRZ are near-identical in width and separated
            // by a gap no larger than roughly one line's height.
            boolean sameBlock = widthRatio > 0.7 && verticalGap >= 0
                    && verticalGap < previous.height() * 1.5;

            if (sameBlock) {
                currentGroup.add(current);
            } else {
                groups.add(currentGroup);
                currentGroup = new java.util.ArrayList<>();
                currentGroup.add(current);
            }
        }
        groups.add(currentGroup);

        // Score every group — including singletons — with the same shape
        // criteria used to find the individual candidates in the first place.
        // A single wide, short, low region (the real MRZ, sitting alone
        // because a real photo's second line separated cleanly) must be able
        // to beat a single tall, narrower region elsewhere on the page; raw
        // vertical span cannot make that distinction; scoreCandidate can,
        // because it directly measures how MRZ-shaped a region is.
        java.util.List<Rect> bestGroup = null;
        double bestGroupScore = -1;

        for (java.util.List<Rect> group : groups) {
            Rect merged = union(group);
            double score = scoreCandidate(merged, imageWidth, imageHeight);
            if (score > bestGroupScore) {
                bestGroupScore = score;
                bestGroup = group;
            }
        }

        return bestGroupScore > 0 ? union(bestGroup) : null;
    }

    private static Rect union(java.util.List<Rect> boxes) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (Rect box : boxes) {
            left = Math.min(left, box.x());
            top = Math.min(top, box.y());
            right = Math.max(right, box.x() + box.width());
            bottom = Math.max(bottom, box.y() + box.height());
        }
        return new Rect(left, top, right - left, bottom - top);
    }

    private static BufferedImage cropWithMargin(BufferedImage image, Rect box) {
        // A generous vertical margin matters more than a tight crop here: if
        // only one MRZ line surfaced as its own contour — the second line's
        // edges were too faint, or dilation didn't bridge to it — a wide
        // margin still pulls the neighbouring line's pixels into the crop, and
        // LineSplitter finds it correctly on its own from there. A tight crop
        // that misses the second line entirely cannot be recovered downstream.
        int verticalMargin = box.height() * 3;
        int horizontalMargin = box.height();

        int x = Math.max(0, box.x() - horizontalMargin);
        int y = Math.max(0, box.y() - verticalMargin);
        int width = Math.min(image.getWidth() - x, box.width() + 2 * horizontalMargin);
        int height = Math.min(image.getHeight() - y, box.height() + 2 * verticalMargin);

        return image.getSubimage(x, y, width, height);
    }

    /**
     * Wraps {@link #toMat} to convert its checked IOException into an
     * unchecked one. Encoding an in-memory BufferedImage as PNG cannot fail
     * for reasons a caller could meaningfully recover from, so forcing every
     * caller to handle a checked exception here would only add noise.
     */
    private static Mat toMatUnchecked(BufferedImage image) {
        try {
            return toMat(image);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Failed to encode image for OpenCV", e);
        }
    }

    private static Mat toMat(BufferedImage image) throws java.io.IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", buffer);
        Mat encoded = new Mat(buffer.toByteArray());
        return imdecode(encoded, IMREAD_COLOR);
    }
}