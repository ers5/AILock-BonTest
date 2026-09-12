package sajo.AiLock_bonTest.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import sajo.AiLock_bonTest.service.admin.AdminAuthService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class AdminAuthFilter extends OncePerRequestFilter {

    private final AdminAuthService adminAuthService;

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {
        String path = request.getRequestURI();

        return !path.equals("/admin")
                && !path.startsWith("/admin/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isPublicPath(path)
                || adminAuthService.isAuthenticated(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (acceptsJson(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    """
                    {"code":"ADMIN_AUTH_REQUIRED","message":"관리자 로그인이 필요합니다."}
                    """
            );
            return;
        }

        response.sendRedirect("/admin/login");
    }

    private boolean isPublicPath(String path) {
        return path.equals("/admin/login")
                || path.equals("/admin/login.html")
                || path.equals("/admin/login.js");
    }

    private boolean acceptsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");

        return accept != null
                && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }
}