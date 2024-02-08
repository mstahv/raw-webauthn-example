package org.example;

import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Random;

@Service
public class WebAuthnService {

    private final RelyingPartyIdentity rpIdentity;
    private final RelyingParty rp;
    private final InMemoryRegistrationRepository repository;
    private final UserSession userSession;
    Random random = new Random();

    public WebAuthnService(InMemoryRegistrationRepository storage, UserSession userSession) {
        this.repository = storage;
        this.userSession = userSession;

        rpIdentity = RelyingPartyIdentity.builder()
                .id("webauthn.dokku1.parttio.org")  // Set this to a parent domain that covers all subdomains
                // where users' credentials should be valid
                .name("Vaadin WebAuthn Example")
                .build();

        rp = RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(storage)
                .allowOriginPort(true)
                .build();

    }

    public AssertionRequest startAssertion() {
        return rp.startAssertion(StartAssertionOptions.builder().build());
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

    public void finishLogin(AssertionRequest assertionRequest, String publicKeyCredentialJson) {
        try {
            var response = PublicKeyCredential.parseAssertionResponseJson(publicKeyCredentialJson);
            AssertionResult assertionResult = rp.finishAssertion(FinishAssertionOptions.builder()
                    .request(assertionRequest)
                    .response(response)
                    .build());
            userSession.setUser(assertionResult.getUsername());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (AssertionFailedException e) {
            throw new RuntimeException(e);
        }

    }

    public RegistrationResult finishRegistration(PublicKeyCredentialCreationOptions creationOptions, String publicKeyCredentialJson) {
        try {
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
            userSession.setUser(creationOptions.getUser().getName());
            return registrationResult;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (RegistrationFailedException e) {
            throw new RuntimeException(e);
        }

    }

    public List<String> findKnownUsers() {
        return repository.findKnonwnUsers();
    }
}
