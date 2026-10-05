package guideme.testmod;

import guideme.web.WebSiteContribution;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * Tests that mods can add stylesheets to the generated website.
 */
public class TestModWebSiteContribution implements WebSiteContribution {
    @Override
    public List<Identifier> getStylesheets() {
        return List.of(Identifier.fromNamespaceAndPath("testmod", "web/testmod.css"));
    }
}
