package org.example;

import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * A service class that mostly uses Yubico's Java WebAuthn library.
 * <p>
 * In this demo app we have only passkey based logins, one per users,
 * and users can pick up any unique username when registering. Although
 * real life setup is rarely this simple, especially for existing systems,
 * the example should help to figure out how WebAuthn works. Code mostly
 * derived from Yubico's docs.
 * </p>
 * This service is used primarily via {@link WebAuthnSession} that works
 * between the UI, browser's WebAuthn JS API and this service (~ the "backend").
 */
@Service
public class WebAuthnService {

    private final RelyingPartyIdentity rpIdentity;
    private final RelyingParty rp;
    private final InMemoryRegistrationRepository repository;
    Random random = new Random();

    public WebAuthnService(InMemoryRegistrationRepository repository) {
        // repository is our "in memory database", our "user database"
        this.repository = repository;

        // The main purpose of this demo is that it can be launched locally.
        // "localhost" domain has exceptions in browser -> no https required etc 💪
        String domain = "localhost";
        if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            // a public deployment of this example
            domain = "webauthn.dokku1.parttio.org";
        }

        rpIdentity = RelyingPartyIdentity.builder()
                .id(domain)  // Set this to a parent domain that covers all subdomains
                // where users' credentials should be valid
                .name("Vaadin WebAuthn Example")
                .build();

        rp = RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(repository)
                .allowOriginPort(true)
                .build();

    }

    public AssertionRequest startReauthentication(String username) {
        return rp.startAssertion(StartAssertionOptions.builder()
                // only allow currently logged-in username
                .username(username)
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build());
    }

    public AssertionRequest startAssertion() {
        return rp.startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.PREFERRED)
                .build());
    }

    public PublicKeyCredentialCreationOptions startRegistration(String usernameValue) {
        if (!repository.getCredentialIdsForUsername(usernameValue).isEmpty()) {
            throw new RuntimeException("Username already exists!");
        }

        return rp.startRegistration(
                StartRegistrationOptions.builder()
                        .user(
                                UserIdentity.builder()
                                        .name(usernameValue)
                                        .displayName(usernameValue)
                                        .id(generateRandom(32))
                                        .build()
                        ).build());
    }

    private ByteArray generateRandom(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return new ByteArray(bytes);
    }

    public String finishAssertion(AssertionRequest assertionRequest, String publicKeyCredentialJson) throws IOException, AssertionFailedException {
        var response = PublicKeyCredential.parseAssertionResponseJson(publicKeyCredentialJson);
        AssertionResult assertionResult = rp.finishAssertion(FinishAssertionOptions.builder()
                .request(assertionRequest)
                .response(response)
                .build());
        return assertionResult.getUsername();
    }

    public RegistrationResult finishRegistration(PublicKeyCredentialCreationOptions creationOptions, String publicKeyCredentialJson) throws RegistrationFailedException, IOException {
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc = PublicKeyCredential.parseRegistrationResponseJson(publicKeyCredentialJson);
        RegistrationResult registrationResult = rp.finishRegistration(FinishRegistrationOptions.builder()
                .request(creationOptions)  // The PublicKeyCredentialCreationOptions from startRegistration above
                // NOTE: Must be stored in server memory or otherwise protected against tampering
                .response(pkc)
                .build());

        repository.storeCredential(
                creationOptions.getUser().getName(),
                creationOptions.getUser().getId(),
                registrationResult.getKeyId(),
                registrationResult.getPublicKeyCose(),
                pkc.getResponse().getAttestation(),
                pkc.getResponse().getClientDataJSON()
        );
        return registrationResult;

    }

    public List<String> findKnownUsers() {
        return repository.findKnownUsers();
    }
}
