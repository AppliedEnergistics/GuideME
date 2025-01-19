package guideme.internal;

import guideme.MutableGuide;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal registry for Guides.
 */
public class GuideRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(GuideRegistry.class);

    private static final ConcurrentHashMap<ResourceLocation, MutableGuide> guides = new ConcurrentHashMap<>();

    private static final Map<ResourceLocation, MutableGuide> dataDrivenGuides = new HashMap<>();

    // Merged between data-driven and in-code guides
    private static volatile Map<ResourceLocation, MutableGuide> mergedGuides = Map.of();

    public static Collection<MutableGuide> getAll() {
        return mergedGuides.values();
    }

    public static @Nullable MutableGuide getById(ResourceLocation id) {
        return mergedGuides.get(id);
    }

    /**
     * Register a static guide (implemented in code).
     */
    public static void registerStatic(MutableGuide guide) {
        if (guides.putIfAbsent(guide.getId(), guide) != null) {
            throw new IllegalStateException("There is already a Guide registered with id " + guide.getId());
        }

        rebuildGuides();
    }

    /**
     * Remove a static guide (implemented in code). This is primarily for testing purposes.
     */
    public static void unregisterStatic(MutableGuide guide) {
        if (guides.remove(guide.getId(), guide)) {
            rebuildGuides();
        }
    }

    /**
     * Register all dynamic guides (defined in resource packs), which replaces all previously available dynamic guides.
     */
    public static void setDataDriven(Map<ResourceLocation, MutableGuide> guides) {
        dataDrivenGuides.clear();
        dataDrivenGuides.putAll(guides);

        rebuildGuides();
    }

    private static void rebuildGuides() {
        var merged = new HashMap<>(guides);
        var overridden = new ArrayList<ResourceLocation>();
        for (var entry : dataDrivenGuides.entrySet()) {
            if (merged.put(entry.getKey(), entry.getValue()) != null) {
                overridden.add(entry.getKey());
            }
        }

        if (!overridden.isEmpty()) {
            Collections.sort(overridden);
            LOG.info("The following guides are overridden in resource packs: {}", overridden);
        }

        GuideRegistry.mergedGuides = Map.copyOf(merged);
    }
}
