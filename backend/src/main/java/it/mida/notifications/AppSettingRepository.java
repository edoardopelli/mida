package it.mida.notifications;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AppSettingRepository extends MongoRepository<AppSetting, String> {
}
