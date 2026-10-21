package guideme.siteexport;

import java.util.function.IntUnaryOperator;

/**
 * Represents a pixel modifier to apply to a texture or sprite during export. The exported file's name is suffixed with
 * this modifier's name.
 */
public interface NamedPixelModifier extends IntUnaryOperator {
    @Override
    int applyAsInt(int pixel);

    /**
     * @return the name of this modifier for suffixing the exported file's name
     */
    String name();
}
