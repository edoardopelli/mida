package it.mida.google.classroom.api;

public record ClassroomMaterialDto(
        String id,
        String type,
        String title,
        String url,
        String filename,
        String mimeType,
        long size,
        String status,
        String error,
        String downloadUrl) {
}
