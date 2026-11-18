package it.mida.notifications;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PushSubscriptionRepository extends MongoRepository<PushSubscription, String> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByActiveTrue();

    long countByActiveTrue();
}
