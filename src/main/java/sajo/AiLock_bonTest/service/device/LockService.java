package sajo.AiLock_bonTest.service.device;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sajo.AiLock_bonTest.domain.entity.App;
import sajo.AiLock_bonTest.domain.entity.ChatSession;
import sajo.AiLock_bonTest.domain.entity.Device;
import sajo.AiLock_bonTest.domain.enums.SessionStatus;
import sajo.AiLock_bonTest.dto.device.LockRequest;
import sajo.AiLock_bonTest.dto.device.LockResponse;
import sajo.AiLock_bonTest.global.exception.DeviceNotFoundException;
import sajo.AiLock_bonTest.repository.AppRepository;
import sajo.AiLock_bonTest.repository.ChatSessionRepository;
import sajo.AiLock_bonTest.repository.DeviceRepository;

import java.util.*;
@Service
@Slf4j
@RequiredArgsConstructor
public class LockService {

    private final AppRepository appRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public LockResponse lock(LockRequest request) {

        Device device = deviceRepository.findByIdForUpdate(request.deviceId())
                .orElseThrow(()-> new DeviceNotFoundException("등록되지 않은 기기: " + request.deviceId()));
        App app = appRepository.findByDeviceIdAndPackageName(request.deviceId(), request.packageName())
                .orElseGet(() -> appRepository.save(
                        App.create(request.deviceId(), request.packageName(), request.appName())));

        Optional<ChatSession> existing = chatSessionRepository
                .findByDeviceIdAndAppIdAndStatus(request.deviceId(), app.getId(), SessionStatus.ACTIVE);

        if (existing.isPresent()) return new LockResponse(true);

        ChatSession session = ChatSession.open(
                request.deviceId(),
                app.getId(),
                request.lockedAt(),
                request.lockedAt().plusSeconds(request.lockSecond())
        );

        device.increaseSessionCount();

        chatSessionRepository.save(session);

        return new LockResponse(true);
    }
    }

