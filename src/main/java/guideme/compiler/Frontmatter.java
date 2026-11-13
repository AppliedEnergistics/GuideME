package guideme.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public record Frontmatter(
        @Nullable FrontmatterNavigation navigationEntry,
        List<String> requiredMods,
        Map<String, Object> additionalProperties) {
    /**
     * Key in the frontmatter that lists the mods a page requires to be shown.
     */
    public static final String REQUIRED_MODS_KEY = "required_mods";

    public Frontmatter(@Nullable FrontmatterNavigation navigationEntry, Map<String, Object> additionalProperties) {
        this(navigationEntry, List.of(), additionalProperties);
    }

    /**
     * {@return whether all mods required by this page are loaded, using {@link ModPresence} semantics (i.e. ids
     * prefixed with {@code !} have to be absent)}
     */
    public boolean isAvailable(Predicate<String> isModLoaded) {
        return ModPresence.testAll(requiredMods, isModLoaded);
    }

    public static Frontmatter parse(Identifier pageId, String yamlText) {
        var yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

        FrontmatterNavigation navigation = null;
        Map<String, Object> data = yaml.load(yamlText);
        var requiredMods = getStringList(data.remove(REQUIRED_MODS_KEY), REQUIRED_MODS_KEY);
        var navigationObj = data.remove("navigation");
        if (navigationObj != null) {
            if (!(navigationObj instanceof Map<?, ?> navigationMap)) {
                throw new IllegalArgumentException("The navigation key in the frontmatter has to be a map");
            }

            var title = getString(navigationMap, "title");
            if (title == null) {
                throw new IllegalArgumentException("title is missing in navigation frontmatter");
            }
            var parentIdStr = getString(navigationMap, "parent");
            var position = 0;
            if (navigationMap.containsKey("position")) {
                position = getInt(navigationMap, "position");
            }
            var iconIdStr = getString(navigationMap, "icon");
            Map<?, ?> iconComponents = getCompound(navigationMap, "icon_components");
            Integer maxDepth = null;
            if (navigationMap.containsKey("max_depth")) {
                maxDepth = getInt(navigationMap, "max_depth");
                if (maxDepth < 0) {
                    throw new IllegalArgumentException("max_depth in navigation frontmatter must not be negative");
                }
            }

            Identifier parentId = null;
            if (parentIdStr != null) {
                parentId = IdUtils.resolveId(parentIdStr, pageId.getNamespace());
            }

            Identifier iconId = null;
            if (iconIdStr != null) {
                iconId = IdUtils.resolveId(iconIdStr, pageId.getNamespace());
            }

            navigation = new FrontmatterNavigation(title, parentId, position, iconId, iconComponents, maxDepth);
        }

        return new Frontmatter(
                navigation,
                requiredMods,
                Map.copyOf(data));
    }

    /**
     * Reads a list of strings, which can also be given as a single string for convenience.
     */
    private static List<String> getStringList(@Nullable Object value, String key) {
        if (value == null) {
            return List.of();
        }

        List<?> list;
        if (value instanceof String singleValue) {
            list = List.of(singleValue);
        } else if (value instanceof List<?> listValue) {
            list = listValue;
        } else {
            throw new IllegalArgumentException("Key " + key + " has to be a list of strings!");
        }

        var result = new ArrayList<String>(list.size());
        for (var element : list) {
            if (!(element instanceof String modId)) {
                throw new IllegalArgumentException("Key " + key + " has to only contain strings!");
            }
            if (modId.isBlank() || modId.equals("!")) {
                throw new IllegalArgumentException("Key " + key + " must not contain empty mod ids!");
            }
            result.add(modId);
        }
        return List.copyOf(result);
    }

    @Nullable
    private static String getString(Map<?, ?> map, String key) {
        var value = map.get(key);
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException("Key " + key + " has to be a String!");
        }
        return (String) value;
    }

    private static int getInt(Map<?, ?> map, String key) {
        var value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Key " + key + " is missing in navigation frontmatter");
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Key " + key + " has to be a number!");
        }
        return number.intValue();
    }

    @Nullable
    private static Map<?, ?> getCompound(Map<?, ?> map, String key) {
        var value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> mapValue)) {
            throw new IllegalArgumentException("Key " + key + " has to be a map!");
        }
        return mapValue;
    }
}
