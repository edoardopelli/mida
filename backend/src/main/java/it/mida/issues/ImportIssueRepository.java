package it.mida.issues;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ImportIssueRepository extends MongoRepository<ImportIssue, String> {

    Optional<ImportIssue> findByFingerprint(String fingerprint);

    boolean existsByFilenameAndSha256AndStatusAndTypeIn(
            String filename, String sha256, ImportIssue.Status status, Collection<ImportIssue.Type> types);

    Page<ImportIssue> findByStatusOrderByLastSeenAtDesc(ImportIssue.Status status, Pageable pageable);

    List<ImportIssue> findByFilenameAndTypeAndStatus(
            String filename, ImportIssue.Type type, ImportIssue.Status status);

    List<ImportIssue> findByFilenameAndStatus(String filename, ImportIssue.Status status);

    long countByStatus(ImportIssue.Status status);
}
