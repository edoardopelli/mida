package it.mida.homework;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HomeworkAssignmentRepository extends MongoRepository<HomeworkAssignment, String> {

    Optional<HomeworkAssignment> findByContentHash(String contentHash);

    List<HomeworkAssignment> findBySlotKey(String slotKey);

    List<HomeworkAssignment> findByBaseKey(String baseKey);

    List<HomeworkAssignment> findByFirstImportId(String firstImportId);

    long countByCompletedTrue();
}
