package dk.billetsystem.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Lytter på Spring Securitys egne hændelser for login og tæller forkerte og vellykkede forsøg.
 * (Et ukendt brugernavn giver også "forkert kodeord", så de tælles på samme måde.)
 */
@Component
public class LoginAttemptListener {

    private final LoginAttemptService attempts;

    public LoginAttemptListener(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        attempts.loginFailed(event.getAuthentication().getName(), ip(event.getAuthentication()));
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        attempts.loginSucceeded(event.getAuthentication().getName(), ip(event.getAuthentication()));
    }

    private static String ip(Authentication authentication) {
        return authentication.getDetails() instanceof WebAuthenticationDetails details ? details.getRemoteAddress() : null;
    }
}
