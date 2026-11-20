package it.mida.imports;

import it.mida.cluster.ConditionalOnMidaRole;
import it.mida.cluster.MidaRole;
import it.mida.common.ImportReference;
import it.mida.config.MidaProperties;
import it.mida.diary.DiaryEntryWriter;
import it.mida.homework.HomeworkAssignmentWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Migra una tantum i compiti e le attività incorporate nei documenti di
 * importazione verso le collezioni {@code homeworkAssignments} e
 * {@code diaryEntries}.
 *
 * <p>La migrazione è idempotente e conserva gli identificativi originali e lo
 * stato "completato". Ogni documento di importazione viene marcato e ripulito
 * singolarmente: se il processo si interrompe, al riavvio riprende solo dai
 * documenti non ancora migrati. Un'importazione che contiene dati non
 * interpretabili resta intatta e viene ritentata, senza cancellare nulla.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnMidaRole({MidaRole.STANDALONE, MidaRole.MASTER})
public class LegacyDataMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyDataMigrator.class);

    private static final String HOMEWORK_COLLECTION = "homeworkImports";
    private static final String DIARY_COLLECTION = "diaryImports";

    private final MongoTemplate mongoTemplate;
    private final HomeworkAssignmentWriter homeworkWriter;
    private final DiaryEntryWriter diaryWriter;
    private final MidaProperties properties;

    public LegacyDataMigrator(
            MongoTemplate mongoTemplate,
            HomeworkAssignmentWriter homeworkWriter,
            DiaryEntryWriter diaryWriter,
            MidaProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.homeworkWriter = homeworkWriter;
        this.diaryWriter = diaryWriter;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            migrateHomework();
        } catch (RuntimeException ex) {
            log.error("Migrazione dei compiti incorporati non riuscita", ex);
        }
        try {
            migrateDiary();
        } catch (RuntimeException ex) {
            log.error("Migrazione delle attività del diario incorporate non riuscita", ex);
        }
    }

    private void migrateHomework() {
        List<Document> imports = legacyDocuments(HOMEWORK_COLLECTION, "assignments");
        if (imports.isEmpty()) {
            return;
        }
        int migratedImports = 0;
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (Document imported : imports) {
            if (Boolean.TRUE.equals(imported.getBoolean("assignmentsMigrated"))) {
                // Migrazione completata ma pulizia non riuscita: rimuove solo
                // l'array, senza riscrivere i compiti.
                unsetArray(imported, HOMEWORK_COLLECTION, "assignments");
                continue;
            }
            List<Document> assignments = imported.getList("assignments", Document.class);
            if (assignments == null) {
                assignments = List.of();
            }
            ImportReference reference = reference(imported);
            int importCreated = 0;
            int importUpdated = 0;
            int importUnchanged = 0;
            try {
                for (Document assignment : assignments) {
                    HomeworkAssignmentWriter.LegacyAssignment legacy = legacyAssignment(assignment);
                    switch (homeworkWriter.importLegacy(legacy, reference)) {
                        case CREATED -> importCreated++;
                        case UPDATED -> importUpdated++;
                        case UNCHANGED -> importUnchanged++;
                    }
                }
            } catch (RuntimeException ex) {
                log.error(
                        "Compito non migrabile nell'import {}: il documento resta intatto",
                        reference.importId(),
                        ex);
                continue;
            }

            Update update = new Update()
                    .set("assignmentsMigrated", true)
                    .set("createdCount", importCreated)
                    .set("updatedCount", importUpdated)
                    .set("unchangedCount", importUnchanged)
                    .unset("assignments");
            applyMigration(imported, HOMEWORK_COLLECTION, update);

            migratedImports++;
            created += importCreated;
            updated += importUpdated;
            unchanged += importUnchanged;
        }
        if (migratedImports > 0) {
            log.info(
                    "Migrati {} import di compiti: {} creati, {} aggiornati, {} già presenti",
                    migratedImports,
                    created,
                    updated,
                    unchanged);
        }
    }

    private void migrateDiary() {
        List<Document> imports = legacyDocuments(DIARY_COLLECTION, "entries");
        if (imports.isEmpty()) {
            return;
        }
        int migratedImports = 0;
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (Document imported : imports) {
            if (Boolean.TRUE.equals(imported.getBoolean("entriesMigrated"))) {
                unsetArray(imported, DIARY_COLLECTION, "entries");
                continue;
            }
            List<Document> entries = imported.getList("entries", Document.class);
            if (entries == null) {
                entries = List.of();
            }
            ImportReference reference = reference(imported);
            int importCreated = 0;
            int importUpdated = 0;
            int importUnchanged = 0;
            try {
                for (Document entry : entries) {
                    DiaryEntryWriter.LegacyEntry legacy = legacyDiaryEntry(entry);
                    switch (diaryWriter.importLegacy(legacy, reference)) {
                        case CREATED -> importCreated++;
                        case UPDATED -> importUpdated++;
                        case UNCHANGED -> importUnchanged++;
                    }
                }
            } catch (RuntimeException ex) {
                log.error(
                        "Attività del diario non migrabile nell'import {}: il documento resta intatto",
                        reference.importId(),
                        ex);
                continue;
            }

            Update update = new Update()
                    .set("entriesMigrated", true)
                    .set("createdCount", importCreated)
                    .set("updatedCount", importUpdated)
                    .set("unchangedCount", importUnchanged)
                    .unset("entries");
            applyMigration(imported, DIARY_COLLECTION, update);

            migratedImports++;
            created += importCreated;
            updated += importUpdated;
            unchanged += importUnchanged;
        }
        if (migratedImports > 0) {
            log.info(
                    "Migrati {} import di diario: {} create, {} aggiornate, {} già presenti",
                    migratedImports,
                    created,
                    updated,
                    unchanged);
        }
    }

    private List<Document> legacyDocuments(String collection, String arrayField) {
        Query query = new Query(Criteria.where(arrayField).exists(true))
                .with(Sort.by(Sort.Order.asc("importedAt"), Sort.Order.asc("_id")));
        return mongoTemplate.find(query, Document.class, collection);
    }

    private void applyMigration(Document imported, String collection, Update update) {
        var result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(imported.get("_id"))), update, collection);
        if (result.getMatchedCount() == 0) {
            throw new IllegalStateException(
                    "Importazione non trovata durante la migrazione: " + imported.get("_id"));
        }
    }

    private void unsetArray(Document imported, String collection, String arrayField) {
        try {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(imported.get("_id"))),
                    new Update().unset(arrayField),
                    collection);
        } catch (RuntimeException ex) {
            log.warn(
                    "Impossibile rimuovere l'array legacy {} dall'import {}: {}",
                    arrayField,
                    imported.get("_id"),
                    ex.getMessage());
        }
    }

    private ImportReference reference(Document imported) {
        String importId = text(imported.get("_id"));
        return new ImportReference(
                importId,
                text(imported.get("sourceCode")),
                text(imported.get("originalFilename")),
                instant(imported.get("importedAt")));
    }

    private HomeworkAssignmentWriter.LegacyAssignment legacyAssignment(Document value) {
        return new HomeworkAssignmentWriter.LegacyAssignment(
                text(value.get("id")),
                localDate(value.get("assignedDate")),
                text(value.get("assignedDateRaw")),
                localDate(value.get("dueDate")),
                text(value.get("dueDateRaw")),
                text(value.get("subject")),
                text(value.get("teacher")),
                text(value.get("description")),
                Boolean.TRUE.equals(value.getBoolean("completed")),
                instant(value.get("completedAt")));
    }

    private DiaryEntryWriter.LegacyEntry legacyDiaryEntry(Document value) {
        return new DiaryEntryWriter.LegacyEntry(
                text(value.get("id")),
                localDate(value.get("activityDate")),
                text(value.get("activityDateRaw")),
                intValue(value.get("period")),
                text(value.get("periodLabel")),
                text(value.get("subject")),
                text(value.get("teacher")),
                text(value.get("description")));
    }

    private LocalDate localDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toInstant().atZone(schoolZone()).toLocalDate();
        }
        if (value instanceof String text) {
            return LocalDate.parse(text);
        }
        throw new IllegalArgumentException("Data legacy non riconosciuta: " + value);
    }

    private Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof String text) {
            return Instant.parse(text);
        }
        throw new IllegalArgumentException("Istante legacy non riconosciuto: " + value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            return Integer.parseInt(text);
        }
        return 0;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private ZoneId schoolZone() {
        return properties.getTimeZone() == null ? ZoneId.of("Europe/Rome") : properties.getTimeZone();
    }
}
