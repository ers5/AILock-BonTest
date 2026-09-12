package sajo.AiLock_bonTest.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sajo.AiLock_bonTest.dto.admin.AdminDataResponse;
import sajo.AiLock_bonTest.service.admin.AdminReadService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminReadController {

    private final AdminReadService adminReadService;

    @GetMapping("/data")
    public AdminDataResponse data() {
        return adminReadService.data();
    }
}