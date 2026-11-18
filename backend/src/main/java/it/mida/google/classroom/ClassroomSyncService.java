package it.mida.google.classroom;

import tools.jackson.databind.JsonNode;
import it.mida.config.MidaProperties;
import it.mida.google.GoogleApiClient;
import it.mida.google.GoogleApiException;
import it.mida.attachments.AttachmentStorageService;
import it.mida.google.GoogleReauthRequiredException;
import it.mida.google.GoogleSettings;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Sincronizza corsi, compiti, materiali e annunci di Google Classroom.
 * I file Drive vengono copiati in GridFS; i link esterni restano riferimenti.
 */
@Service
public class ClassroomSyncService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomSyncService.class);
    private static final int MAX_PAGES = 20;

    private final GoogleApiClient api;
    private final ClassroomItemRepository itemRepository;
    private final ClassroomMaterialRepository materialRepository;
    private final AttachmentStorageService fileService;
    private final MidaProperties properties;

    public ClassroomSyncService(
            GoogleApiClient api,
            ClassroomItemRepository itemRepository,
            ClassroomMaterialRepository materialRepository,
            AttachmentStorageService fileService,
            MidaProperties properties) {
        this.api = api;
        this.itemRepository = itemRepository;
        this.materialRepository = materialRepository;
        this.fileService = fileService;
        this.properties = properties;
    }

    public SyncResult sync(String accessToken, GoogleSettings settings) {
        if (!settings.isClassroomEnabled()) {
            return SyncResult.empty();
        }
        List<JsonNode> courses = fetchPages(
                GoogleApiClient.CLASSROOM_BASE + "/courses",
                Map.of("courseStates", "ACTIVE", "pageSize", "100"),
                "courses",
                accessToken);
        int itemsSeen = 0;
        int newItems = 0;
        int materials = 0;
        int newMaterials = 0;
        int errors = 0;
        String lastError = null;

        for (JsonNode course : courses) {
            String courseId = course.path("id").asText();
            if (!StringUtils.hasText(courseId)) {
                continue;
            }
            String courseName = course.path("name").asText("Corso");
            String courseSection = course.path("section").asText(null);
            try {
                Result coursework = syncItems(
                        courseId, courseName, courseSection, "COURSEWORK",
                        GoogleApiClient.CLASSROOM_BASE + "/courses/" + courseId + "/courseWork",
                        Map.of("courseWorkStates", "PUBLISHED", "pageSize", "100"),
                        "courseWork", accessToken);
                itemsSeen += coursework.seen();
                newItems += coursework.created();
                materials += coursework.materials();
                newMaterials += coursework.newMaterials();
                errors += coursework.errors();
                lastError = firstError(lastError, coursework.lastError());

                Result materialsResult = syncItems(
                        courseId, courseName, courseSection, "MATERIAL",
                        GoogleApiClient.CLASSROOM_BASE + "/courses/" + courseId
                                + "/courseWorkMaterials",
                        Map.of("courseWorkMaterialStates", "PUBLISHED", "pageSize", "100"),
                        "courseWorkMaterial", accessToken);
                itemsSeen += materialsResult.seen();
                newItems += materialsResult.created();
                materials += materialsResult.materials();
                newMaterials += materialsResult.newMaterials();
                errors += materialsResult.errors();
                lastError = firstError(lastError, materialsResult.lastError());

                if (properties.getGoogle().isClassroomAnnouncements()) {
                    Result announcements = syncItems(
                            courseId, courseName, courseSection, "ANNOUNCEMENT",
                            GoogleApiClient.CLASSROOM_BASE + "/courses/" + courseId
                                    + "/announcements",
                            Map.of("announcementStates", "PUBLISHED", "pageSize", "100"),
                            "announcements", accessToken);
                    itemsSeen += announcements.seen();
                    newItems += announcements.created();
                    materials += announcements.materials();
                    newMaterials += announcements.newMaterials();
                    errors += announcements.errors();
                    lastError = firstError(lastError, announcements.lastError());
                }
            } catch (GoogleReauthRequiredException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                errors++;
                lastError = "Corso " + courseName + ": " + ex.getMessage();
                log.warn("Corso Classroom {} non sincronizzato: {}", courseName, ex.getMessage());
            }
        }
        return new SyncResult(courses.size(), itemsSeen, newItems, materials, newMaterials, errors, lastError);
    }

    private Result syncItems(
            String courseId,
            String courseName,
            String courseSection,
            String itemType,
            String baseUrl,
            Map<String, String> query,
            String arrayField,
            String accessToken) {
        List<JsonNode> items = fetchPages(baseUrl, query, arrayField, accessToken);
        int created = 0;
        int materials = 0;
        int newMaterials = 0;
        int errors = 0;
        String lastError = null;

        for (JsonNode itemJson : items) {
            String itemId = itemJson.path("id").asText();
            if (!StringUtils.hasText(itemId)) {
                continue;
            }
            try {
                String classroomKey = courseId + ":" + itemType + ":" + itemId;
                Optional<ClassroomItem> existing = itemRepository.findByClassroomKey(classroomKey);
                ClassroomItem item = existing.orElseGet(ClassroomItem::new);
                boolean isNew = item.getId() == null;
                if (isNew) {
                    item.setId(UUID.randomUUID().toString());
                    item.setClassroomKey(classroomKey);
                    item.setFirstSeenAt(Instant.now());
                    created++;
                }
                item.setCourseId(courseId);
                item.setCourseName(courseName);
                item.setCourseSection(courseSection);
                item.setItemType(itemType);
                String title = value(itemJson, "title", "");
                String description = value(itemJson, "description", "");
                if ("ANNOUNCEMENT".equals(itemType)) {
                    if (!StringUtils.hasText(description)) {
                        description = value(itemJson, "text", "");
                    }
                    if (!StringUtils.hasText(title)) {
                        title = firstLine(description, 90);
                    }
                }
                item.setTitle(StringUtils.hasText(title) ? title : "(senza titolo)");
                item.setDescription(description);
                item.setState(value(itemJson, "state", ""));
                item.setAlternateLink(value(itemJson, "alternateLink", null));
                item.setDueDate(dueDate(itemJson));
                item.setDueTime(dueTime(itemJson));
                item.setCreationTime(instant(itemJson.path("creationTime").asText(null)));
                item.setUpdateTime(instant(itemJson.path("updateTime").asText(null)));
                item.setLastSeenAt(Instant.now());
                item.setUpdatedAt(Instant.now());
                item = itemRepository.save(item);

                // I vecchi materiali "OTHER" erano segnaposto creati quando la
                // struttura annidata di Drive non era riconosciuta.
                materialRepository.deleteByItemIdAndType(item.getId(), "OTHER");

                JsonNode materialNodes = itemJson.path("materials");
                int index = 0;
                if (materialNodes.isArray()) {
                    for (JsonNode materialNode : materialNodes) {
                        try {
                            MaterialResult result = upsertMaterial(
                                    item, materialNode, index, accessToken);
                            materials++;
                            if (result.downloaded()) {
                                newMaterials++;
                            }
                        } catch (GoogleReauthRequiredException ex) {
                            throw ex;
                        } catch (RuntimeException ex) {
                            errors++;
                            lastError = "Materiale di " + item.getTitle() + ": " + ex.getMessage();
                            log.warn("Materiale Classroom non scaricato per {}: {}",
                                    item.getTitle(), ex.getMessage());
                        }
                        index++;
                    }
                }
                item.setMaterialCount((int) materialRepository.countByItemId(item.getId()));
                itemRepository.save(item);
            } catch (GoogleReauthRequiredException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                errors++;
                lastError = ex.getMessage();
                log.warn("Elemento Classroom {} non sincronizzato: {}", itemId, ex.getMessage());
            }
        }
        return new Result(items.size(), created, materials, newMaterials, errors, lastError);
    }

    private MaterialResult upsertMaterial(
            ClassroomItem item, JsonNode node, int index, String accessToken) {
        MaterialDescriptor descriptor = describe(node, index);
        Optional<ClassroomMaterial> existing = materialRepository
                .findByItemIdAndExternalKey(item.getId(), descriptor.externalKey());
        ClassroomMaterial material = existing.orElseGet(ClassroomMaterial::new);
        if (material.getId() == null) {
            material.setId(UUID.randomUUID().toString());
            material.setItemId(item.getId());
            material.setExternalKey(descriptor.externalKey());
            material.setCreatedAt(Instant.now());
        }
        material.setType(descriptor.type());
        material.setTitle(descriptor.title());
        material.setUrl(descriptor.url());

        if (!"DRIVE_FILE".equals(descriptor.type())) {
            material.setStatus(ClassroomMaterial.STATUS_LINK_ONLY);
            material.setError(null);
            materialRepository.save(material);
            return new MaterialResult(false);
        }
        if (StringUtils.hasText(material.getGridFsId())) {
            return new MaterialResult(false);
        }

        try {
            DownloadedFile file = downloadDriveFile(descriptor, accessToken);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", "CLASSROOM");
            metadata.put("classroomItemId", item.getId());
            metadata.put("driveFileId", descriptor.driveFileId());
            AttachmentStorageService.StoredFile stored = fileService.store(
                    file.bytes(), file.filename(), file.mimeType(), metadata);
            material.setGridFsId(stored.id());
            material.setSha256(stored.sha256());
            material.setFilename(file.filename());
            material.setMimeType(file.mimeType());
            material.setSize(file.bytes().length);
            material.setStatus(ClassroomMaterial.STATUS_DOWNLOADED);
            material.setError(null);
            materialRepository.save(material);
            return new MaterialResult(true);
        } catch (GoogleReauthRequiredException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            material.setStatus(ClassroomMaterial.STATUS_ERROR);
            material.setError(ex.getMessage());
            materialRepository.save(material);
            throw ex;
        }
    }

    private DownloadedFile downloadDriveFile(
            MaterialDescriptor descriptor, String accessToken) {
        String fileId = descriptor.driveFileId();
        JsonNode metadata = api.getJson(
                GoogleApiClient.withQuery(
                        GoogleApiClient.DRIVE_BASE + "/files/" + fileId,
                        Map.of(
                                "fields", "id,name,mimeType,size,webViewLink",
                                "supportsAllDrives", "true")),
                accessToken);
        String sourceMime = metadata.path("mimeType").asText("application/octet-stream");
        long declaredSize = longValue(metadata.path("size"));
        if (declaredSize > properties.getGoogle().getMaxAttachmentSizeBytes()) {
            throw new GoogleApiException(
                    "File più grande del limite di "
                            + properties.getGoogle().getMaxAttachmentSizeBytes() + " byte");
        }
        String name = metadata.path("name").asText(descriptor.title());
        if (!StringUtils.hasText(name)) {
            name = descriptor.title();
        }

        String targetMime = exportMimeType(sourceMime);
        String url;
        if (targetMime != null) {
            url = UriComponentsBuilder.fromUriString(
                            GoogleApiClient.DRIVE_BASE + "/files/" + fileId + "/export")
                    .queryParam("mimeType", targetMime)
                    .build()
                    .encode()
                    .toUriString();
            name = ensureExtension(name, extensionFor(targetMime));
        } else {
            if (sourceMime.startsWith("application/vnd.google-apps.")) {
                throw new GoogleApiException("Formato Google non esportabile: " + sourceMime);
            }
            targetMime = sourceMime;
            url = GoogleApiClient.withQuery(
                    GoogleApiClient.DRIVE_BASE + "/files/" + fileId,
                    Map.of("alt", "media", "supportsAllDrives", "true"));
        }

        GoogleApiClient.BinaryResponse response = api.getBytes(url, accessToken);
        if (response.body().length > properties.getGoogle().getMaxAttachmentSizeBytes()) {
            throw new GoogleApiException(
                    "File più grande del limite di "
                            + properties.getGoogle().getMaxAttachmentSizeBytes() + " byte");
        }
        String contentType = StringUtils.hasText(response.contentType())
                ? response.contentType()
                : targetMime;
        return new DownloadedFile(response.body(), name, contentType);
    }

    private MaterialDescriptor describe(JsonNode node, int index) {
        JsonNode driveWrapper = node.path("driveFile");
        JsonNode driveFile = driveWrapper.hasNonNull("id")
                ? driveWrapper
                : driveWrapper.path("driveFile");
        if (driveFile.hasNonNull("id")) {
            return new MaterialDescriptor(
                    "DRIVE_FILE",
                    driveFile.path("id").asText(),
                    value(driveFile, "title", "File Drive"),
                    value(driveFile, "alternateLink", null),
                    driveFile.path("id").asText());
        }
        JsonNode youtube = node.has("youTubeVideo")
                ? node.path("youTubeVideo")
                : node.path("youtubeVideo");
        if (!youtube.isMissingNode() && youtube.hasNonNull("id")) {
            String videoId = youtube.path("id").asText();
            return new MaterialDescriptor(
                    "YOUTUBE_VIDEO",
                    videoId,
                    value(youtube, "title", "Video YouTube"),
                    value(youtube, "alternateLink", "https://youtu.be/" + videoId),
                    null);
        }
        JsonNode link = node.path("link");
        if (!link.isMissingNode() && link.hasNonNull("url")) {
            String url = link.path("url").asText();
            return new MaterialDescriptor(
                    "LINK", url, value(link, "title", url), url, null);
        }
        JsonNode form = node.path("form");
        if (!form.isMissingNode() && form.hasNonNull("formUrl")) {
            String url = form.path("formUrl").asText();
            return new MaterialDescriptor(
                    "FORM", url, value(form, "title", "Modulo Google"), url, null);
        }
        return new MaterialDescriptor(
                "OTHER", "other-" + index, "Materiale", null, null);
    }

    private List<JsonNode> fetchPages(
            String baseUrl, Map<String, String> query, String arrayField, String accessToken) {
        List<JsonNode> result = new ArrayList<>();
        String pageToken = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, String> parameters = new LinkedHashMap<>(query);
            if (StringUtils.hasText(pageToken)) {
                parameters.put("pageToken", pageToken);
            }
            JsonNode response = api.getJson(
                    GoogleApiClient.withQuery(baseUrl, parameters), accessToken);
            JsonNode items = response.path(arrayField);
            if (items.isArray()) {
                items.forEach(result::add);
            }
            pageToken = response.path("nextPageToken").asText(null);
            if (!StringUtils.hasText(pageToken)) {
                break;
            }
        }
        return result;
    }

    private String exportMimeType(String sourceMime) {
        return switch (sourceMime) {
            case "application/vnd.google-apps.document" -> "application/pdf";
            case "application/vnd.google-apps.spreadsheet" -> "application/pdf";
            case "application/vnd.google-apps.presentation" -> "application/pdf";
            case "application/vnd.google-apps.drawing" -> "image/png";
            default -> null;
        };
    }

    private String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> "";
        };
    }

    private String ensureExtension(String name, String extension) {
        if (!StringUtils.hasText(name)) {
            return "allegato" + extension;
        }
        return name.toLowerCase(Locale.ROOT).endsWith(extension) ? name : name + extension;
    }

    private String value(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String firstLine(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String first = value.lines().findFirst().orElse("").strip();
        if (!StringUtils.hasText(first)) {
            first = value.strip();
        }
        return first.length() <= maxLength
                ? first
                : first.substring(0, maxLength - 1).stripTrailing() + "…";
    }

    private long longValue(JsonNode node) {
        if (node.isNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private Instant instant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private LocalDate dueDate(JsonNode item) {
        JsonNode dueDate = item.path("dueDate");
        if (dueDate.isMissingNode()) {
            return null;
        }
        try {
            return LocalDate.of(
                    dueDate.path("year").asInt(),
                    dueDate.path("month").asInt(),
                    dueDate.path("day").asInt());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String dueTime(JsonNode item) {
        JsonNode dueTime = item.path("dueTime");
        if (dueTime.isMissingNode()) {
            return null;
        }
        return String.format(
                "%02d:%02d", dueTime.path("hours").asInt(), dueTime.path("minutes").asInt());
    }

    private String firstError(String current, String candidate) {
        return current != null ? current : candidate;
    }

    public record SyncResult(
            int courses,
            int seen,
            int created,
            int materials,
            int newMaterials,
            int errors,
            String lastError) {

        public static SyncResult empty() {
            return new SyncResult(0, 0, 0, 0, 0, 0, null);
        }
    }

    private record Result(
            int seen, int created, int materials, int newMaterials, int errors, String lastError) {
    }

    private record MaterialResult(boolean downloaded) {
    }

    private record MaterialDescriptor(
            String type, String externalKey, String title, String url, String driveFileId) {
    }

    private record DownloadedFile(byte[] bytes, String filename, String mimeType) {
    }
}
