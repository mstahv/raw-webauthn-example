package org.example.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Route;
import org.example.WebAuthnService;
import org.example.WebAuthnSession;
import org.vaadin.firitin.components.RichText;
import org.vaadin.firitin.components.textfield.VTextField;

@Route("login")
public class LoginAndRegistrationView extends VerticalLayout {

    public LoginAndRegistrationView(
            WebAuthnService webAuthnService,
            WebAuthnSession webAuthnSession,
            UserListing userListing) {
        setAlignItems(Alignment.CENTER);
        getStyle().setTextAlign(Style.TextAlign.CENTER);

        add(new RichText().withMarkDown("""
        # WebAuthn }> in Java
            
        This is a [demo/example application (check the source code!)](https://github.com/mstahv/raw-webauthn-example) 
        implementing [WebAuthn/Passkey](https://developer.mozilla.org/en-US/docs/Web/API/Web_Authentication_API) 
        based authentication and authorization from atoms, with Vaadin and 
        [java-webauthn-server](https://github.com/Yubico/java-webauthn-server) 
        for the server-side implementation. 
                
        This demo app is for learning and understanding the concepts. For real 
        business apps, it would be most often a better solution to
        utilize WebAuthn/Passkeys "indirectly", by for example dropping in
        [KeyCloak](https://www.keycloak.org) for identity management and utilizing
        it with Spring Security (in case of a Spring Boot app). Less own security 
        related code in your app, the better.
        
        *This demo is best consumed locally from your IDE: run, debug, modify, play & learn!* 
        
        [Check out the 
        source code via GitHub.](https://github.com/mstahv/raw-webauthn-example)
        """));

        Button login = new Button("Already registered? Login!", e -> {
            webAuthnSession.login().thenAccept(username -> {
                UI.getCurrent().navigate(MainView.class);
                Notification.show("Welcome back %s!".formatted(username));
            }).exceptionally(ex -> {
                Notification.show("Login failed: " + ex.getMessage());
                return null;
            });
        });

        TextField username = new VTextField("Username");
        Button register = new Button("Register!", e -> {
            webAuthnSession.registerUser(username.getValue()).thenAccept(v -> {
                Notification.show("Username %s succesfully registered. Welcome!"
                        .formatted(username.getValue()))
                        .setPosition(Notification.Position.MIDDLE);
                UI.getCurrent().navigate(MainView.class);
            }).exceptionally(ex -> {
                Notification.show("Failed to create user. " + ex.getMessage());
                return null;
            });
        });
        register.setEnabled(false);
        username.setManualValidation(true);
        username.addValueChangeListener(e -> {
            boolean isValid = !e.getValue().isEmpty() && e.getValue().matches("[a-zA-Z0-9]+");
            boolean userExists = webAuthnSession.userExists(username.getValue());
            if (isValid) {
                if (userExists)
                    username.setErrorMessage("User name already exists!");
                else username.setErrorMessage(null);
            } else {
                username.setErrorMessage("Only alphanumeric characters allowed!");
                username.setInvalid(true);
            }
            username.setInvalid(!isValid || userExists);
            register.setEnabled(isValid && !userExists);
        });

        add(
                new Hr(),
                new H3("Start here, register as new user with a passkey"),
                new Paragraph("Choose a username of your choice below. Clicking register will then ignite a process using WebAuthn API to generate a passkey for this service. Note, the app only stores passkeys in-memory, so don't expect your account created today will exist next week 🧸."),
                username,
                register,
                new Hr(),
                new H3("Already registered?"),
                new Paragraph("Clicking the button below will start a login process and you will be prompted for an existing passkey."),
                login,
                new Hr(),
                userListing);
    }
}
