package it.mida.attachments;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attachments")
@Tag(name = "Allegati", description = "Download degli allegati archiviati in MongoDB")
public class AttachmentController {

    private final AttachmentStorageService fileService;

    public AttachmentController(AttachmentStorageService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Scarica un allegato")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        AttachmentStorageService.FileResource file = fileService.resource(id);
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(file.contentType());
        } catch (RuntimeException ex) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(file.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(file.resource());
    }
}
