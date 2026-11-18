package it.mida.diary;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DiaryImportRepository extends MongoRepository<DiaryImport, String> {

    Optional<DiaryImport> findBySourceCode(String sourceCode);

    Optional<DiaryImport> findFirstBySha256(String sha256);

    Optional<DiaryImport> findFirstByOrderByImportedAtDesc();
}
