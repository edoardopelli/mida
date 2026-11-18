package it.mida.google.email;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmailAttachmentRepository extends MongoRepository<EmailAttachment, String> {

    List<EmailAttachment> findByEmailMessageIdOrderByFilenameAsc(String emailMessageId);

    Optional<EmailAttachment> findByEmailMessageIdAndProviderAttachmentId(
            String emailMessageId, String providerAttachmentId);

    long countByEmailMessageId(String emailMessageId);
}
