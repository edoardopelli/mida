package it.mida.google.email.api;

public record EmailAttachmentDto(
        String id,
        String filename,
        String mimeType,
        long size,
        boolean inline,
        String importStatus,
        String importMessage,
        String downloadUrl) {
}
