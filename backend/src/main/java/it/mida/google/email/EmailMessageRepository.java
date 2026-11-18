package it.mida.google.email;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmailMessageRepository extends MongoRepository<EmailMessage, String> {

    Optional<EmailMessage> findByProviderMessageId(String providerMessageId);

    boolean existsByProviderMessageId(String providerMessageId);
}
