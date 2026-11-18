package it.mida.google.email;

import it.mida.imports.ImportService;
import it.mida.parser.DidupContentDetector;
import it.mida.parser.DidupFileName;
import it.mida.parser.DidupParseException;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Importa nei compiti o nel diario gli allegati testuali riconosciuti come
 * esportazioni DidUP, indipendentemente dalla sorgente (Gmail o altre).
 */
@Service
public class DidupAttachmentImporter {

    private final ImportService importService;
    private final DidupContentDetector detector;

    public DidupAttachmentImporter(
            ImportService importService, DidupContentDetector detector) {
        this.importService = importService;
        this.detector = detector;
    }

    public void apply(EmailAttachment attachment, byte[] bytes, String filename) {
        if (!isTextLike(attachment.getMimeType(), filename)) {
            attachment.setImportStatus(EmailAttachment.IMPORT_STORED);
            attachment.setImportMessage("Allegato disponibile per il download");
            return;
        }
        try {
            detector.detect(bytes);
        } catch (DidupParseException ex) {
            attachment.setImportStatus(EmailAttachment.IMPORT_NOT_DIDUP);
            attachment.setImportMessage("Testo non riconosciuto come esportazione DidUP");
            return;
        } catch (RuntimeException ex) {
            attachment.setImportStatus(EmailAttachment.IMPORT_FAILED);
            attachment.setImportMessage("Errore durante il riconoscimento: " + ex.getMessage());
            return;
        }

        DidupFileName name = DidupFileName.parse(filename).orElseGet(() -> new DidupFileName(
                ImportService.sha256(bytes).substring(0, 12).toUpperCase(Locale.ROOT),
                null,
                filename));
        ImportService.ImportOutcome outcome = importService.importBytes(bytes, name);
        attachment.setImportMessage(outcome.message());
        switch (outcome.status()) {
            case IMPORTED -> attachment.setImportStatus(EmailAttachment.IMPORT_IMPORTED);
            case ALREADY_IMPORTED -> attachment.setImportStatus(EmailAttachment.IMPORT_ALREADY);
            case CONFLICT, ERROR, SKIPPED -> attachment.setImportStatus(EmailAttachment.IMPORT_FAILED);
        }
    }

    private boolean isTextLike(String mimeType, String filename) {
        return (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("text/"))
                || (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".txt"));
    }
}
