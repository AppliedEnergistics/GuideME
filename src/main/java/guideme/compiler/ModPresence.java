package guideme.compiler;

import java.util.List;
import java.util.function.Predicate;
import net.neoforged.fml.ModList;

/**
 * Evaluates mod ids against the mods that are currently loaded.
 * <p>
 * Each id may be prefixed with {@code !} to invert the check, i.e. {@code !jei} is only true if JEI is <i>not</i>
 * loaded.
 */
public final class ModPresence {
    private ModPresence() {
    }

    /**
     * {@return whether the given mod id is loaded, accounting for a leading {@code !} to invert the check}
     *
     * @param modIdExpression The mod id, optionally prefixed with {@code !}.
     */
    public static boolean test(String modIdExpression) {
        return test(modIdExpression, modId -> ModList.get().isLoaded(modId));
    }

    /**
     * {@return whether the given mod id is loaded according to the given predicate, accounting for a leading {@code !}
     * to invert the check}
     */
    public static boolean test(String modIdExpression, Predicate<String> isModLoaded) {
        var invert = modIdExpression.startsWith("!");
        var modId = invert ? modIdExpression.substring(1) : modIdExpression;
        return isModLoaded.test(modId) ^ invert;
    }

    /**
     * {@return whether all given mod ids are matching, i.e. a logical AND of {@link #test(String, Predicate)}}
     */
    public static boolean testAll(List<String> modIdExpressions, Predicate<String> isModLoaded) {
        for (var modIdExpression : modIdExpressions) {
            if (!test(modIdExpression, isModLoaded)) {
                return false;
            }
        }
        return true;
    }
}
