package it.mida.attachments;

import it.mida.common.MidaNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

/**
 * Archivia gli allegati in GridFS e li rende scaricabili tramite un
 * identificativo opaco. Il nome del file non viene mai usato come percorso.
 */
@Service
public class AttachmentStorageService {

    private final GridFsTemplate gridFsTemplate;

    public AttachmentStorageService(GridFsTemplate gridFsTemplate) {
        this.gridFsTemplate = gridFsTemplate;
    }

    public StoredFile store(
            byte[] bytes, String filename, String contentType, Map<String, Object> metadata) {
        Map<String, Object> values = new HashMap<>(metadata);
        values.put("sha256", sha256(bytes));
        ObjectId id = gridFsTemplate.store(
                new ByteArrayInputStream(bytes), safeFilename(filename), contentType, values);
        return new StoredFile(id.toHexString(), bytes.length, sha256(bytes));
    }

    public FileResource resource(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            throw new MidaNotFoundException("Allegato non trovato");
        }
        var file = gridFsTemplate.findOne(
                Query.query(Criteria.where("_id").is(new ObjectId(id))));
        if (file == null) {
            throw new MidaNotFoundException("Allegato non trovato");
        }
        GridFsResource resource = gridFsTemplate.getResource(file);
        String contentType = resource.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        return new FileResource(
                resource.getFilename(), contentType, file.getLength(), resource);
    }

    public InputStream open(String id) {
        try {
            return resource(id).resource().getInputStream();
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile leggere l'allegato", ex);
        }
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "allegato";
        }
        return filename.replaceAll("[\\r\\n/\\\\]", "_").strip();
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 non disponibile", ex);
        }
    }

    public record StoredFile(String id, long size, String sha256) {
    }

    public record FileResource(
            String filename, String contentType, long length, GridFsResource resource) {
    }
}
