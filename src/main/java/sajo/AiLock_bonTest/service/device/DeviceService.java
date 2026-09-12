package sajo.AiLock_bonTest.service.device;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sajo.AiLock_bonTest.domain.entity.Device;
import sajo.AiLock_bonTest.dto.device.DeviceRegisterRequest;
import sajo.AiLock_bonTest.repository.DeviceRepository;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public Device register(DeviceRegisterRequest request) {
        return deviceRepository.save(Device.create(request));
    }
}
