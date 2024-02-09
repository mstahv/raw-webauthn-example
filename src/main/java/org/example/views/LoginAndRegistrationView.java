package org.example.views;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import org.example.UserSession;
import org.example.WebAuthnService;
import org.vaadin.firitin.util.JsPromise;

import java.util.List;

@Route("")
// Publish JS helpers to base64 encoded/decode binary data.
// There are good JS libraries to do this, but keeping deps to minimum
@JavaScript("context://webauthnhelpers.js")
public class LoginAndRegistrationView extends VerticalLayout {

    private final WebAuthnService webAuthnService;
    private final UserSession userSession;

    public LoginAndRegistrationView(WebAuthnService webAuthnService, UserSession userSession) {
        this.webAuthnService = webAuthnService;
        this.userSession = userSession;
        setAlignItems(Alignment.CENTER);
        init();
    }

    private void init() {
        removeAll();
        if(userSession.isLoggedIn()) {
            add(new H1("You are logged in as " + userSession.getUsername()));

            add(new Button("TODO: Try important action, will request re-authentication", e-> {
                // TODO, like login, but force user
                // Completable future that will be executed if
                // re-auth ok
                var assertionRequest = webAuthnService.startReauthentication();
                assertLogin(assertionRequest);
            }));

            add(new Button("Logout", e-> {
                userSession.logout();
                init();
            }));

        } else {
            add(new H1("Log in to test login :-)"));

            Button login = new Button("Login with WebAuthn with existing passkey", e -> {
                AssertionRequest assertionRequest = webAuthnService.startAssertion();
                assertLogin(assertionRequest);
            });

            TextField username = new TextField("Username");
            Button register = new Button("Register!", e -> {
                try {
                    PublicKeyCredentialCreationOptions creationOptions = webAuthnService.startRegistration(username.getValue());
                    String json = creationOptions.toCredentialsCreateJson();
                    JsPromise.resolveString("""
                    var c = %s;
                    fromB64Cred(c);
                    navigator.credentials.create(c).then(cred => {
                      resolve(credentialJsonToServer(cred));
                    });
                    """.formatted(json)).thenAccept(credsJson -> {
                        RegistrationResult registrationResult = webAuthnService.finishRegistration(creationOptions, credsJson);
                        init();
                    });
                } catch (Exception ex) {
                    Notification.show("Failed to create user. " + ex.getMessage());
                }
            });

            add(login, new Hr(), new H3("Register new user/passkey"), username, register);
        }
        listKnownUsers();
    }

    private void listKnownUsers() {
        List<String> knownUsers = webAuthnService.findKnownUsers();
        Grid<String> grid = new Grid<>();
        grid.setWidth("200px");
        grid.addColumn(s -> s);
        grid.setItems(knownUsers);
        grid.getStyle().setMargin("0 auto"); // TODO figure out why not centered
        add(new Hr(), new H3("Known users (no duplicates allowed)"), grid);
    }

    private void assertLogin(AssertionRequest assertionRequest) {
        try {
            String credJson = assertionRequest.toCredentialsGetJson();
            JsPromise.resolveString("""
                // raw credential JSON (binary fields b64d)
                var c = %s;
                // convert binary fields from b64 to bytes
                fromB64Cred(c);
                navigator.credentials.get(c).then(cred => {                
                    resolve(credentialJsonToServer(cred));
                });
                """.formatted(credJson))
                .thenAccept(credentialJson -> {
                    try {
                        if(userSession.isLoggedIn()) {
                            // re-authentication
                            webAuthnService.finishLogin(assertionRequest, credentialJson);
                            Notification.show("Very important task is not safe to execute");
                        } else {
                            webAuthnService.finishLogin(assertionRequest, credentialJson);
                            init();
                        }
                    } catch (Exception e) {
                        Notification.show("Login Failed! (The in-memory test server forgot your passkey 🤔, clear old passkeys from your device and create a new test user)").setPosition(Notification.Position.MIDDLE);
                        e.printStackTrace();
                    }
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }
}
