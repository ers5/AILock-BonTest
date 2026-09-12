package sajo.AiLock_bonTest.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sajo.AiLock_bonTest.dto.device.DeviceRegisterRequest;

import java.util.UUID;

@Entity
@Table(name = "device")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "nick_name",nullable = false)
    private String nickName;

    @Column(name="total_session_count",nullable = false)
    private int totalSessionCount;

    private Device(UUID id,String nickName,int totalSessionCount) {
        this.id = id;
        this.nickName = nickName;
        this.totalSessionCount = totalSessionCount;
    }

    public static Device create(DeviceRegisterRequest request) {
        return new Device(UUID.randomUUID(), request.nickName(),0);
    }

    public void increaseSessionCount(){this.totalSessionCount++;}
}