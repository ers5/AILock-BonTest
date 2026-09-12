package sajo.AiLock_bonTest.service.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AdminAuthService {

    private static final String AUTHENTICATED =
            AdminAuthService.class.getName() + ".AUTHENTICATED";

    private final byte[] adminPassword;

    public AdminAuthService(
            @Value("${admin.auth.password}") String adminPassword
    ) {
        this.adminPassword =
                adminPassword.getBytes(StandardCharsets.UTF_8);
    }

    public boolean login(
            HttpServletRequest request,
            String password
    ) {
        if (password == null) return false;

        boolean matched = MessageDigest.isEqual(
                adminPassword,
                password.getBytes(StandardCharsets.UTF_8)
        );

        if (!matched) return false;

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(AUTHENTICATED, true);

        return true;
    }

    public boolean isAuthenticated(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        return session != null
                && Boolean.TRUE.equals(
                        session.getAttribute(AUTHENTICATED)
                );
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}