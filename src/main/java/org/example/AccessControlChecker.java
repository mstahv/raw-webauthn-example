package org.example;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterListener;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.example.views.LoginAndRegistrationView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A trivial access control checker that navigates user to the
 * {@link LoginAndRegistrationView} if they are not logged in.
 *
 * In a real world Vaadin application one typically uses role based
 * checks or permission. Check for example {@link com.vaadin.flow.server.auth.AnnotatedViewAccessChecker}
 * and Spring Boot users should check Vaadin's built-in Spring
 * Security integration.
 * <p>
 *     TODO check if we really don't have as handy ways to listen
 *     BeforeEnterEvent's in Spring Boot as in CDI based apps.
 * </p>
 */
@Component
public class AccessControlChecker implements VaadinServiceInitListener, BeforeEnterListener {

    @Autowired
    WebAuthnSession session;

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource()
                .addUIInitListener(e -> e.getUI().addBeforeEnterListener(this));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent e) {
        // Allow only LoginAndRegistrationView if not logged in
        if (!session.isLoggedIn() &&
                e.getNavigationTarget() != LoginAndRegistrationView.class) {
            e.forwardTo(LoginAndRegistrationView.class);
        }
    }
}
