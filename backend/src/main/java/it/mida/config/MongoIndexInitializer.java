package it.mida.config;

import com.mongodb.client.model.IndexOptions;
import it.mida.cluster.ClusterProperties;
import it.mida.cluster.MidaRole;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Crea gli indici delle collezioni MIDA. Compiti e diario sono documenti
 * autonomi: gli indici legacy sugli array incorporati vengono rimossi perché
 * non servono più e confonderebbero la diagnostica.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MongoIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private static final List<String> LEGACY_HOMEWORK_INDEXES = List.of(
            "ux_homework_assignments_id",
            "ix_homework_assignments_due_date",
            "ix_homework_assignments_assigned_date",
            "ix_homework_assignments_subject",
            "ix_homework_assignments_teacher",
            "ix_homework_assignments_due_completed",
            "tx_homework_assignments_search");

    private static final List<String> LEGACY_DIARY_INDEXES = List.of(
            "ux_diary_entries_id",
            "ix_diary_entries_activity_date",
            "ix_diary_entries_subject",
            "ix_diary_entries_teacher",
            "ix_diary_entries_date_period",
            "tx_diary_entries_search");

    private final MongoTemplate mongoTemplate;
    private final MongoTemplate localMongoTemplate;
    private final ClusterProperties clusterProperties;

    public MongoIndexInitializer(
            MongoTemplate mongoTemplate,
            @Qualifier("localMongoTemplate") MongoTemplate localMongoTemplate,
            ClusterProperties clusterProperties) {
        this.mongoTemplate = mongoTemplate;
        this.localMongoTemplate = localMongoTemplate;
        this.clusterProperties = clusterProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            createImportIndexes();
            dropLegacyIndexes("homeworkImports", LEGACY_HOMEWORK_INDEXES);
            dropLegacyIndexes("diaryImports", LEGACY_DIARY_INDEXES);
            createHomeworkIndexes();
            createDiaryIndexes();
            createClusterFileIndexes();
            createSupportIndexes();
            createLocalIndexes();
            dropLegacyIndexes("emailMessages", List.of("ux_email_messages_gmail_id"));
            createGoogleIndexes();
            log.info("Indici MongoDB MIDA verificati");
        } catch (RuntimeException ex) {
            if (clusterProperties.getRole() != MidaRole.STANDALONE) {
                throw new IllegalStateException(
                        "Impossibile creare gli indici MongoDB per il ruolo "
                                + clusterProperties.getRole() + ". Avvio bloccato.",
                        ex);
            }
            log.error("Impossibile creare gli indici MongoDB. Verificare la connessione al database.", ex);
        }
    }

    private void createImportIndexes() {
        IndexOperations imports = mongoTemplate.indexOps("homeworkImports");
        imports.ensureIndex(new Index().on("sourceCode", Sort.Direction.ASC)
                .unique().named("ux_homework_imports_source_code"));
        imports.ensureIndex(new Index().on("sha256", Sort.Direction.ASC)
                .named("ix_homework_imports_sha256"));
        imports.ensureIndex(new Index().on("importedAt", Sort.Direction.DESC)
                .named("ix_homework_imports_imported_at"));

        IndexOperations diary = mongoTemplate.indexOps("diaryImports");
        diary.ensureIndex(new Index().on("sourceCode", Sort.Direction.ASC)
                .unique().named("ux_diary_imports_source_code"));
        diary.ensureIndex(new Index().on("sha256", Sort.Direction.ASC)
                .named("ix_diary_imports_sha256"));
        diary.ensureIndex(new Index().on("importedAt", Sort.Direction.DESC)
                .named("ix_diary_imports_imported_at"));
    }

    private void createHomeworkIndexes() {
        IndexOperations assignments = mongoTemplate.indexOps("homeworkAssignments");
        assignments.ensureIndex(new Index().on("contentHash", Sort.Direction.ASC)
                .unique().named("ux_homework_assignments_content_hash"));
        assignments.ensureIndex(new Index().on("slotKey", Sort.Direction.ASC)
                .named("ix_homework_assignments_slot_key"));
        assignments.ensureIndex(new Index().on("baseKey", Sort.Direction.ASC)
                .named("ix_homework_assignments_base_key"));
        assignments.ensureIndex(new Index().on("descriptionHash", Sort.Direction.ASC)
                .named("ix_homework_assignments_description_hash"));
        assignments.ensureIndex(new Index().on("firstImportId", Sort.Direction.ASC)
                .named("ix_homework_assignments_first_import"));
        assignments.ensureIndex(new Index().on("dueDate", Sort.Direction.ASC)
                .named("ix_homework_assignments_due_date"));
        assignments.ensureIndex(new Index().on("assignedDate", Sort.Direction.ASC)
                .named("ix_homework_assignments_assigned_date"));
        assignments.ensureIndex(new Index().on("subject", Sort.Direction.ASC)
                .named("ix_homework_assignments_subject"));
        assignments.ensureIndex(new Index().on("teacher", Sort.Direction.ASC)
                .named("ix_homework_assignments_teacher"));
        assignments.ensureIndex(new Index()
                .on("dueDate", Sort.Direction.ASC)
                .on("completed", Sort.Direction.ASC)
                .named("ix_homework_assignments_due_completed"));

        mongoTemplate.getCollection("homeworkAssignments").createIndex(
                new Document("description", "text")
                        .append("subject", "text")
                        .append("teacher", "text"),
                new IndexOptions().name("tx_homework_assignments_search"));
    }

    private void createDiaryIndexes() {
        IndexOperations entries = mongoTemplate.indexOps("diaryEntries");
        entries.ensureIndex(new Index()
                .on("activityDate", Sort.Direction.ASC)
                .on("period", Sort.Direction.ASC)
                .unique().named("ux_diary_entries_date_period"));
        entries.ensureIndex(new Index().on("activityDate", Sort.Direction.DESC)
                .named("ix_diary_entries_activity_date"));
        entries.ensureIndex(new Index().on("subject", Sort.Direction.ASC)
                .named("ix_diary_entries_subject"));
        entries.ensureIndex(new Index().on("teacher", Sort.Direction.ASC)
                .named("ix_diary_entries_teacher"));

        mongoTemplate.getCollection("diaryEntries").createIndex(
                new Document("description", "text")
                        .append("subject", "text")
                        .append("teacher", "text"),
                new IndexOptions().name("tx_diary_entries_search"));
    }

    private void createClusterFileIndexes() {
        IndexOperations files = mongoTemplate.indexOps("clusterFiles");
        files.ensureIndex(new Index().on("sha256", Sort.Direction.ASC)
                .unique().named("ux_cluster_files_sha256"));
        files.ensureIndex(new Index().on("kind", Sort.Direction.ASC)
                .named("ix_cluster_files_kind"));
        files.ensureIndex(new Index().on("createdAt", Sort.Direction.DESC)
                .named("ix_cluster_files_created_at"));
        files.ensureIndex(new Index().on("sourceCodes", Sort.Direction.ASC)
                .named("ix_cluster_files_source_codes"));
    }

    private void createSupportIndexes() {
        IndexOperations issues = mongoTemplate.indexOps("importIssues");
        issues.ensureIndex(new Index().on("lastSeenAt", Sort.Direction.DESC)
                .named("ix_import_issues_last_seen_at"));
    }

    private void createLocalIndexes() {
        IndexOperations subscriptions = localMongoTemplate.indexOps("pushSubscriptions");
        subscriptions.ensureIndex(new Index().on("endpoint", Sort.Direction.ASC)
                .unique().named("ux_push_subscriptions_endpoint"));
        subscriptions.ensureIndex(new Index().on("active", Sort.Direction.ASC)
                .named("ix_push_subscriptions_active"));

        IndexOperations deliveries = localMongoTemplate.indexOps("notificationDeliveries");
        deliveries.ensureIndex(new Index()
                .on("type", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .named("ix_notification_deliveries_type_created"));

        IndexOperations oauthStates = localMongoTemplate.indexOps("googleOauthStates");
        oauthStates.ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC)
                .expire(0)
                .named("ttl_google_oauth_states"));
    }

    private void createGoogleIndexes() {
        IndexOperations emails = mongoTemplate.indexOps("emailMessages");
        emails.ensureIndex(new Index().on("providerMessageId", Sort.Direction.ASC)
                .unique().named("ux_email_messages_provider_message_id"));
        emails.ensureIndex(new Index().on("receivedAt", Sort.Direction.DESC)
                .named("ix_email_messages_received_at"));
        emails.ensureIndex(new Index().on("from", Sort.Direction.ASC)
                .named("ix_email_messages_from"));
        mongoTemplate.getCollection("emailMessages").createIndex(
                new Document("subject", "text")
                        .append("from", "text")
                        .append("bodyText", "text"),
                new IndexOptions().name("tx_email_messages_search"));

        IndexOperations emailAttachments = mongoTemplate.indexOps("emailAttachments");
        emailAttachments.ensureIndex(new Index()
                .on("emailMessageId", Sort.Direction.ASC)
                .on("providerAttachmentId", Sort.Direction.ASC)
                .unique().named("ux_email_attachments_message_provider"));
        emailAttachments.ensureIndex(new Index().on("emailMessageId", Sort.Direction.ASC)
                .named("ix_email_attachments_message"));
        emailAttachments.ensureIndex(new Index().on("sha256", Sort.Direction.ASC)
                .named("ix_email_attachments_sha256"));

        IndexOperations classroomItems = mongoTemplate.indexOps("classroomItems");
        classroomItems.ensureIndex(new Index().on("classroomKey", Sort.Direction.ASC)
                .unique().named("ux_classroom_items_key"));
        classroomItems.ensureIndex(new Index().on("courseId", Sort.Direction.ASC)
                .named("ix_classroom_items_course"));
        classroomItems.ensureIndex(new Index().on("itemType", Sort.Direction.ASC)
                .named("ix_classroom_items_type"));
        classroomItems.ensureIndex(new Index().on("updateTime", Sort.Direction.DESC)
                .named("ix_classroom_items_update_time"));
        mongoTemplate.getCollection("classroomItems").createIndex(
                new Document("title", "text")
                        .append("description", "text")
                        .append("courseName", "text"),
                new IndexOptions().name("tx_classroom_items_search"));

        IndexOperations classroomMaterials = mongoTemplate.indexOps("classroomMaterials");
        classroomMaterials.ensureIndex(new Index()
                .on("itemId", Sort.Direction.ASC)
                .on("externalKey", Sort.Direction.ASC)
                .unique().named("ux_classroom_materials_item_external"));
        classroomMaterials.ensureIndex(new Index().on("itemId", Sort.Direction.ASC)
                .named("ix_classroom_materials_item"));
        classroomMaterials.ensureIndex(new Index().on("status", Sort.Direction.ASC)
                .named("ix_classroom_materials_status"));
    }

    private void dropLegacyIndexes(String collection, List<String> names) {        Set<String> existing = new HashSet<>();
        try {
            mongoTemplate.getCollection(collection).listIndexes()
                    .forEach(index -> existing.add(index.getString("name")));
        } catch (RuntimeException ex) {
            log.debug("Collezione {} non ancora presente: {}", collection, ex.getMessage());
            return;
        }
        for (String name : names) {
            if (!existing.contains(name)) {
                continue;
            }
            try {
                mongoTemplate.getCollection(collection).dropIndex(name);
                log.info("Rimosso indice legacy {} su {}", name, collection);
            } catch (RuntimeException ex) {
                log.warn("Impossibile rimuovere l'indice legacy {} su {}: {}",
                        name, collection, ex.getMessage());
            }
        }
    }
}
