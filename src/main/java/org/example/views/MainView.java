package org.example.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.example.WebAuthnSession;

@Route("")
public class MainView extends VerticalLayout {

    private final WebAuthnSession userSession;
    private final ImportantTaskDialog importantTaskDialog;
    private final UserListing userListing;

    public MainView(WebAuthnSession userSession, ImportantTaskDialog importantTaskDialog, UserListing userListing) {
        this.userSession = userSession;
        this.importantTaskDialog = importantTaskDialog;
        this.userListing = userListing;
        setAlignItems(Alignment.CENTER);
        init();
    }

    private void init() {
        add(new H1("This is the main view"));

        add(new Paragraph("You are logged in as " + userSession.getUsername()));

        add(new Button("Try important action", e -> {
            importantTaskDialog.open();
        }));

        add(new Button("Logout", e -> {
            userSession.logout();
        }));

        add(new Hr());

        add(userListing);

    }

}
