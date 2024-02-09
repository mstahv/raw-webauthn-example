package org.example.views;


import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.yubico.webauthn.AssertionRequest;
import org.example.UserSession;
import org.example.WebAuthnService;
import org.springframework.web.context.annotation.SessionScope;

@SessionScope
public class ImportantTaskDialog extends Dialog {

    private final UserSession session;
    private final WebAuthnService service;

    public ImportantTaskDialog(UserSession session, WebAuthnService service) {
        this.session = session;
        this.service = service;

        setHeaderTitle("Execute an important action");
        add("Consider you would be transferring a large amount of money or launching a nuke. It would be probably good that somebody just didn't forget the lit open in their laptop, and the fellow behind the keyboard is still there. Before the action initiated by the button below is executed, you will be requested to verify yourself again, but this time the username/passkey is fixed to the one you already provided for this session.");

        add(new Button("Re-authenticate and execute!", e-> {
            AssertionRequest assertionRequest = service.startReauthentication();
        }));


    }
}
