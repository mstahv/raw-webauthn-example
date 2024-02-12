package org.example.views;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.example.WebAuthnService;
import org.springframework.context.annotation.Scope;

@Scope("prototype")
@SpringComponent
public class UserListing extends VerticalLayout {

    public UserListing(WebAuthnService webAuthnService) {
        setAlignItems(Alignment.CENTER);
        Grid<String> grid = new Grid<>();
        grid.setWidth("300px");
        grid.addColumn(s -> s);
        grid.setItems(webAuthnService.findKnownUsers());
        grid.getStyle().setMargin("0 auto"); // TODO figure out why not centered
        add(new H3("Known users (no dupes allowed)"), grid);
    }
}
