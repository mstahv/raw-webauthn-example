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
import org.vaadin.firitin.components.textfield.VTextField;

@Route("login")
public class LoginAndRegistrationView extends VerticalLayout {

    public LoginAndRegistrationView(
            WebAuthnService webAuthnService,
            WebAuthnSession webAuthnSession,
            UserListing userListing) {
        setAlignItems(Alignment.CENTER);
        getStyle().setTextAlign(Style.TextAlign.CENTER);

        add(new H1("WebAuthn }> in Java"));
        add(new Paragraph("""
        This is a demo/example application implementing WebAuthn/Passkey based
        authentication and authorization from atoms, with Vaadin and 
        java-webauthn-server(https://github.com/Yubico/java-webauthn-server) 
        for the server-side implementation. The purpose of the example is explain how 
        WebAuthn/Passkeys work in modern browsers and how to easily consume 
        Promise based JavaScript browser APIs with Vaadin (utilizes JsPromise from
        Viritin add-on for simplified asynchronous browsers API access).
        """));
        add(new Paragraph("""
        For real business apps, it would be most often a better solution to
        utilize WebAuthn/Passkeys "indirectly", by for example dropping in
        KeyCloak and/or Spring Security. Less security related code in your
        app, the better.
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
                new Paragraph("Choose a username of your choice below. Clicking register will then ignite a process using WebAuthn API to generate a passkey for this service."),
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
