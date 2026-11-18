package it.mida.diary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DiaryEntryRepository extends MongoRepository<DiaryEntry, String> {

    Optional<DiaryEntry> findByActivityDateAndPeriod(LocalDate activityDate, int period);

    List<DiaryEntry> findByActivityDateOrderByPeriodAsc(LocalDate activityDate);
}
