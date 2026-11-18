package it.mida.homework;

import it.mida.common.MidaNotFoundException;
import it.mida.events.MidaEventPublisher;
import it.mida.events.MidaEvents;
import it.mida.homework.api.HomeworkView;
import java.time.Instant;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class HomeworkCommandService {

    private final MongoTemplate mongoTemplate;
    private final HomeworkQueryService queryService;
    private final MidaEventPublisher events;

    public HomeworkCommandService(
            MongoTemplate mongoTemplate, HomeworkQueryService queryService, MidaEventPublisher events) {
        this.mongoTemplate = mongoTemplate;
        this.queryService = queryService;
        this.events = events;
    }

    /**
     * Aggiorna il completamento di un singolo compito. Ogni compito è un
     * documento autonomo, quindi l'update per {@code _id} non può toccare gli
     * altri compiti.
     */
    public HomeworkView setCompleted(String assignmentId, boolean completed) {
        Update update = new Update().set("completed", completed);
        if (completed) {
            update.set("completedAt", Instant.now());
        } else {
            update.unset("completedAt");
        }

        var result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(assignmentId)),
                update,
                HomeworkAssignment.class);
        if (result.getMatchedCount() == 0) {
            throw new MidaNotFoundException("Compito non trovato: " + assignmentId);
        }

        HomeworkView view = queryService.findById(assignmentId)
                .orElseThrow(() -> new MidaNotFoundException("Compito non trovato: " + assignmentId));
        events.publish(MidaEvents.HOMEWORK_UPDATED, MidaEvents.homeworkUpdated(view));
        return view;
    }
}
