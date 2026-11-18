package it.mida.notifications;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationDeliveryRepository extends MongoRepository<NotificationDelivery, String> {

    boolean existsByDedupKey(String dedupKey);

    Optional<NotificationDelivery> findByDedupKey(String dedupKey);
}
