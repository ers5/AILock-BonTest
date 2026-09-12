package sajo.AiLock_bonTest.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;


@Entity
@Table(
    name = "app",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_deviceId_packageName_unique",
        columnNames = {"device_id", "package_name"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class App {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, columnDefinition = "uuid")
    private UUID deviceId;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(name = "app_name")
    private String appName;

    private App(UUID deviceId, String packageName, String appName) {
        this.deviceId = deviceId;
        this.packageName = packageName;
        this.appName = appName;
    }

    public static App create(UUID deviceId, String packageName, String appName) {
        return new App(deviceId, packageName, appName);
    }
}
