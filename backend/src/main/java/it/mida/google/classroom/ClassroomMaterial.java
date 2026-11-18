package it.mida.google.classroom;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Materiale collegato a un elemento Classroom: file Drive scaricato in GridFS,
 * oppure link esterno conservato solo come riferimento.
 */
@Document(collection = "classroomMaterials")
@CompoundIndex(
        name = "ux_classroom_materials_item_external",
        def = "{'itemId': 1, 'externalKey': 1}",
        unique = true)
public class ClassroomMaterial {

    public static final String STATUS_DOWNLOADED = "DOWNLOADED";
    public static final String STATUS_LINK_ONLY = "LINK_ONLY";
    public static final String STATUS_ERROR = "ERROR";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_TOO_LARGE = "TOO_LARGE";

    @Id
    private String id;

    @Indexed(name = "ix_classroom_materials_item")
    private String itemId;

    private String type;

    private String externalKey;

    private String title;

    private String url;

    private String filename;

    private String mimeType;

    private long size;

    private String gridFsId;

    private String sha256;

    @Indexed(name = "ix_classroom_materials_status")
    private String status;

    private String error;

    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public void setExternalKey(String externalKey) {
        this.externalKey = externalKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getGridFsId() {
        return gridFsId;
    }

    public void setGridFsId(String gridFsId) {
        this.gridFsId = gridFsId;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
