package it.mida.google.email.api;

import it.mida.cluster.ConditionalOnMidaRole;
import it.mida.cluster.MidaRole;
import it.mida.common.PageResponse;
import it.mida.google.email.EmailQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/mail/emails")
@Tag(name = "Email", description = "Email sincronizzate via Gmail API")
@ConditionalOnMidaRole({MidaRole.STANDALONE, MidaRole.MASTER})
public class EmailController {

    private final EmailQueryService queryService;

    public EmailController(EmailQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Elenca le email sincronizzate")
    public PageResponse<EmailMessageSummaryDto> list(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queryService.search(new EmailQuery(from, q, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Restituisce una email con corpo e allegati")
    public EmailMessageView detail(@PathVariable String id) {
        return queryService.findById(id);
    }
}
