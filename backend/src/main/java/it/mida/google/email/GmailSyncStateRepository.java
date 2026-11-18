package it.mida.google.email;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GmailSyncStateRepository extends MongoRepository<GmailSyncState, String> {
}
