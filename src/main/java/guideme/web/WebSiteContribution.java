package guideme.web;

import java.util.List;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Allows mods to contribute to every page of the generated website.
 * <p>
 * Resources are referenced by their id and are loaded from {@code assets/<namespace>/<path>} on the classpath of the
 * website generator, which usually includes the resources of your mod. Relative {@code url(...)} references in
 * stylesheets are resolved against the stylesheet and the referenced resources are copied as well.
 * <p>
 * <b>NOTE:</b> This is loaded through the Java {@link java.util.ServiceLoader} mechanism. Implementations run in the
 * website generator, outside the game. They may use Minecraft classes, but must not access registries or other game
 * state.
 */
@ApiStatus.Experimental
public interface WebSiteContribution {
    /**
     * {@return stylesheets to include on every page}
     */
    default List<Identifier> getStylesheets() {
        return List.of();
    }

    /**
     * {@return scripts to include on every page, which are loaded with the defer attribute}
     */
    default List<Identifier> getScripts() {
        return List.of();
    }
}
