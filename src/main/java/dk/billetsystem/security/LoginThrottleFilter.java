package dk.billetsystem.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stopper login-forsøg, mens brugernavnet eller IP-adressen er låst (se LoginAttemptService).
 * Forsøget afvises, før kodeordet overhovedet bliver tjekket, så man ikke kan blive ved med at gætte.
 *
 * Filteret er med vilje ikke en @Component: så ville Spring Boot også køre det på alle andre forespørgsler.
 * Det sættes ind i SecurityConfig lige før Springs eget login-filter.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private final LoginAttemptService attempts;

    public LoginThrottleFilter(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/admin/login".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (attempts.isBlocked(request.getParameter("username"), request.getRemoteAddr())) {
            response.sendRedirect(request.getContextPath() + "/admin/login.html?locked");
            return;
        }
        chain.doFilter(request, response);
    }
}
