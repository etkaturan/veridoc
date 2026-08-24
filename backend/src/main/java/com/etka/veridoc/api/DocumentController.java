package com.etka.veridoc.api;

import com.etka.veridoc.document.DocumentVerificationService;
import com.etka.veridoc.document.VerificationOutcome;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Set;

/**
 * HTTP entry point for document verification.
 *
 * <p>Endpoints are scoped by purpose rather than returning one record for
 * every caller: an age check returns a boolean, not a date of birth.
 */
@RestController
@RequestMapping("/api/documents")
@Validated
public class DocumentController {

    private static final Set<String> ACCEPTED_TYPES =
            Set.of("image/png", "image/jpeg", "image/jpg");

    private static final long MAX_BYTES = 10 * 1024 * 1024;

    private final DocumentVerificationService service;

    public DocumentController(DocumentVerificationService service) {
        this.service = service;
    }

    /**
     * Checks whether the document holder has reached a given age.
     *
     * @param file        an image cropped to the machine readable zone
     * @param requiredAge the threshold to check against
     */
    @PostMapping(value = "/age-check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AgeCheckResponse> checkAge(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "18") @Min(1) @Max(120) int requiredAge)
            throws IOException {

        validate(file);

        BufferedImage image = read(file);
        LocalDate today = LocalDate.now();

        VerificationOutcome outcome = service.verifyAndRecord(image, today);

                if (outcome.status() != VerificationOutcome.Status.PARSED) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                    AgeCheckResponse.failed(requiredAge, outcome.message().orElse("Unreadable")));
        }

        boolean meets = outcome.data()
                .map(data -> data.isAtLeastAge(requiredAge, today))
                .orElse(false);

        return ResponseEntity.ok(AgeCheckResponse.of(
                meets, requiredAge, outcome.isTrustworthy(), outcome.recordId().orElse(null)));
    }

    /**
     * Returns full structured detail from a document — name, dates, document
     * number, nationality — for inspection and testing.
     *
     * <p>Unlike {@link #checkAge}, this exposes the underlying identity data
     * in full rather than a single boolean. It exists so the extraction
     * pipeline itself can be verified end to end; the age-check endpoint
     * remains the one intended for privacy-preserving production use.
     */
    @PostMapping(value = "/detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDetailResponse> detail(
            @RequestParam("file") MultipartFile file) throws IOException {

        validate(file);

        BufferedImage image = read(file);
        LocalDate today = LocalDate.now();

        VerificationOutcome outcome = service.verifyAndRecord(image, today);

        DocumentDetailResponse response = DocumentDetailResponse.from(outcome, today);

        return outcome.status() == VerificationOutcome.Status.PARSED
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    private static void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File exceeds the 10 MB limit");
        }
        // Content type is client-supplied and therefore untrusted; ImageIO
        // decoding below is the real check that this is an image at all.
        String contentType = file.getContentType();
        if (contentType == null || !ACCEPTED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported type '%s'. Accepted: PNG, JPEG".formatted(contentType));
        }
    }

    private static BufferedImage read(MultipartFile file) throws IOException {
        try (InputStream stream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IllegalArgumentException("File is not a decodable image");
            }
            System.err.println("[controller] decoded upload: " + image.getWidth() + "x"
                    + image.getHeight() + " originalFilename=" + file.getOriginalFilename()
                    + " size=" + file.getSize() + " contentType=" + file.getContentType());
            return image;
        }
    }
}