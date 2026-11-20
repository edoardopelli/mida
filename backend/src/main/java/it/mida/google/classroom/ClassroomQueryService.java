package it.mida.google.classroom;

import it.mida.cluster.ConditionalOnMidaRole;
import it.mida.cluster.MidaRole;
import it.mida.common.MidaNotFoundException;
import it.mida.common.PageResponse;
import it.mida.google.classroom.api.ClassroomCourseDto;
import it.mida.google.classroom.api.ClassroomItemSummaryDto;
import it.mida.google.classroom.api.ClassroomItemView;
import it.mida.google.classroom.api.ClassroomMaterialDto;
import it.mida.google.classroom.api.ClassroomQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnMidaRole({MidaRole.STANDALONE, MidaRole.MASTER})
public class ClassroomQueryService {

    private final MongoTemplate mongoTemplate;
    private final ClassroomItemRepository itemRepository;
    private final ClassroomMaterialRepository materialRepository;

    public ClassroomQueryService(
            MongoTemplate mongoTemplate,
            ClassroomItemRepository itemRepository,
            ClassroomMaterialRepository materialRepository) {
        this.mongoTemplate = mongoTemplate;
        this.itemRepository = itemRepository;
        this.materialRepository = materialRepository;
    }

    public PageResponse<ClassroomItemSummaryDto> search(ClassroomQuery query) {
        Query mongoQuery = new Query(criteria(query));
        long total = mongoTemplate.count(mongoQuery, ClassroomItem.class);
        mongoQuery.with(Sort.by(
                Sort.Order.desc("updateTime"),
                Sort.Order.desc("dueDate"),
                Sort.Order.desc("id")));
        mongoQuery.skip((long) query.page() * query.size());
        mongoQuery.limit(query.size());
        List<ClassroomItemSummaryDto> content = mongoTemplate
                .find(mongoQuery, ClassroomItem.class).stream()
                .map(this::toSummary)
                .toList();
        return PageResponse.of(content, query.page(), query.size(), total);
    }

    public ClassroomItemView findById(String id) {
        ClassroomItem item = itemRepository.findById(id)
                .orElseThrow(() -> new MidaNotFoundException("Elemento Classroom non trovato"));
        List<ClassroomMaterialDto> materials = materialRepository
                .findByItemIdOrderByTitleAsc(id).stream()
                .map(this::toMaterial)
                .toList();
        return new ClassroomItemView(
                item.getId(),
                item.getCourseId(),
                item.getCourseName(),
                item.getCourseSection(),
                item.getItemType(),
                item.getTitle(),
                item.getDescription(),
                item.getState(),
                item.getAlternateLink(),
                item.getDueDate(),
                item.getDueTime(),
                item.getCreationTime(),
                item.getUpdateTime(),
                materials);
    }

    public List<ClassroomCourseDto> courses() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("courseId")
                        .first("courseName").as("courseName")
                        .first("courseSection").as("courseSection"),
                Aggregation.sort(Sort.Direction.ASC, "courseName"));
        List<Document> documents = mongoTemplate.aggregate(
                aggregation, "classroomItems", Document.class).getMappedResults();
        List<ClassroomCourseDto> courses = new ArrayList<>();
        for (Document document : documents) {
            String courseId = document.get("_id") == null ? null : document.get("_id").toString();
            if (!StringUtils.hasText(courseId)) {
                continue;
            }
            courses.add(new ClassroomCourseDto(
                    courseId,
                    document.getString("courseName"),
                    document.getString("courseSection")));
        }
        return courses;
    }

    private Criteria criteria(ClassroomQuery query) {
        List<Criteria> criteria = new ArrayList<>();
        if (StringUtils.hasText(query.courseId())) {
            criteria.add(Criteria.where("courseId").is(query.courseId().strip()));
        }
        if (StringUtils.hasText(query.type())) {
            criteria.add(Criteria.where("itemType").is(query.type().strip()));
        }
        if (StringUtils.hasText(query.q())) {
            Pattern pattern = Pattern.compile(
                    Pattern.quote(query.q().strip()),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            criteria.add(new Criteria().orOperator(
                    Criteria.where("title").regex(pattern),
                    Criteria.where("description").regex(pattern),
                    Criteria.where("courseName").regex(pattern)));
        }
        return criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria);
    }

    private ClassroomItemSummaryDto toSummary(ClassroomItem item) {
        return new ClassroomItemSummaryDto(
                item.getId(),
                item.getCourseId(),
                item.getCourseName(),
                item.getCourseSection(),
                item.getItemType(),
                item.getTitle(),
                item.getDescription(),
                item.getDueDate(),
                item.getDueTime(),
                item.getUpdateTime(),
                item.getMaterialCount(),
                item.getAlternateLink());
    }

    private ClassroomMaterialDto toMaterial(ClassroomMaterial material) {
        String downloadUrl = StringUtils.hasText(material.getGridFsId())
                ? "/api/attachments/" + material.getGridFsId()
                : null;
        return new ClassroomMaterialDto(
                material.getId(),
                material.getType(),
                material.getTitle(),
                material.getUrl(),
                material.getFilename(),
                material.getMimeType(),
                material.getSize(),
                material.getStatus(),
                material.getError(),
                downloadUrl);
    }
}
