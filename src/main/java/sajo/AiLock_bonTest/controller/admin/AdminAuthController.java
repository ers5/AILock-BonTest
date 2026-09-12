package sajo.AiLock_bonTest.controller.admin;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import sajo.AiLock_bonTest.service.admin.AdminAuthService;

@Controller
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @GetMapping("/admin/login")
    public String loginPage(HttpServletRequest request) {
        if (adminAuthService.isAuthenticated(request)) {
            return "redirect:/admin/";
        }

        return "forward:/admin/login.html";
    }

    @PostMapping("/admin/login")
    public String login(
            @RequestParam String password,
            HttpServletRequest request
    ) {
        if (!adminAuthService.login(request, password)) {
            return "redirect:/admin/login?error=1";
        }

        return "redirect:/admin/";
    }

    @PostMapping("/admin/logout")
    public String logout(HttpServletRequest request) {
        adminAuthService.logout(request);
        return "redirect:/admin/login";
    }
}