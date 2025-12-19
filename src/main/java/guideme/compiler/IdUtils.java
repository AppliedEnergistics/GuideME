package guideme.compiler;

import java.net.URI;
import net.minecraft.IdentifierException;
import net.minecraft.resources.Identifier;

/**
 * Helper to resolve shorthand and relative IDs found in markdown pages.
 */
public final class IdUtils {

    private IdUtils() {
    }

    public static Identifier resolveId(String idText, String defaultNamespace) {
        if (!idText.contains(":")) {
            return Identifier.fromNamespaceAndPath(defaultNamespace, idText);
        }
        return Identifier.parse(idText);
    }

    /**
     * Supports relative resource locations such as: ./somepath, which would resolve relative to a given anchor
     * location. Relative locations must not be namespaced since we would otherwise run into the problem if namespaced
     * locations potentially having a different namespace than the anchor.
     */
    public static Identifier resolveLink(String idText, Identifier anchor)
            throws IdentifierException {
        if (idText.startsWith("/")) {
            // Absolute path, but relative to namespace
            return Identifier.fromNamespaceAndPath(anchor.getNamespace(), idText.substring(1));
        } else if (!idText.contains(":")) {
            URI uri = URI.create(anchor.getPath());
            uri = uri.resolve(idText);

            var relativeId = uri.toString();

            return Identifier.fromNamespaceAndPath(anchor.getNamespace(), relativeId);
        }

        // if it contains a ":" it's assumed to be absolute
        return Identifier.parse(idText);
    }

}
