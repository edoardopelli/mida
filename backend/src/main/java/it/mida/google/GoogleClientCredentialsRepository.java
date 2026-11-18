package it.mida.google;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GoogleClientCredentialsRepository
        extends MongoRepository<GoogleClientCredentials, String> {
}
