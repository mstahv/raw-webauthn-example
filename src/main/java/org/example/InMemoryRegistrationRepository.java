package org.example;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.AttestationObject;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

// TODO clean this s%#t
@Service
public class InMemoryRegistrationRepository implements CredentialRepository {

    private Map<String, Set<RegisteredCredential>> usernameToCredential = new HashMap<>();
    private Map<String, Set<PublicKeyCredentialDescriptor>> usernameToKey = new HashMap<>();

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String s) {
        return usernameToKey.computeIfAbsent(s, k -> new HashSet<>());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String s) {
        return usernameToCredential.computeIfAbsent(s, k -> new HashSet<>())
                .stream().map(c -> c.getUserHandle()).findFirst();
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        AtomicReference<String> username = new AtomicReference<>(null);
        usernameToCredential.forEach((u, c) -> {
            Optional<RegisteredCredential> first = c.stream().filter(rc -> rc.getUserHandle().equals(userHandle)).findFirst();
            if(first.isPresent()) {
                username.set(u);
            }
        });
        return Optional.ofNullable(username.get());
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {

        Optional<RegisteredCredential> registrationMaybe =
                usernameToCredential.values().stream()
                        .flatMap(Collection::stream)
                        .filter(
                                credReg ->
                                        credentialId.equals(credReg.getCredentialId())
                                                && userHandle.equals(credReg.getUserHandle()))
                        .findAny();

        return registrationMaybe;
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        HashSet<RegisteredCredential> res = new HashSet<>();
        usernameToCredential.forEach((uname, creds) -> {
            creds.forEach(c -> {
                if(c.getCredentialId().equals(credentialId)) {
                    res.add(c);
                }
            });
        });
        return res;
    }

    public void storeCredential(String username, ByteArray user, PublicKeyCredentialDescriptor keyId, ByteArray publicKeyCose, AttestationObject attestation, ByteArray clientDataJSON) {
        RegisteredCredential registeredCredential = RegisteredCredential.builder()
                .credentialId(keyId.getId())
                .userHandle(user)
                .publicKeyCose(publicKeyCose)
                .build();
        usernameToCredential.computeIfAbsent(username, k -> new HashSet<>()).add(registeredCredential);
        usernameToKey.computeIfAbsent(username, k -> new HashSet<>()).add(keyId);
    }

    public List<String> findKnonwnUsers() {
        return usernameToCredential.keySet().stream().toList();
    }
}