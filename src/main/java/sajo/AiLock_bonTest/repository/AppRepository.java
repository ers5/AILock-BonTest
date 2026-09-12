package sajo.AiLock_bonTest.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sajo.AiLock_bonTest.domain.entity.App;

import java.util.Optional;
import java.util.UUID;

public interface AppRepository extends JpaRepository<App, Long> {

    Optional<App> findByDeviceIdAndPackageName(UUID deviceId, String packageName);
}
