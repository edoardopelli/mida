package it.mida;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.mida.common.ImportReference;
import it.mida.diary.DiaryEntry;
import it.mida.diary.DiaryEntryRepository;
import it.mida.diary.DiaryEntryWriter;
import it.mida.diary.DiaryImport;
import it.mida.diary.DiaryImportRepository;
import it.mida.diary.DiaryQueryService;
import it.mida.attachments.AttachmentStorageService;
import it.mida.config.MidaProperties;
import it.mida.google.classroom.ClassroomItem;
import it.mida.google.classroom.ClassroomItemRepository;
import it.mida.google.classroom.ClassroomMaterial;
import it.mida.google.classroom.ClassroomMaterialRepository;
import it.mida.google.email.EmailAttachment;
import it.mida.google.email.EmailAttachmentRepository;
import it.mida.google.email.EmailMessage;
import it.mida.google.email.EmailMessageRepository;
import it.mida.homework.HomeworkAssignment;
import it.mida.homework.HomeworkAssignmentRepository;
import it.mida.homework.HomeworkAssignmentWriter;
import it.mida.homework.HomeworkImport;
import it.mida.homework.HomeworkImportRepository;
import it.mida.homework.HomeworkQueryService;
import it.mida.homework.api.HomeworkQuery;
import it.mida.imports.DownloadsMonitor;
import it.mida.imports.ImportService;
import it.mida.imports.ImportStatusService;
import it.mida.imports.LegacyDataMigrator;
import it.mida.issues.ImportIssue;
import it.mida.issues.ImportIssueRepository;
import it.mida.notifications.NotificationDeliveryRepository;
import it.mida.notifications.NotificationPreferencesRepository;
import it.mida.notifications.PushSubscriptionRepository;
import it.mida.parser.DidupDiaryParser;
import it.mida.parser.DidupFileName;
import it.mida.parser.DidupFileParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MidaIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:8.0");

    static final Path importsDirectory;

    static {
        try {
            importsDirectory = Files.createTempDirectory("mida-import-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("mida.imports.directory", () -> importsDirectory.toString());
        registry.add("mida.imports.enabled", () -> "true");
        registry.add("mida.imports.poll-interval-ms", () -> "3600000");
        registry.add("mida.imports.initial-delay-ms", () -> "3600000");
        registry.add("mida.imports.stability-scans", () -> "2");
        registry.add("mida.imports.max-file-size-bytes", () -> "4096");
        registry.add("mida.school-year-start", () -> "2026");
        registry.add("mida.notifications.enabled", () -> "false");
    }

    @Autowired
    HomeworkImportRepository importRepository;
    @Autowired
    HomeworkAssignmentRepository assignmentRepository;
    @Autowired
    HomeworkAssignmentWriter assignmentWriter;
    @Autowired
    DiaryImportRepository diaryImportRepository;
    @Autowired
    DiaryEntryRepository diaryEntryRepository;
    @Autowired
    DiaryEntryWriter diaryEntryWriter;
    @Autowired
    DiaryQueryService diaryQueryService;
    @Autowired
    ImportIssueRepository issueRepository;
    @Autowired
    NotificationDeliveryRepository deliveryRepository;
    @Autowired
    NotificationPreferencesRepository preferencesRepository;
    @Autowired
    PushSubscriptionRepository subscriptionRepository;
    @Autowired
    HomeworkQueryService queryService;
    @Autowired
    DownloadsMonitor monitor;
    @Autowired
    ImportStatusService statusService;
    @Autowired
    LegacyDataMigrator legacyDataMigrator;
    @Autowired
    ImportService importService;
    @Autowired
    MidaProperties midaProperties;
    @Autowired
    AttachmentStorageService googleFileService;
    @Autowired
    EmailMessageRepository emailMessageRepository;
    @Autowired
    EmailAttachmentRepository emailAttachmentRepository;
    @Autowired
    ClassroomItemRepository classroomItemRepository;
    @Autowired
    ClassroomMaterialRepository classroomMaterialRepository;
    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    MockMvc mockMvc;

    private static final AtomicInteger CODE_SEQUENCE = new AtomicInteger(100);

    @BeforeEach
    void cleanDatabaseAndDirectory() throws IOException {
        deliveryRepository.deleteAll();
        preferencesRepository.deleteAll();
        subscriptionRepository.deleteAll();
        issueRepository.deleteAll();
        assignmentRepository.deleteAll();
        diaryEntryRepository.deleteAll();
        importRepository.deleteAll();
        diaryImportRepository.deleteAll();
        emailAttachmentRepository.deleteAll();
        emailMessageRepository.deleteAll();
        classroomMaterialRepository.deleteAll();
        classroomItemRepository.deleteAll();
        mongoTemplate.getCollection("googleConnection").deleteMany(new Document());
        mongoTemplate.getCollection("googleSettings").deleteMany(new Document());
        mongoTemplate.getCollection("googleOauthStates").deleteMany(new Document());
        mongoTemplate.getCollection("gmailSyncState").deleteMany(new Document());
        mongoTemplate.getCollection("fs.files").deleteMany(new Document());
        mongoTemplate.getCollection("fs.chunks").deleteMany(new Document());
        if (Files.isDirectory(importsDirectory)) {
            try (var stream = Files.list(importsDirectory)) {
                stream.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
        }
    }

    @AfterAll
    static void cleanupDirectory() throws IOException {
        if (Files.isDirectory(importsDirectory)) {
            try (var stream = Files.list(importsDirectory)) {
                for (Path path : stream.toList()) {
                    Files.deleteIfExists(path);
                }
            }
            Files.deleteIfExists(importsDirectory);
        }
    }

    @Test
    void storesEachAssignmentAsItsOwnDocument() {
        HomeworkImport imported = seedHomework("AAAAAAAAAAAA", List.of(
                parsed("Arte ed Immagine", "A. Fertitta", LocalDate.of(2026, 11, 11),
                        LocalDate.of(2026, 11, 18), "Tavola LEGO"),
                parsed("Matematica", "C. Ariotti", LocalDate.of(2026, 11, 11),
                        LocalDate.of(2026, 11, 18), "Svolgere es")));

        List<HomeworkAssignment> stored = assignmentRepository.findAll();
        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(HomeworkAssignment::getId)
                .doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(id).isNotBlank());
        assertThat(stored).extracting(HomeworkAssignment::getDescription)
                .containsExactlyInAnyOrder("Tavola LEGO", "Svolgere es");
        assertThat(stored).allSatisfy(assignment -> {
            assertThat(assignment.getContentHash()).isNotBlank();
            assertThat(assignment.getSlotKey()).isNotBlank();
            assertThat(assignment.getBaseKey()).isNotBlank();
            assertThat(assignment.getRevision()).isEqualTo(1);
            assertThat(assignment.getFirstImportId()).isEqualTo(imported.getId());
        });
        assertThat(importRepository.findById(imported.getId()).orElseThrow().getCreatedCount()).isEqualTo(2);
    }

    @Test
    void enforcesUniqueSourceCodeAndContentHashAtDatabaseLevel() {
        seedHomework("CCCCCCCCCCCC", List.of(
                parsed("Italiano", "G. Fazio", LocalDate.of(2026, 11, 12),
                        LocalDate.of(2026, 11, 20), "Antologia")));

        HomeworkImport duplicateCode = new HomeworkImport();
        duplicateCode.setSourceCode("CCCCCCCCCCCC");
        duplicateCode.setOriginalFilename("text-CCCCCCCCCCCC-2.txt");
        duplicateCode.setSha256("d".repeat(64));
        duplicateCode.setImportedAt(Instant.parse("2026-11-13T10:00:00Z"));
        duplicateCode.setAssignmentCount(0);
        duplicateCode.setSchoolYearStart(2026);
        assertThatThrownBy(() -> importRepository.save(duplicateCode))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);

        HomeworkAssignment first = assignmentRepository.findAll().getFirst();
        HomeworkAssignment duplicate = new HomeworkAssignment();
        duplicate.setId(UUID.randomUUID().toString());
        duplicate.setContentHash(first.getContentHash());
        duplicate.setSlotKey(first.getSlotKey());
        duplicate.setBaseKey(first.getBaseKey());
        duplicate.setDescriptionHash(first.getDescriptionHash());
        duplicate.setAssignedDate(first.getAssignedDate());
        duplicate.setDueDate(first.getDueDate());
        duplicate.setSubject(first.getSubject());
        duplicate.setTeacher(first.getTeacher());
        duplicate.setDescription("Descrizione diversa ma stesso hash forzato");
        assertThatThrownBy(() -> assignmentRepository.save(duplicate))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);

        List<IndexInfo> importIndexes = mongoTemplate.indexOps("homeworkImports").getIndexInfo();
        IndexInfo sourceCodeIndex = importIndexes.stream()
                .filter(index -> index.getName().equals("ux_homework_imports_source_code"))
                .findFirst().orElseThrow();
        assertThat(sourceCodeIndex.isUnique()).isTrue();
        assertThat(importIndexes).extracting(IndexInfo::getName)
                .doesNotContain("ux_homework_assignments_id", "ix_homework_assignments_due_date");

        List<IndexInfo> assignmentIndexes = mongoTemplate.indexOps("homeworkAssignments").getIndexInfo();
        assertThat(assignmentIndexes.stream()
                .filter(index -> index.getName().equals("ux_homework_assignments_content_hash"))
                .findFirst().orElseThrow().isUnique()).isTrue();
        assertThat(assignmentIndexes).extracting(IndexInfo::getName)
                .contains("ix_homework_assignments_slot_key", "ix_homework_assignments_base_key",
                        "ix_homework_assignments_due_date", "tx_homework_assignments_search");

        List<IndexInfo> diaryIndexes = mongoTemplate.indexOps("diaryEntries").getIndexInfo();
        assertThat(diaryIndexes.stream()
                .filter(index -> index.getName().equals("ux_diary_entries_date_period"))
                .findFirst().orElseThrow().isUnique()).isTrue();
    }

    @Test
    void queriesByDateSubjectTeacherCompletionAndText() {
        HomeworkImport first = seedHomework("DDDDDDDDDDDD", List.of(
                parsed("Arte ed Immagine", "A. Fertitta", LocalDate.of(2026, 11, 11),
                        LocalDate.of(2026, 11, 18), "Tavola LEGO con sfumature"),
                parsed("Matematica", "C. Ariotti", LocalDate.of(2026, 11, 11),
                        LocalDate.of(2026, 11, 18), "Svolgere esercizi pagina 111")));
        seedHomework("EEEEEEEEEEEE", List.of(
                parsed("Italiano", "G. Fazio", LocalDate.of(2026, 11, 12),
                        LocalDate.of(2026, 11, 20), "Antologia Dal testo al genere")));

        HomeworkAssignment math = assignmentRepository.findAll().stream()
                .filter(item -> item.getSubject().equals("Matematica"))
                .findFirst().orElseThrow();
        math.setCompleted(true);
        math.setCompletedAt(Instant.parse("2026-11-13T09:00:00Z"));
        assignmentRepository.save(math);

        var all = queryService.search(new HomeworkQuery(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                HomeworkQuery.DateField.DUE, null, null, null, null, 0, 20));
        assertThat(all.totalElements()).isEqualTo(3);
        assertThat(all.content()).extracting(item -> item.subject())
                .containsExactly("Arte ed Immagine", "Matematica", "Italiano");

        var bySubject = queryService.search(new HomeworkQuery(
                null, null, HomeworkQuery.DateField.DUE, "Matematica", "C. Ariotti",
                true, null, 0, 20));
        assertThat(bySubject.content()).hasSize(1);
        assertThat(bySubject.content().getFirst().subject()).isEqualTo("Matematica");

        var search = queryService.search(new HomeworkQuery(
                null, null, HomeworkQuery.DateField.DUE, null, null, null, "LEGO", 0, 20));
        assertThat(search.content()).hasSize(1);
        assertThat(search.content().getFirst().importId()).isEqualTo(first.getId());

        var calendar = queryService.calendar(new HomeworkQuery(
                LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 18),
                HomeworkQuery.DateField.DUE, null, null, null, null, 0, 100));
        assertThat(calendar).hasSize(2);

        var classes = queryService.calendar(new HomeworkQuery(
                LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12),
                HomeworkQuery.DateField.ASSIGNED, null, null, null, null, 0, 100));
        assertThat(classes).hasSize(3);

        assertThat(queryService.subjects()).containsExactly("Arte ed Immagine", "Italiano", "Matematica");
        assertThat(queryService.teachers()).containsExactly("A. Fertitta", "C. Ariotti", "G. Fazio");
        assertThat(queryService.completedCount()).isEqualTo(1);
        assertThat(queryService.findById(all.content().getFirst().id())).isPresent();
    }

    @Test
    void updatesOnlyTheSelectedAssignment() throws Exception {
        seedHomework("FFFFFFFFFFFF", List.of(
                parsed("Arte ed Immagine", "A. Fertitta", LocalDate.of(2026, 11, 11),
                        LocalDate.of(2026, 11, 18), "Tavola LEGO"),
                parsed("Matematica", "C. Ariotti", LocalDate.of(2026, 11, 11),
                        LocalDate.of(2026, 11, 18), "Svolgere es")));
        HomeworkAssignment first = byDescription("Tavola LEGO");
        HomeworkAssignment second = byDescription("Svolgere es");

        mockMvc.perform(patch("/api/homework/{id}/completion", first.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.getId()))
                .andExpect(jsonPath("$.completed").value(true));

        HomeworkAssignment reloadedFirst = assignmentRepository.findById(first.getId()).orElseThrow();
        HomeworkAssignment reloadedSecond = assignmentRepository.findById(second.getId()).orElseThrow();
        assertThat(reloadedFirst.isCompleted()).isTrue();
        assertThat(reloadedFirst.getCompletedAt()).isNotNull();
        assertThat(reloadedSecond.isCompleted()).isFalse();
    }

    @Test
    void exposesRestApiAndStatus() throws Exception {
        HomeworkImport imported = seedHomework("111111111111", List.of(
                parsed("Matematica", "C. Ariotti", LocalDate.of(2026, 11, 11),
                        LocalDate.of(2026, 11, 18), "Svolgere es")));

        mockMvc.perform(get("/api/homework").param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("Matematica"))
                .andExpect(jsonPath("$.content[0].importId").value(imported.getId()))
                .andExpect(jsonPath("$.content[0].revision").value(1));

        mockMvc.perform(get("/api/homework/calendar")
                        .param("from", "2026-11-18").param("to", "2026-11-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Svolgere es"));

        mockMvc.perform(get("/api/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sourceCode").value("111111111111"))
                .andExpect(jsonPath("$.content[0].assignmentCount").value(1))
                .andExpect(jsonPath("$.content[0].createdCount").value(1));

        mockMvc.perform(get("/api/imports/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalImports").value(1))
                .andExpect(jsonPath("$.totalAssignments").value(1));

        mockMvc.perform(get("/api/homework/subjects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Matematica"));
    }

    @Test
    void importsNewFileAfterTwoStableScansAndDeletesIt() throws Exception {
        String code = nextCode();
        String content = validContent("Compito importato");
        Path file = importsDirectory.resolve("text-" + code + "-1.txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);

        monitor.scan();
        assertThat(importRepository.findBySourceCode(code)).isEmpty();
        assertThat(file).exists();

        monitor.scan();
        HomeworkImport imported = importRepository.findBySourceCode(code).orElseThrow();
        assertThat(imported.getAssignmentCount()).isEqualTo(1);
        assertThat(imported.getCreatedCount()).isEqualTo(1);
        assertThat(imported.getUpdatedCount()).isZero();
        assertThat(imported.getUnchangedCount()).isZero();
        assertThat(file).doesNotExist();
        assertThat(assignmentRepository.count()).isEqualTo(1);

        ImportStatusService.ImportStatusDto status = statusService.status();
        assertThat(status.totalImports()).isEqualTo(1);
        assertThat(status.totalAssignments()).isEqualTo(1);
    }

    @Test
    void standaloneImportWithDeletionDisabledKeepsTheSourceFile() throws Exception {
        boolean previous = midaProperties.getImports().isDeleteAfterImport();
        midaProperties.getImports().setDeleteAfterImport(false);
        try {
            String code = nextCode();
            String content = validContent("Compito senza cancellazione");
            Path file = importsDirectory.resolve("text-" + code + ".txt");
            Files.writeString(file, content, StandardCharsets.UTF_8);

            monitor.scan();
            monitor.scan();

            assertThat(importRepository.findBySourceCode(code)).isPresent();
            assertThat(file).exists();
            assertThat(issueRepository.findAll()).noneMatch(issue ->
                    issue.getType() == ImportIssue.Type.FILE_DELETION_FAILED);
        } finally {
            midaProperties.getImports().setDeleteAfterImport(previous);
        }
    }

    @Test
    void recognizesIdenticalFileAlreadyImportedAndRemovesItWithoutDuplicating() throws Exception {
        String code = nextCode();
        String content = validContent("Compito duplicato");
        Path file = importsDirectory.resolve("text-" + code + ".txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();
        assertThat(file).doesNotExist();
        assertThat(importRepository.findBySourceCode(code).orElseThrow().getAssignmentCount()).isEqualTo(1);

        Files.writeString(file, content, StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();
        assertThat(file).doesNotExist();
        assertThat(importRepository.findAll()).hasSize(1);
        assertThat(assignmentRepository.count()).isEqualTo(1);
    }

    @Test
    void deduplicatesIdenticalContentSharedWithDifferentFileCodes() throws Exception {
        String firstCode = nextCode();
        String secondCode = nextCode();
        String content = validContent("Compito condiviso con due codici");
        Path first = importsDirectory.resolve("text-" + firstCode + ".txt");
        Path second = importsDirectory.resolve("text-" + secondCode + ".txt");

        Files.writeString(first, content, StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();
        assertThat(first).doesNotExist();
        assertThat(importRepository.findBySourceCode(firstCode)).isPresent();

        Files.writeString(second, content, StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();
        assertThat(second).doesNotExist();
        assertThat(importRepository.findBySourceCode(secondCode)).isEmpty();
        assertThat(importRepository.findAll()).hasSize(1);
        assertThat(assignmentRepository.count()).isEqualTo(1);
        assertThat(issueRepository.findAll()).isEmpty();
    }

    @Test
    void deduplicatesTheSameAssignmentAppearingInDifferentFiles() throws Exception {
        String mathCode = nextCode();
        String mixedCode = nextCode();
        String math = homeworkContent(
                "11 NOVEMBRE", "MATEMATICA", "C. ARIOTTI",
                "Svolgere esercizi pagina 111", "MERCOLEDÌ 18 NOVEMBRE");
        String mixed = math + """

                ASSEGNATI IL 12 NOVEMBRE

                ITALIANO (G. FAZIO):
                Antologia Dal testo al genere
                --- CONSEGNA VENERDÌ 20 NOVEMBRE ---
                """;

        Files.writeString(importsDirectory.resolve("text-" + mathCode + ".txt"), math, StandardCharsets.UTF_8);
        Files.writeString(importsDirectory.resolve("text-" + mixedCode + ".txt"), mixed, StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();

        assertThat(importRepository.findAll()).hasSize(2);
        assertThat(assignmentRepository.count()).isEqualTo(2);
        assertThat(assignmentRepository.findAll())
                .filteredOn(assignment -> assignment.getSubject().equals("MATEMATICA"))
                .hasSize(1);
        assertThat(assignmentRepository.findAll())
                .filteredOn(assignment -> assignment.getSubject().equals("MATEMATICA"))
                .allSatisfy(assignment -> assertThat(assignment.getRevision()).isEqualTo(1));
    }

    @Test
    void updatesTheSameAssignmentWhenTheTeacherExtendsTheDescription() throws Exception {
        String firstCode = nextCode();
        String secondCode = nextCode();
        Files.writeString(importsDirectory.resolve("text-" + firstCode + ".txt"),
                homeworkContent("11 NOVEMBRE", "ARTE ED IMMAGINE", "A. FERTITTA",
                        "Portare il materiale di arte", "MERCOLEDÌ 18 NOVEMBRE"),
                StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();

        HomeworkAssignment original = assignmentRepository.findAll().getFirst();
        mockMvc.perform(patch("/api/homework/{id}/completion", original.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk());

        Files.writeString(importsDirectory.resolve("text-" + secondCode + ".txt"),
                homeworkContent("11 NOVEMBRE", "ARTE ED IMMAGINE", "A. FERTITTA",
                        "Portare il materiale di arte e il foglio d'album con i colori",
                        "MERCOLEDÌ 18 NOVEMBRE"),
                StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();

        assertThat(assignmentRepository.count()).isEqualTo(1);
        HomeworkAssignment updated = assignmentRepository.findById(original.getId()).orElseThrow();
        assertThat(updated.getDescription())
                .isEqualTo("Portare il materiale di arte e il foglio d'album con i colori");
        assertThat(updated.getRevision()).isEqualTo(2);
        assertThat(updated.isCompleted()).isTrue();
        assertThat(updated.getCompletedAt()).isNotNull();
        assertThat(updated.getFirstImportId()).isEqualTo(original.getFirstImportId());

        HomeworkImport second = importRepository.findBySourceCode(secondCode).orElseThrow();
        assertThat(second.getCreatedCount()).isZero();
        assertThat(second.getUpdatedCount()).isEqualTo(1);
        assertThat(second.getUnchangedCount()).isZero();
    }

    @Test
    void movesTheDeadlineWhenItIsTheOnlyChange() throws Exception {
        String firstCode = nextCode();
        String secondCode = nextCode();
        String description = "Svolgere gli esercizi sul quaderno";
        Files.writeString(importsDirectory.resolve("text-" + firstCode + ".txt"),
                homeworkContent("11 NOVEMBRE", "MATEMATICA", "C. ARIOTTI",
                        description, "MERCOLEDÌ 18 NOVEMBRE"),
                StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();

        HomeworkAssignment original = assignmentRepository.findAll().getFirst();

        Files.writeString(importsDirectory.resolve("text-" + secondCode + ".txt"),
                homeworkContent("11 NOVEMBRE", "MATEMATICA", "C. ARIOTTI",
                        description, "VENERDÌ 20 NOVEMBRE"),
                StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();

        assertThat(assignmentRepository.count()).isEqualTo(1);
        HomeworkAssignment updated = assignmentRepository.findById(original.getId()).orElseThrow();
        assertThat(updated.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(updated.getRevision()).isEqualTo(2);
        assertThat(updated.getDescription()).isEqualTo(description);
    }

    @Test
    void keepsDistinctAssignmentsWithDifferentContentInTheSameSlot() throws Exception {
        String firstCode = nextCode();
        String secondCode = nextCode();
        Files.writeString(importsDirectory.resolve("text-" + firstCode + ".txt"),
                homeworkContent("11 NOVEMBRE", "MATEMATICA", "C. ARIOTTI",
                        "Leggere pagina 10", "MERCOLEDÌ 18 NOVEMBRE"),
                StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();

        Files.writeString(importsDirectory.resolve("text-" + secondCode + ".txt"),
                homeworkContent("11 NOVEMBRE", "MATEMATICA", "C. ARIOTTI",
                        "Leggere pagina 11", "MERCOLEDÌ 18 NOVEMBRE"),
                StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();

        assertThat(assignmentRepository.count()).isEqualTo(2);
        assertThat(assignmentRepository.findAll())
                .extracting(HomeworkAssignment::getDescription)
                .containsExactlyInAnyOrder("Leggere pagina 10", "Leggere pagina 11");
        assertThat(assignmentRepository.findAll())
                .allSatisfy(assignment -> assertThat(assignment.getRevision()).isEqualTo(1));
    }

    @Test
    void keepsAndReportsConflictingFileForTheSameSourceCode() throws Exception {
        String code = nextCode();
        Path file = importsDirectory.resolve("text-" + code + ".txt");
        Files.writeString(file, validContent("Versione originale"), StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();
        assertThat(importRepository.findBySourceCode(code)).isPresent();

        Files.writeString(file, validContent("Versione differente"), StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();

        assertThat(importRepository.findAll()).hasSize(1);
        assertThat(assignmentRepository.findAll()).hasSize(1)
                .allSatisfy(assignment -> assertThat(assignment.getDescription()).isEqualTo("Versione originale"));
        assertThat(file).exists();
        assertThat(issueRepository.findAll())
                .anySatisfy(issue -> {
                    assertThat(issue.getType()).isEqualTo(ImportIssue.Type.DUPLICATE_CONFLICT);
                    assertThat(issue.getStatus()).isEqualTo(ImportIssue.Status.OPEN);
                });
    }

    @Test
    void importsDiaryFileAndExposesActivities() throws Exception {
        String code = nextCode();
        Path file = importsDirectory.resolve("text-" + code + "-1.txt");
        Files.writeString(file, diaryContent(), StandardCharsets.UTF_8);

        monitor.scan();
        assertThat(diaryImportRepository.findBySourceCode(code)).isEmpty();
        assertThat(file).exists();

        monitor.scan();
        var diary = diaryImportRepository.findBySourceCode(code).orElseThrow();
        assertThat(diary.getEntryCount()).isEqualTo(5);
        assertThat(diary.getCreatedCount()).isEqualTo(5);
        assertThat(file).doesNotExist();
        assertThat(importRepository.findAll()).isEmpty();

        List<DiaryEntry> entries = diaryEntryRepository
                .findByActivityDateOrderByPeriodAsc(LocalDate.of(2026, 11, 13));
        assertThat(entries).hasSize(5);
        assertThat(entries).extracting(DiaryEntry::getId)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
        assertThat(entries).extracting(DiaryEntry::getPeriod)
                .containsExactly(1, 2, 4, 5, 6);
        assertThat(entries).extracting(DiaryEntry::getRevision)
                .containsOnly(1);

        mockMvc.perform(get("/api/diary/day").param("date", "2026-11-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].subject").value("INGLESE"))
                .andExpect(jsonPath("$[0].periodLabel").value("1° ORA"))
                .andExpect(jsonPath("$[4].teacher").value("D. FRATTA"));

        mockMvc.perform(get("/api/diary/dates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-11-13"))
                .andExpect(jsonPath("$[0].entryCount").value(5));

        mockMvc.perform(get("/api/imports/diary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sourceCode").value(code))
                .andExpect(jsonPath("$.content[0].entryCount").value(5))
                .andExpect(jsonPath("$.content[0].createdCount").value(5));

        ImportStatusService.ImportStatusDto status = statusService.status();
        assertThat(status.totalDiaryImports()).isEqualTo(1);
        assertThat(status.totalDiaryEntries()).isEqualTo(5);
        assertThat(status.totalImports()).isZero();
    }

    @Test
    void updatesADiaryEntryWhenTheTeacherChangesTheSamePeriod() throws Exception {
        String firstCode = nextCode();
        String secondCode = nextCode();
        Files.writeString(importsDirectory.resolve("text-" + firstCode + ".txt"), diaryContent(), StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();

        DiaryEntry original = diaryEntryRepository
                .findByActivityDateAndPeriod(LocalDate.of(2026, 11, 13), 1)
                .orElseThrow();

        String updatedDiary = """
                Attività svolta il 13 NOVEMBRE

                1° ORA
                INGLESE (E. NIFOSI)
                Virtual classmates aggiornato


                7° ORA
                STORIA (L. BIANCHI)
                Nuova attività di storia
                """;
        Files.writeString(importsDirectory.resolve("text-" + secondCode + ".txt"), updatedDiary, StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();

        assertThat(diaryEntryRepository.count()).isEqualTo(6);
        DiaryEntry updated = diaryEntryRepository.findById(original.getId()).orElseThrow();
        assertThat(updated.getDescription()).isEqualTo("Virtual classmates aggiornato");
        assertThat(updated.getRevision()).isEqualTo(2);
        assertThat(updated.getFirstImportId()).isEqualTo(original.getFirstImportId());

        DiaryImport second = diaryImportRepository.findBySourceCode(secondCode).orElseThrow();
        assertThat(second.getCreatedCount()).isEqualTo(1);
        assertThat(second.getUpdatedCount()).isEqualTo(1);
        assertThat(second.getUnchangedCount()).isZero();

        mockMvc.perform(get("/api/diary/day").param("date", "2026-11-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].description").value("Virtual classmates aggiornato"));
    }

    @Test
    void deduplicatesDiaryFileBySourceCodeAndHash() throws Exception {
        String code = nextCode();
        String content = diaryContent();
        Path file = importsDirectory.resolve("text-" + code + ".txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();
        assertThat(diaryImportRepository.findBySourceCode(code)).isPresent();
        assertThat(file).doesNotExist();

        Files.writeString(file, content, StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();
        assertThat(diaryImportRepository.findAll()).hasSize(1);
        assertThat(file).doesNotExist();
        assertThat(diaryEntryRepository.count()).isEqualTo(5);

        String otherCode = nextCode();
        Path otherFile = importsDirectory.resolve("text-" + otherCode + ".txt");
        Files.writeString(otherFile, content, StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();
        assertThat(otherFile).doesNotExist();
        assertThat(diaryImportRepository.findBySourceCode(otherCode)).isEmpty();
        assertThat(diaryImportRepository.findAll()).hasSize(1);

        Files.writeString(file, content.replace("Virtual classmates", "Attività differente"), StandardCharsets.UTF_8);
        monitor.scan();
        monitor.scan();
        assertThat(diaryImportRepository.findAll()).hasSize(1);
        assertThat(file).exists();
        assertThat(issueRepository.findAll()).anySatisfy(issue -> {
            assertThat(issue.getType()).isEqualTo(ImportIssue.Type.DUPLICATE_CONFLICT);
            assertThat(issue.getStatus()).isEqualTo(ImportIssue.Status.OPEN);
        });
    }

    @Test
    void migratesLegacyEmbeddedAssignmentsAndDiaryEntriesPreservingIdsAndCompletion() {
        Date assigned = mongoDate(LocalDate.of(2026, 11, 11));
        Date due = mongoDate(LocalDate.of(2026, 11, 18));
        Document legacyAssignment = new Document("id", "legacy-assignment-1")
                .append("assignedDate", assigned)
                .append("assignedDateRaw", "11 NOVEMBRE")
                .append("dueDate", due)
                .append("dueDateRaw", "MERCOLEDÌ 18 NOVEMBRE")
                .append("subject", "MATEMATICA")
                .append("teacher", "C. ARIOTTI")
                .append("description", "Svolgere gli esercizi")
                .append("completed", true)
                .append("completedAt", Date.from(Instant.parse("2026-11-13T09:00:00Z")));
        Document legacyHomeworkImport = new Document("_id", "legacy-import-1")
                .append("sourceCode", "ABCDEF123456")
                .append("originalFilename", "text-ABCDEF123456.txt")
                .append("sha256", "a".repeat(64))
                .append("importedAt", Date.from(Instant.parse("2026-11-12T10:00:00Z")))
                .append("assignmentCount", 1)
                .append("schoolYearStart", 2026)
                .append("assignments", List.of(legacyAssignment));

        Document legacyDiaryEntry = new Document("id", "legacy-diary-1")
                .append("activityDate", mongoDate(LocalDate.of(2026, 11, 13)))
                .append("activityDateRaw", "13 NOVEMBRE")
                .append("period", 1)
                .append("periodLabel", "1° ORA")
                .append("subject", "INGLESE")
                .append("teacher", "E. NIFOSI")
                .append("description", "Virtual classmates");
        Document legacyDiaryImport = new Document("_id", "legacy-diary-import-1")
                .append("sourceCode", "FEDCBA654321")
                .append("originalFilename", "text-FEDCBA654321.txt")
                .append("sha256", "b".repeat(64))
                .append("importedAt", Date.from(Instant.parse("2026-11-13T10:00:00Z")))
                .append("entryCount", 1)
                .append("schoolYearStart", 2026)
                .append("entries", List.of(legacyDiaryEntry));

        mongoTemplate.insert(legacyHomeworkImport, "homeworkImports");
        mongoTemplate.insert(legacyDiaryImport, "diaryImports");

        legacyDataMigrator.run(null);

        HomeworkAssignment migrated = assignmentRepository.findById("legacy-assignment-1").orElseThrow();
        assertThat(migrated.getSubject()).isEqualTo("MATEMATICA");
        assertThat(migrated.isCompleted()).isTrue();
        assertThat(migrated.getCompletedAt()).isEqualTo(Instant.parse("2026-11-13T09:00:00Z"));
        assertThat(migrated.getRevision()).isEqualTo(1);
        assertThat(migrated.getFirstImportId()).isEqualTo("legacy-import-1");

        DiaryEntry migratedDiary = diaryEntryRepository.findById("legacy-diary-1").orElseThrow();
        assertThat(migratedDiary.getSubject()).isEqualTo("INGLESE");
        assertThat(migratedDiary.getRevision()).isEqualTo(1);

        Document migratedImport = mongoTemplate.getCollection("homeworkImports")
                .find(new Document("_id", "legacy-import-1")).first();
        assertThat(migratedImport).isNotNull();
        assertThat(migratedImport.get("assignments")).isNull();
        assertThat(migratedImport.getBoolean("assignmentsMigrated")).isTrue();
        assertThat(migratedImport.get("createdCount")).isEqualTo(1);

        Document migratedDiaryImport = mongoTemplate.getCollection("diaryImports")
                .find(new Document("_id", "legacy-diary-import-1")).first();
        assertThat(migratedDiaryImport).isNotNull();
        assertThat(migratedDiaryImport.get("entries")).isNull();
        assertThat(migratedDiaryImport.getBoolean("entriesMigrated")).isTrue();

        // Una seconda esecuzione non deve creare copie né perdere lo stato.
        legacyDataMigrator.run(null);
        assertThat(assignmentRepository.count()).isEqualTo(1);
        assertThat(diaryEntryRepository.count()).isEqualTo(1);
        assertThat(assignmentRepository.findById("legacy-assignment-1").orElseThrow().isCompleted()).isTrue();
    }

    @Test
    void doesNotDeleteMalformedFileAndDoesNotRetryUnchangedParsing() throws Exception {
        String code = nextCode();
        Path file = importsDirectory.resolve("text-" + code + ".txt");
        Files.writeString(file, "ASSEGNATI IL 11 NOVEMBRE\nTesto non valido\n", StandardCharsets.UTF_8);

        monitor.scan();
        monitor.scan();
        assertThat(file).exists();
        List<ImportIssue> issues = issueRepository.findAll();
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getType()).isEqualTo(ImportIssue.Type.PARSE_ERROR);
        int attempts = issues.getFirst().getAttempts();

        monitor.scan();
        assertThat(file).exists();
        assertThat(importRepository.findAll()).isEmpty();
        assertThat(issueRepository.findAll().getFirst().getAttempts()).isEqualTo(attempts);
    }

    @Test
    void retriesDeletionAfterItInitiallyFails() throws Exception {
        assumePosix(importsDirectory);
        String code = nextCode();
        Path file = importsDirectory.resolve("text-" + code + ".txt");
        Files.writeString(file, validContent("Compito con cancellazione difficile"), StandardCharsets.UTF_8);
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(importsDirectory);
        try {
            Files.setPosixFilePermissions(importsDirectory, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE));

            monitor.scan();
            monitor.scan();
            assertThat(importRepository.findBySourceCode(code)).isPresent();
            assertThat(file).exists();
            assertThat(issueRepository.findAll()).anySatisfy(issue ->
                    assertThat(issue.getType()).isEqualTo(ImportIssue.Type.FILE_DELETION_FAILED));
        } finally {
            Files.setPosixFilePermissions(importsDirectory, original);
        }

        monitor.scan();
        assertThat(file).doesNotExist();
        assertThat(importRepository.findAll()).hasSize(1);
        assertThat(issueRepository.findAll())
                .filteredOn(issue -> issue.getType() == ImportIssue.Type.FILE_DELETION_FAILED)
                .allSatisfy(issue -> assertThat(issue.getStatus()).isEqualTo(ImportIssue.Status.RESOLVED));
    }

    @Test
    void ignoresUnrelatedFilesSymlinksAndOversizedFiles() throws Exception {
        Path unrelated = importsDirectory.resolve("notes.txt");
        Files.writeString(unrelated, "Non è MIDA", StandardCharsets.UTF_8);
        String symlinkCode = nextCode();
        Path outside = Files.createTempFile("mida-outside-", ".txt");
        Files.writeString(outside, validContent("Fuori"), StandardCharsets.UTF_8);
        Path symlink = importsDirectory.resolve("text-" + symlinkCode + ".txt");
        Files.createSymbolicLink(symlink, outside);
        Path oversized = importsDirectory.resolve("text-" + nextCode() + ".txt");
        Files.writeString(oversized, "x".repeat(5000), StandardCharsets.UTF_8);

        monitor.scan();
        monitor.scan();

        assertThat(importRepository.findAll()).isEmpty();
        assertThat(assignmentRepository.findAll()).isEmpty();
        assertThat(unrelated).exists();
        assertThat(symlink).exists();
        assertThat(outside).exists();
        assertThat(oversized).exists();
        assertThat(issueRepository.findAll())
                .anySatisfy(issue -> assertThat(issue.getType()).isEqualTo(ImportIssue.Type.FILE_TOO_LARGE));

        Files.deleteIfExists(symlink);
        Files.deleteIfExists(outside);
    }

    @Test
    void openApiAndActuatorAreAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("MIDA API"))
                .andExpect(jsonPath("$.paths['/api/homework']").exists());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void returnsAProblemDetailForUnsupportedHttpMethods() throws Exception {
        mockMvc.perform(get("/api/notifications/subscriptions"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.title").value("Metodo non consentito"));
    }

    @Test
    void importsDidupContentFromMemoryAndDeduplicatesIt() {
        byte[] content = homeworkContent(
                "11 NOVEMBRE", "MATEMATICA", "C. ARIOTTI",
                "Svolgere gli esercizi dal file in memoria", "MERCOLEDÌ 18 NOVEMBRE")
                .getBytes(StandardCharsets.UTF_8);
        DidupFileName name = DidupFileName.parse("text-A1B2C3D4E5F6.txt").orElseThrow();

        ImportService.ImportOutcome first = importService.importBytes(content, name);
        assertThat(first.status()).isEqualTo(ImportService.ImportOutcome.Status.IMPORTED);
        assertThat(first.message()).contains("1 nuovo compito");
        assertThat(importRepository.findBySourceCode("A1B2C3D4E5F6")).isPresent();
        assertThat(assignmentRepository.count()).isEqualTo(1);
        assertThat(assignmentRepository.findAll().getFirst().getDescription())
                .isEqualTo("Svolgere gli esercizi dal file in memoria");

        ImportService.ImportOutcome second = importService.importBytes(content, name);
        assertThat(second.status()).isEqualTo(ImportService.ImportOutcome.Status.ALREADY_IMPORTED);
        assertThat(assignmentRepository.count()).isEqualTo(1);
        assertThat(importRepository.count()).isEqualTo(1);
    }

    @Test
    void storesEmailAttachmentInGridFsAndServesItForDownload() throws Exception {
        EmailMessage email = new EmailMessage();
        email.setId("email-1");
        email.setProviderMessageId("gmail-1");
        email.setThreadId("thread-1");
        email.setFrom("DidUP Family <didup@example.com>");
        email.setTo("studente@example.com");
        email.setSubject("Compiti di matematica");
        email.setSnippet("Svolgere gli esercizi");
        email.setBodyText("Svolgere gli esercizi pagina 111.");
        email.setReceivedAt(Instant.parse("2026-11-13T10:00:00Z"));
        email.setLabels(List.of("INBOX"));
        email.setHasAttachments(true);
        email.setAttachmentCount(1);
        email.setAttachmentsComplete(true);
        email.setFirstSeenAt(Instant.parse("2026-11-13T10:01:00Z"));
        email.setLastSeenAt(Instant.parse("2026-11-13T10:01:00Z"));
        emailMessageRepository.save(email);

        byte[] bytes = "contenuto allegato di prova".getBytes(StandardCharsets.UTF_8);
        AttachmentStorageService.StoredFile stored = googleFileService.store(
                bytes, "compiti.txt", "text/plain", Map.of("source", "TEST"));

        EmailAttachment attachment = new EmailAttachment();
        attachment.setId("attachment-1");
        attachment.setEmailMessageId("email-1");
        attachment.setProviderAttachmentId("provider-1");
        attachment.setFilename("compiti.txt");
        attachment.setMimeType("text/plain");
        attachment.setSize(bytes.length);
        attachment.setSha256(stored.sha256());
        attachment.setGridFsId(stored.id());
        attachment.setImportStatus(EmailAttachment.IMPORT_STORED);
        attachment.setImportMessage("Allegato disponibile per il download");
        attachment.setCreatedAt(Instant.parse("2026-11-13T10:01:00Z"));
        emailAttachmentRepository.save(attachment);

        mockMvc.perform(get("/api/mail/emails"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("Compiti di matematica"))
                .andExpect(jsonPath("$.content[0].attachmentCount").value(1));

        mockMvc.perform(get("/api/mail/emails/email-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyText").value("Svolgere gli esercizi pagina 111."))
                .andExpect(jsonPath("$.attachments[0].filename").value("compiti.txt"))
                .andExpect(jsonPath("$.attachments[0].downloadUrl")
                        .value("/api/attachments/" + stored.id()));

        var download = mockMvc.perform(get("/api/attachments/{id}", stored.id()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getContentType()).contains("text/plain");
        assertThat(download.getResponse().getHeader("Content-Disposition")).contains("compiti.txt");
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(bytes);

        mockMvc.perform(get("/api/mail/emails/email-unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsClassroomItemsCoursesAndMaterials() throws Exception {
        ClassroomItem item = new ClassroomItem();
        item.setId("classroom-1");
        item.setClassroomKey("course-1:COURSEWORK:work-1");
        item.setCourseId("course-1");
        item.setCourseName("Matematica");
        item.setCourseSection("3A");
        item.setItemType("COURSEWORK");
        item.setTitle("Esercizi sulle frazioni");
        item.setDescription("Completare gli esercizi dal libro.");
        item.setState("PUBLISHED");
        item.setAlternateLink("https://classroom.google.com/c/course-1/a/work-1");
        item.setDueDate(LocalDate.of(2026, 11, 20));
        item.setDueTime("09:00");
        item.setMaterialCount(1);
        item.setFirstSeenAt(Instant.parse("2026-11-13T10:00:00Z"));
        item.setLastSeenAt(Instant.parse("2026-11-13T10:00:00Z"));
        item.setUpdatedAt(Instant.parse("2026-11-13T10:00:00Z"));
        classroomItemRepository.save(item);

        ClassroomMaterial material = new ClassroomMaterial();
        material.setId("material-1");
        material.setItemId("classroom-1");
        material.setType("LINK");
        material.setExternalKey("https://example.com/esercizi");
        material.setTitle("Foglio esercizi");
        material.setUrl("https://example.com/esercizi");
        material.setStatus(ClassroomMaterial.STATUS_LINK_ONLY);
        material.setCreatedAt(Instant.parse("2026-11-13T10:00:00Z"));
        classroomMaterialRepository.save(material);

        mockMvc.perform(get("/api/google/classroom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Esercizi sulle frazioni"))
                .andExpect(jsonPath("$.content[0].itemType").value("COURSEWORK"));

        mockMvc.perform(get("/api/google/classroom/classroom-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materials[0].title").value("Foglio esercizi"))
                .andExpect(jsonPath("$.materials[0].status").value("LINK_ONLY"));

        mockMvc.perform(get("/api/google/classroom/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseId").value("course-1"))
                .andExpect(jsonPath("$[0].courseName").value("Matematica"));
    }

    @Test
    void reportsGoogleAsNotConfiguredAndRedirectsToSettings() throws Exception {
        mockMvc.perform(get("/api/google/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.connected").value(false));

        var redirect = mockMvc.perform(get("/api/google/connect"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(redirect.getResponse().getRedirectedUrl())
                .contains("/impostazioni?google=not-configured");
    }

    @Test
    void exposesGmailStatusWhenNotConnected() throws Exception {
        mockMvc.perform(get("/api/google/gmail/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.lastSyncStatus").value("NEVER"));
    }

    private HomeworkImport seedHomework(
            String sourceCode, List<DidupFileParser.ParsedAssignment> assignments) {
        HomeworkImport imported = new HomeworkImport();
        imported.setId(UUID.randomUUID().toString());
        imported.setSourceCode(sourceCode);
        imported.setOriginalFilename("text-" + sourceCode + "-1.txt");
        imported.setFilenameSuffix(1);
        imported.setSha256(sourceCode.toLowerCase().repeat(6).substring(0, 64));
        imported.setImportedAt(Instant.parse("2026-11-12T10:00:00Z"));
        imported.setAssignmentCount(assignments.size());
        imported.setSchoolYearStart(2026);

        ImportReference reference = new ImportReference(
                imported.getId(), sourceCode, imported.getOriginalFilename(), imported.getImportedAt());
        HomeworkAssignmentWriter.ImportResult result =
                assignmentWriter.importAssignments(assignments, reference);
        imported.setCreatedCount(result.created());
        imported.setUpdatedCount(result.updated());
        imported.setUnchangedCount(result.unchanged());
        return importRepository.save(imported);
    }

    private DidupFileParser.ParsedAssignment parsed(
            String subject,
            String teacher,
            LocalDate assignedDate,
            LocalDate dueDate,
            String description) {
        return new DidupFileParser.ParsedAssignment(
                assignedDate,
                "11 NOVEMBRE",
                dueDate,
                "MERCOLEDÌ 18 NOVEMBRE",
                subject,
                teacher,
                description);
    }

    private HomeworkAssignment byDescription(String description) {
        return assignmentRepository.findAll().stream()
                .filter(assignment -> assignment.getDescription().equals(description))
                .findFirst()
                .orElseThrow();
    }

    private String validContent(String description) {
        return homeworkContent("11 NOVEMBRE", "MATEMATICA", "C. ARIOTTI",
                description, "MERCOLEDÌ 18 NOVEMBRE");
    }

    private String homeworkContent(
            String assignedRaw, String subject, String teacher, String description, String dueRaw) {
        return """
                ASSEGNATI IL %s

                %s (%s):
                %s
                --- CONSEGNA %s ---
                """.formatted(assignedRaw, subject, teacher, description, dueRaw);
    }

    private String diaryContent() {
        try (var stream = getClass().getResourceAsStream("/fixtures/didup/text-D1A2B3C4D5E6-1.txt")) {
            if (stream == null) {
                throw new IllegalStateException("Fixture diario non trovata");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile leggere la fixture diario", ex);
        }
    }

    private Date mongoDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.of("Europe/Rome")).toInstant());
    }

    private String nextCode() {
        return String.format("%012X", CODE_SEQUENCE.incrementAndGet());
    }

    private void assumePosix(Path path) throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.getFileStore(path).supportsFileAttributeView("posix"),
                "POSIX permissions not supported");
    }
}
