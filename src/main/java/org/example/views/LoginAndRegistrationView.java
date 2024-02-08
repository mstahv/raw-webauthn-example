package org.example.views;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.button.Button;
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

import java.util.List;

@Route("")
public class LoginAndRegistrationView extends VerticalLayout {

    private final WebAuthnService webAuthnService;
    private final UserSession userSession;
    private AssertionRequest assertionRequest;
    private PublicKeyCredentialCreationOptions creationOptions;

    public LoginAndRegistrationView(WebAuthnService webAuthnService, UserSession userSession) {
        this.webAuthnService = webAuthnService;
        this.userSession = userSession;
        init();
        setAlignItems(Alignment.CENTER);

    }

    private void init() {
        removeAll();
        if(userSession.isLoggedIn()) {
            add(new H1("You are logged in as " + userSession.getUsername()));

            add(new Button("TODO: Try important action, will request re-authentication", e-> {
                // TODO, like login, but force user
                // Completable future that will be executed if
                // re-auth ok
            }));

            add(new Button("Logout", e-> {
                userSession.logout();
                init();
            }));

        } else {
            add(new H1("Log in to test login :-)"));

            Button login = new Button("Login with WebAuthn with existing passkey", e -> {
                assertionRequest = webAuthnService.startAssertion();
                assertLogin();
            });

            TextField username = new TextField("Username");
            Button register = new Button("Register!", e -> {
                try {
                    creationOptions = webAuthnService.startRegistration(username.getValue());
                    createPasskeyInClient();
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

    private void createPasskeyInClient() {
        try {
            String json = creationOptions.toCredentialsCreateJson();
            getElement().executeJs("""
                    const server = this.$server;
                    const fb64 = base64url => {
                        const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
                        const binStr = window.atob(base64);
                        const bin = new Uint8Array(binStr.length);
                        for (let i = 0; i < binStr.length; i++) {
                          bin[i] = binStr.charCodeAt(i);
                        }
                        return bin.buffer;
                      };
                    const tb64 = buffer => {
                        const base64 = window.btoa(String.fromCharCode(...new Uint8Array(buffer)));
                        return base64.replace(/=/g, '').replace(/\\+/g, '-').replace(/\\//g, '_');
                    };
                    var c = %s;
                    c.publicKey.challenge = fb64(c.publicKey.challenge);
                    c.publicKey.user.id = fb64(c.publicKey.user.id);
                    navigator.credentials.create(c).then(cred => {
                      const credential = {};
                      credential.id = cred.id;
                      credential.type = cred.type;
                      // Base64URL encode `rawId`
                      credential.rawId = tb64(cred.rawId);
                      credential.clientExtensionResults = cred.getClientExtensionResults();
                                    
                      // Base64URL encode some values
                      const clientDataJSON = tb64(cred.response.clientDataJSON);
                      const attestationObject = tb64(cred.response.attestationObject);
                      const authenticatorData = tb64(cred.response.authenticatorData);
                                    
                      credential.response = {
                        clientDataJSON,
                        attestationObject,
                        authenticatorData
                      };
                      server.validateRegistration(JSON.stringify(credential));
                    });
                    """.formatted(json));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    private void assertLogin() {
        try {
            String json = assertionRequest.toCredentialsGetJson();
            getElement().executeJs("""
                    const server = this.$server;
                    const fb64 = base64url => {
                        const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
                        const binStr = window.atob(base64);
                        const bin = new Uint8Array(binStr.length);
                        for (let i = 0; i < binStr.length; i++) {
                          bin[i] = binStr.charCodeAt(i);
                        }
                        return bin.buffer;
                      };
                    const tb64 = buffer => {
                        const base64 = window.btoa(String.fromCharCode(...new Uint8Array(buffer)));
                        return base64.replace(/=/g, '').replace(/\\+/g, '-').replace(/\\//g, '_');
                    };
                    var c = %s;
                    c.publicKey.challenge = fb64(c.publicKey.challenge);

                    navigator.credentials.get(c).then(cred => {
                          const credential = {};
                          credential.id = cred.id;
                          credential.type = cred.type;
                          // Base64URL encode `rawId`
                          credential.rawId = tb64(cred.rawId);
                          credential.clientExtensionResults = cred.getClientExtensionResults();
                        
                          // Base64URL encode some values
                          const clientDataJSON = tb64(cred.response.clientDataJSON);
                          const authenticatorData = tb64(cred.response.authenticatorData);
                          const signature = tb64(cred.response.signature);
                          const userHandle = tb64(cred.response.userHandle);
                        
                          credential.response = {
                            clientDataJSON,
                            authenticatorData,
                            signature,
                            userHandle,
                          };
                        
                        server.validateLogin(JSON.stringify(credential));
                    });
                    """.formatted(json));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    @ClientCallable
    private void validateRegistration(String publicKeyCredentialJson) {
        RegistrationResult registrationResult = webAuthnService.finishRegistration(creationOptions, publicKeyCredentialJson);
        init();
    }

    @ClientCallable
    private void validateLogin(String publicKeyCredentialJson) {
        try {
            webAuthnService.finishLogin(assertionRequest, publicKeyCredentialJson);
            init();
        } catch (Exception e) {
            Notification.show("Login Failed! (The in-memory test server forgot your passkey 🤔, clear old passkeys from your device and create a new test user)").setPosition(Notification.Position.MIDDLE);
            e.printStackTrace();
        }
    }
}
