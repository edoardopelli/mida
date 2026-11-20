package it.mida.google.classroom.api;

import it.mida.cluster.ConditionalOnMidaRole;
import it.mida.cluster.MidaRole;
import it.mida.common.PageResponse;
import it.mida.google.classroom.ClassroomQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/google/classroom")
@Tag(name = "Classroom", description = "Compiti, materiali e annunci di Google Classroom")
@ConditionalOnMidaRole({MidaRole.STANDALONE, MidaRole.MASTER})
public class ClassroomController {

    private final ClassroomQueryService queryService;

    public ClassroomController(ClassroomQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Elenca gli elementi Classroom sincronizzati")
    public PageResponse<ClassroomItemSummaryDto> list(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queryService.search(new ClassroomQuery(courseId, type, q, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Restituisce un elemento Classroom con i materiali")
    public ClassroomItemView detail(@PathVariable String id) {
        return queryService.findById(id);
    }

    @GetMapping("/courses")
    @Operation(summary = "Elenca i corsi Classroom presenti")
    public List<ClassroomCourseDto> courses() {
        return queryService.courses();
    }
}
