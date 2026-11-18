package it.mida.google.classroom;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClassroomItemRepository extends MongoRepository<ClassroomItem, String> {

    Optional<ClassroomItem> findByClassroomKey(String classroomKey);

    List<ClassroomItem> findByCourseIdOrderByUpdateTimeDesc(String courseId);
}
