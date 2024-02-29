package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.example.views.LoginAndRegistrationView;
import org.springframework.web.context.annotation.SessionScope;
import org.vaadin.firitin.util.JsPromise;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A helper class for the UI to tackle all things related to WebAuthn
 * based session handling. Contains methods to login, creating new
 * usernames etc. Executes JSs in the browser and stores login
 * information securely in the server side session.
 * <p>
 *     TODO figure out if the API would be simpler with callback
 *     interfaces instead of using CompletableFuture 🤷‍
 * </p>
 */
@SpringComponent
@SessionScope
public class WebAuthnSession {

    private String username;
    private final WebAuthnService webAuthnService;

    public WebAuthnSession(WebAuthnService webAuthnService) {
        this.webAuthnService = webAuthnService;
    }

    /**
     * WebAuthn API uses binary data in the browser, which is not
     * nice for JSON (transport format). The webauthnhelpers.js
     * injected into the browser contains helpers to convert between
     * base64 & binary data.
     */
    private static void injectWebAuthnHelperJavaScripts() {
        UI.getCurrent().getPage().addJavaScript("context://webauthnhelpers.js");
    }

    /**
     * Starts a process to register given username and generate a
     * passkey in the browser for it.
     *
     * @param username the requested username
     * @return a CompletableFuture to execute UI code once the
     * passkey is saved.
     */
    public CompletableFuture<Void> registerUser(String username) {
        injectWebAuthnHelperJavaScripts();

        // Returning a void CompletableFuture that the UI can use
        // to execute logic after successful registration
        try {
            // Uses Yubico's server library to start a username registration
            // process. The creation options contains e.g. the username &
            // a challenge that the server can verify the process was started
            // by it.
            var creationOptions = webAuthnService.startRegistration(username);
            // Make a JSON that can be sent to browser via Vaadin's JS API
            // send it to browser and request a new passkey
            // with the WebAuthn API, once user has created one, it will be
            // passed back to the server side for validation/persistence
            String json = creationOptions.toCredentialsCreateJson();
            // Evaluate an async JS code in the browser within an async JS method
            // and return the value as JSON back to the server
            return JsPromise.computeString("""
            // the JSON gets to the c variable
            var c = %s;
            // convert base64 fields to bytes
            fromB64Cred(c);
            // trigger the browser dialog to generate passkey
            const cred = await navigator.credentials.create(c);
            // send the generated passkey data back to server as return value
            return createCredentialJsonForServer(cred);
            """.formatted(json)).thenAccept(credsJson -> {
                // credsJson is the stringified/base64 JSON from the WebAuthn API

                // Let the Yubico's library map the JSON to Java objects,
                // do the cryptography to validate the process was really started
                // by us and save the public key part of the generated passkey to
                // the "database" (in memory in this demo app).
                try {
                    webAuthnService.finishRegistration(creationOptions, credsJson);
                    // save the username to session and complete the future
                    setUser(creationOptions.getUser().getName());
                } catch (RegistrationFailedException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (JsonProcessingException ex) {
            throw new RuntimeException();
        }
    }


    /**
     * Starts a login proses for existing users and returns the
     * username after a successful login. The username can also
     * be retrieved via getUsername() method after a successful
     * login.
     *
     * @return a CompletableFuture for the username
     */
    public CompletableFuture<String> login() {
        injectWebAuthnHelperJavaScripts();
        // Uses Yubico's server library to create a challenge etc that is
        // needed to start the login process in the browser
        AssertionRequest assertionRequest = webAuthnService.startAssertion();
        try {
            // Make a JSON of the needed request data
            String credJson = assertionRequest.toCredentialsGetJson();
            // Use the WebAuthn API in the browser and return the
            // credentials from it back to the server
            return JsPromise.computeString("""
            // raw credential JSON (binary fields b64d)
            var c = %s;
            // convert binary fields from base64 to bytes
            fromB64Cred(c);
            const cred = await navigator.credentials.get(c);
            return createCredentialJsonForServer(cred);
            """.formatted(credJson)).thenApply(credentialJson -> {
                try {
                    // Let the Yubico's library to parse the response and
                    // do the cryptographic checks this is a response to our
                    // original challenge and the passkey is from its original
                    // issuer
                    String username = webAuthnService
                            .finishAssertion(assertionRequest, credentialJson);
                    // Save the username to session
                    setUser(username);
                    // also return it for the UI in the CompletableFuture
                    return username;
                } catch (IOException | AssertionFailedException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method can be used to re-authenticate user before an
     * action that needs an extra layer of security. In practice,
     * a user for example uses fingerprint censor or facial recognition
     * and then the action is executed.
     * <p>
     *     This is almost the same as login, but the user can not choose
     *     which passkey to use. Instead we force that to be the same
     *     that was used for the login.
     * </p>
     *
     * @return a {@link CompletableFuture} that can be used to run
     * an important action if the user re-authenticated properly.
     */
    public CompletableFuture<Void> runReauthenticated() {
        Objects.requireNonNull(username);
        injectWebAuthnHelperJavaScripts();
        AssertionRequest assertionRequest = webAuthnService.startReauthentication(username);
        try {
            String credJson = assertionRequest.toCredentialsGetJson();
            return JsPromise.computeString("""
            // raw credential JSON (binary fields b64d)
            var c = %s;
            // convert binary fields from b64 to bytes
            fromB64Cred(c);
            const cred = await navigator.credentials.get(c);
            return createCredentialJsonForServer(cred);
            """.formatted(credJson)).thenAccept(credentialJson -> {
                try {
                    webAuthnService.finishAssertion(assertionRequest, credentialJson);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean userExists(String username) {
        return webAuthnService.findKnownUsers().contains(username);
    }

    public boolean isLoggedIn() {
        return username != null;
    }

    public String getUsername() {
        return username;
    }

    public void logout() {
        username = null;
        UI.getCurrent().navigate(LoginAndRegistrationView.class);
    }

    public void setUser(String name) {
        this.username = name;
    }

}