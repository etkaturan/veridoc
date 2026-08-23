package com.etka.veridoc.api;

import com.etka.veridoc.document.SubjectBindingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Binding a verification to a subject, and answering questions about a subject.
 *
 * <p>This is the point of persisting verifications: a subject presents a
 * document once, and every subsequent age check is answered from a stored
 * boolean without the document or any identity data being involved again.
 */
@RestController
@RequestMapping("/api/subjects")
@Validated
public class SubjectController {

    private final SubjectBindingService bindingService;

    public SubjectController(SubjectBindingService bindingService) {
        this.bindingService = bindingService;
    }

    /** Associates a verification record with a subject. */
    @PostMapping("/bind/{recordId}")
    public ResponseEntity<Map<String, String>> bind(
            @PathVariable UUID recordId,
            @Valid @RequestBody BindRequest request) {

        return bindingService.bind(recordId, request.subjectReference())
                .map(record -> ResponseEntity.ok(Map.of(
                        "recordId", record.getId().toString(),
                        "subjectReference", record.getSubjectReference(),
                        "status", "BOUND")))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No verification record with that id")));
    }

    /**
     * Answers whether a subject meets an age requirement, from stored data.
     *
     * <p>Only 18 and 21 are answerable: those are the thresholds the schema
     * stores. Supporting arbitrary ages would mean storing the date of birth,
     * which is precisely what this design avoids.
     */
    @PostMapping("/{subjectReference}/age-check")
    public ResponseEntity<SubjectAgeResponse> checkAge(
            @PathVariable String subjectReference,
            @RequestParam(defaultValue = "18") @Min(18) @Max(21) int minimumAge) {

        var answer = bindingService.checkAge(subjectReference, minimumAge, LocalDate.now());
        var response = SubjectAgeResponse.from(answer);

        HttpStatus status = switch (answer.status()) {
            case ANSWERED -> HttpStatus.OK;
            case NO_VERIFICATION -> HttpStatus.NOT_FOUND;
            case DOCUMENT_EXPIRED -> HttpStatus.GONE;
        };

        return ResponseEntity.status(status).body(response);
    }
}