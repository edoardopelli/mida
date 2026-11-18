package it.mida.google;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GoogleOAuthStateRepository extends MongoRepository<GoogleOAuthState, String> {
}
