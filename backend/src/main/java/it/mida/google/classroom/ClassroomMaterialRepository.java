package it.mida.google.classroom;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClassroomMaterialRepository extends MongoRepository<ClassroomMaterial, String> {

    List<ClassroomMaterial> findByItemIdOrderByTitleAsc(String itemId);

    Optional<ClassroomMaterial> findByItemIdAndExternalKey(String itemId, String externalKey);

    long countByItemId(String itemId);

    void deleteByItemIdAndType(String itemId, String type);
}
