package it.mida.google;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GoogleSettingsRepository extends MongoRepository<GoogleSettings, String> {
}
