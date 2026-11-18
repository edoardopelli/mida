package it.mida.diary;

import it.mida.common.PageResponse;
import it.mida.diary.api.DiaryDateSummary;
import it.mida.diary.api.DiaryEntryView;
import it.mida.diary.api.DiaryQuery;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Read model del diario. Ogni ora è un documento autonomo nella collezione
 * {@code diaryEntries}: non servono più unwind degli array incorporati.
 */
@Service
public class DiaryQueryService {

    private static final String COLLECTION = "diaryEntries";
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Europe/Rome");

    private final MongoTemplate mongoTemplate;

    public DiaryQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public PageResponse<DiaryEntryView> search(DiaryQuery query) {
        Query mongoQuery = new Query(criteria(query));
        long total = mongoTemplate.count(mongoQuery, DiaryEntry.class);
        mongoQuery.with(Sort.by(
                Sort.Order.desc("activityDate"),
                Sort.Order.asc("period"),
                Sort.Order.asc("subject"),
                Sort.Order.asc("teacher"),
                Sort.Order.asc("id")));
        mongoQuery.skip((long) query.page() * query.size());
        mongoQuery.limit(query.size());
        List<DiaryEntryView> content = mongoTemplate.find(mongoQuery, DiaryEntry.class).stream()
                .map(this::toView)
                .toList();
        return PageResponse.of(content, query.page(), query.size(), total);
    }

    /**
     * Restituisce tutte le attività del giorno richiesto, senza paginazione.
     */
    public List<DiaryEntryView> day(DiaryQuery query) {
        Query mongoQuery = new Query(criteria(query));
        mongoQuery.with(Sort.by(
                Sort.Order.asc("period"),
                Sort.Order.asc("subject"),
                Sort.Order.asc("teacher"),
                Sort.Order.asc("id")));
        return mongoTemplate.find(mongoQuery, DiaryEntry.class).stream()
                .map(this::toView)
                .toList();
    }

    public Optional<DiaryEntryView> findById(String entryId) {
        return Optional.ofNullable(mongoTemplate.findById(entryId, DiaryEntry.class))
                .map(this::toView);
    }

    public List<DiaryDateSummary> dates() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("activityDate")
                        .count().as("entryCount")
                        .addToSet("subject").as("subjects"),
                Aggregation.sort(Sort.Direction.DESC, "_id"));
        List<Document> documents = mongoTemplate.aggregate(
                aggregation, COLLECTION, Document.class).getMappedResults();
        List<DiaryDateSummary> dates = new ArrayList<>();
        for (Document document : documents) {
            LocalDate date = toLocalDate(document.get("_id"));
            if (date == null) {
                continue;
            }
            Object countValue = document.get("entryCount");
            long count = countValue instanceof Number number ? number.longValue() : 0L;
            List<String> subjects = new ArrayList<>();
            Object subjectsValue = document.get("subjects");
            if (subjectsValue instanceof List<?> list) {
                list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(StringUtils::hasText)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(subjects::add);
            }
            dates.add(new DiaryDateSummary(date, count, List.copyOf(subjects)));
        }
        return dates;
    }

    public List<String> subjects() {
        return distinctValues("subject");
    }

    public List<String> teachers() {
        return distinctValues("teacher");
    }

    private List<String> distinctValues(String field) {
        List<String> values = new ArrayList<>();
        mongoTemplate.getCollection(COLLECTION).distinct(field, String.class).forEach(values::add);
        return values.stream()
                .filter(StringUtils::hasText)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private Criteria criteria(DiaryQuery query) {
        List<Criteria> criteria = new ArrayList<>();
        if (query.from() != null) {
            criteria.add(Criteria.where("activityDate").gte(query.from()));
        }
        if (query.to() != null) {
            criteria.add(Criteria.where("activityDate").lte(query.to()));
        }
        if (StringUtils.hasText(query.subject())) {
            criteria.add(Criteria.where("subject").is(query.subject().strip()));
        }
        if (StringUtils.hasText(query.teacher())) {
            criteria.add(Criteria.where("teacher").is(query.teacher().strip()));
        }
        if (StringUtils.hasText(query.q())) {
            Pattern pattern = Pattern.compile(
                    Pattern.quote(query.q().strip()),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            criteria.add(new Criteria().orOperator(
                    Criteria.where("description").regex(pattern),
                    Criteria.where("subject").regex(pattern),
                    Criteria.where("teacher").regex(pattern)));
        }
        return criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria);
    }

    private DiaryEntryView toView(DiaryEntry entry) {
        return new DiaryEntryView(
                entry.getId(),
                entry.getImportId(),
                entry.getSourceCode(),
                entry.getOriginalFilename(),
                entry.getImportedAt(),
                entry.getActivityDate(),
                entry.getActivityDateRaw(),
                entry.getPeriod(),
                entry.getPeriodLabel(),
                entry.getSubject(),
                entry.getTeacher(),
                entry.getDescription(),
                entry.getRevision(),
                entry.getUpdatedAt());
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toInstant().atZone(SCHOOL_ZONE).toLocalDate();
        }
        if (value instanceof String text) {
            try {
                return LocalDate.parse(text);
            } catch (RuntimeException ex) {
                return null;
            }
        }
        return null;
    }
}
