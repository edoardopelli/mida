package it.mida.homework;

import it.mida.common.PageResponse;
import it.mida.homework.api.HomeworkQuery;
import it.mida.homework.api.HomeworkView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Read model dei compiti. Ogni compito è un documento autonomo: le query non
 * devono più fare unwind degli array incorporati nelle importazioni.
 */
@Service
public class HomeworkQueryService {

    private static final String COLLECTION = "homeworkAssignments";

    private final MongoTemplate mongoTemplate;

    public HomeworkQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public PageResponse<HomeworkView> search(HomeworkQuery query) {
        Query mongoQuery = new Query(criteria(query));
        long total = mongoTemplate.count(mongoQuery, HomeworkAssignment.class);
        mongoQuery.with(sort());
        mongoQuery.skip((long) query.page() * query.size());
        mongoQuery.limit(query.size());
        List<HomeworkView> content = mongoTemplate.find(mongoQuery, HomeworkAssignment.class).stream()
                .map(this::toView)
                .toList();
        return PageResponse.of(content, query.page(), query.size(), total);
    }

    /**
     * Il calendario non è paginato: l'intervallo richiesto viene restituito per
     * intero, così FullCalendar può mostrare tutti i compiti.
     */
    public List<HomeworkView> calendar(HomeworkQuery query) {
        Query mongoQuery = new Query(criteria(query));
        mongoQuery.with(sort());
        return mongoTemplate.find(mongoQuery, HomeworkAssignment.class).stream()
                .map(this::toView)
                .toList();
    }

    public Optional<HomeworkView> findById(String assignmentId) {
        return Optional.ofNullable(mongoTemplate.findById(assignmentId, HomeworkAssignment.class))
                .map(this::toView);
    }

    public List<String> subjects() {
        return distinctValues("subject");
    }

    public List<String> teachers() {
        return distinctValues("teacher");
    }

    public long completedCount() {
        return mongoTemplate.count(Query.query(Criteria.where("completed").is(true)), HomeworkAssignment.class);
    }

    public long countDueBetween(LocalDate from, LocalDate to, Boolean completed) {
        HomeworkQuery query = new HomeworkQuery(
                from, to, HomeworkQuery.DateField.DUE, null, null, completed, null, 0, 1);
        return mongoTemplate.count(new Query(criteria(query)), HomeworkAssignment.class);
    }

    private List<String> distinctValues(String field) {
        List<String> values = new ArrayList<>();
        mongoTemplate.getCollection(COLLECTION).distinct(field, String.class).forEach(values::add);
        return values.stream()
                .filter(StringUtils::hasText)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private Sort sort() {
        return Sort.by(
                Sort.Order.asc("dueDate"),
                Sort.Order.asc("subject"),
                Sort.Order.asc("teacher"),
                Sort.Order.asc("id"));
    }

    private Criteria criteria(HomeworkQuery query) {
        List<Criteria> criteria = new ArrayList<>();
        String datePath = query.datePath();
        if (query.from() != null) {
            criteria.add(Criteria.where(datePath).gte(query.from()));
        }
        if (query.to() != null) {
            criteria.add(Criteria.where(datePath).lte(query.to()));
        }
        if (StringUtils.hasText(query.subject())) {
            criteria.add(Criteria.where("subject").is(query.subject().strip()));
        }
        if (StringUtils.hasText(query.teacher())) {
            criteria.add(Criteria.where("teacher").is(query.teacher().strip()));
        }
        if (query.completed() != null) {
            criteria.add(Criteria.where("completed").is(query.completed()));
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

    private HomeworkView toView(HomeworkAssignment assignment) {
        return new HomeworkView(
                assignment.getId(),
                assignment.getImportId(),
                assignment.getSourceCode(),
                assignment.getOriginalFilename(),
                assignment.getImportedAt(),
                assignment.getAssignedDate(),
                assignment.getAssignedDateRaw(),
                assignment.getDueDate(),
                assignment.getDueDateRaw(),
                assignment.getSubject(),
                assignment.getTeacher(),
                assignment.getDescription(),
                assignment.isCompleted(),
                assignment.getCompletedAt(),
                assignment.getRevision(),
                assignment.getUpdatedAt());
    }
}
