package org.example.views;


import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.example.WebAuthnSession;
import org.springframework.context.annotation.Scope;
import org.vaadin.firitin.components.button.VButton;

@Scope("prototype")
@SpringComponent
public class ImportantTaskDialog extends Dialog {

    private final WebAuthnSession webAuthnSession;

    public ImportantTaskDialog(WebAuthnSession webAuthnSession) {
        this.webAuthnSession = webAuthnSession;

        setHeaderTitle("Execute an important action");
        add(new Paragraph("Consider you would be transferring a large " +
                "amount of money or launching a nuke. It would be probably" +
                " good to verify that somebody just didn't forget the lit open in their" +
                " laptop while going for a cup of coffee." +
                "Before the action initiated by the button below is executed, " +
                "you will be requested to verify yourself again, but this time " +
                "the username/passkey is fixed to the one you already provided " +
                "for this session."));

        add(new VButton("Re-authenticate and execute!", e-> {
            webAuthnSession.runReauthenticated().thenAccept(v -> {
                // This would be executed only after a fingerprint scanning
                // or similar "re-login"
                Notification.show("Congrats, you got the millions!")
                        .setPosition(Notification.Position.MIDDLE);
                close();
            }).exceptionally(ex -> {
                Notification.show("Re-authentication failed! " + ex.getMessage())
                        .setPosition(Notification.Position.MIDDLE);
                return null;
            });
        }).withClassName(LumoUtility.TextColor.ERROR));


    }
}
