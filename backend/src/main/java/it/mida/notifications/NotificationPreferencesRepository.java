package it.mida.notifications;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationPreferencesRepository extends MongoRepository<NotificationPreferences, String> {
}
