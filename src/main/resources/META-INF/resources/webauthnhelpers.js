// Note, there are actual JS libraries doing these same things
// but skipping those to avoid front-end build
window.fb64 = base64url => {
    const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
    const binStr = window.atob(base64);
    const bin = new Uint8Array(binStr.length);
    for (let i = 0; i < binStr.length; i++) {
      bin[i] = binStr.charCodeAt(i);
    }
    return bin.buffer;
  };
window.tb64 = buffer => {
    const base64 = window.btoa(String.fromCharCode(...new Uint8Array(buffer)));
    return base64.replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
};
window.fromB64Cred = c => {
    c.publicKey.challenge = fb64(c.publicKey.challenge);
    if(c.publicKey.user && c.publicKey.user.id) {
        c.publicKey.user.id = fb64(c.publicKey.user.id);
    }
    if(c.publicKey.allowCredentials) {
        c.publicKey.allowCredentials.forEach(ac => {
            ac.id = fb64(ac.id);
        });
    }
};
// Serializes credentials data needed on the server to JSON
window.createCredentialJsonForServer = cred => {
    const c = {};
    c.id = cred.id;
    c.type = cred.type;
    // Base64URL encode `rawId`
    c.rawId = tb64(cred.rawId);
    c.clientExtensionResults = cred.getClientExtensionResults();

    // Base64URL encode some values
    c.response = {
        clientDataJSON : tb64(cred.response.clientDataJSON),
        authenticatorData : tb64(cred.response.authenticatorData)
    };
    if(cred.response.attestationObject) {
        c.response.attestationObject = tb64(cred.response.attestationObject);
    }
    if(cred.response.authenticatorData) {
        c.response.authenticatorData = tb64(cred.response.authenticatorData);
    }
    if(cred.response.signature) {
        c.response.signature = tb64(cred.response.signature);
    }
    if(cred.response.userHandle) {
        c.response.userHandle = tb64(cred.response.userHandle);
    }
    return JSON.stringify(c);
}
