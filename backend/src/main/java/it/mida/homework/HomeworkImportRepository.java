package it.mida.homework;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HomeworkImportRepository extends MongoRepository<HomeworkImport, String> {

    Optional<HomeworkImport> findBySourceCode(String sourceCode);

    Optional<HomeworkImport> findFirstBySha256(String sha256);

    boolean existsBySourceCode(String sourceCode);

    Optional<HomeworkImport> findFirstByOrderByImportedAtDesc();

    List<HomeworkImport> findAllByOrderByImportedAtDesc();
}
