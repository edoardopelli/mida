package it.mida.google;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GoogleConnectionRepository extends MongoRepository<GoogleConnection, String> {
}
