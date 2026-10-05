package guideme.web;

import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;

/**
 * Registered through META-INF/services to test discovery of custom element renderers and the rendering context API.
 */
public class TestElementWebRenderer implements CustomElementWebRenderer {
    @Override
    public Set<String> getTagNames() {
        return Set.of("guidemetest:TestElement");
    }

    @Override
    public void render(CustomElementWebRenderingContext context, Consumer<HtmlNode> output) {
        record Attributes(String item) {
        }
        var attributes = context.map(Attributes.class);

        output.accept(HtmlNode.tag("div")
                .setClassName("test-element")
                .append(context.itemIcon(attributes.item(), false))
                .append(context.itemLink(attributes.item(), new HtmlFragment(HtmlNode.text("link text"))))
                .append(HtmlNode.tag("a").setAttribute("href", context.getPageUrl("testmod:other.md")))
                .append(HtmlNode.tag("img").setAttribute("src",
                        context.getAssetUrl(Identifier.fromNamespaceAndPath("guidemetest", "web/images/test.png"))))
                .append(context.compileChildren()));
    }
}
