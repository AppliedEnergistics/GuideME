package guideme.color;

import guideme.compiler.PageCompiler;
import guideme.extensions.Extension;
import guideme.extensions.ExtensionPoint;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * This extension point can be used to register custom symbolic colors in your guide.
 */
public interface SymbolicColorResolver extends Extension {
    ExtensionPoint<SymbolicColorResolver> EXTENSION_POINT = new ExtensionPoint<>(SymbolicColorResolver.class);

    /**
     * Attempt to resolve a custom symbolic color value.
     *
     * @param id The id of the color.
     * @return Null if the color is unknown to this resolver.
     */
    @Nullable
    ColorValue resolve(ResourceLocation id);

    /**
     * Helper to resolve a symbolic color from both the pre-defined colors in {@link SymbolicColor}, as well as
     * user-supplied symbolic color resolvers.
     *
     * @return null when the color cannot be resolved.
     */
    @Nullable
    static ColorValue resolve(PageCompiler compiler, String id) {
        try {
            return SymbolicColor.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }

        // See if it's a resource location
        ResourceLocation resourceLocation;
        try {
            resourceLocation = compiler.resolveId(id);
        } catch (Exception e) {
            return null; // Invalid resource location
        }
        if (resourceLocation == null) {
            return null;
        }

        for (var resolver : compiler.getExtensions(EXTENSION_POINT)) {
            var color = resolver.resolve(resourceLocation);
            if (color != null) {
                return color;
            }
        }

        return null;
    }
}
