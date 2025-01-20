package guideme;

import guideme.extensions.Extension;
import guideme.extensions.ExtensionCollection;
import guideme.extensions.ExtensionPoint;
import guideme.indices.CategoryIndex;
import guideme.indices.ItemIndex;
import guideme.indices.PageIndex;
import guideme.internal.GuideRegistry;
import guideme.internal.MutableGuide;
import guideme.internal.extensions.DefaultExtensions;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class GuideBuilder {
    private final ResourceLocation id;
    private final Map<Class<?>, PageIndex> indices = new IdentityHashMap<>();
    private final ExtensionCollection.Builder extensionsBuilder = ExtensionCollection.builder();
    private String defaultNamespace;
    private String folder;
    private ResourceLocation startPage;
    private Path developmentSourceFolder;
    private String developmentSourceNamespace;
    private boolean watchDevelopmentSources = true;
    private boolean disableDefaultExtensions = false;
    private boolean availableToOpenHotkey = true;
    private final Set<ExtensionPoint<?>> disableDefaultsForExtensionPoints = Collections
            .newSetFromMap(new IdentityHashMap<>());
    private boolean register = true;
    private GuideItemSettings itemSettings = GuideItemSettings.DEFAULT;

    GuideBuilder(ResourceLocation id) {
        this.id = Objects.requireNonNull(id, "id");
        this.defaultNamespace = id.getNamespace();
        this.folder = "guides/" + id.getNamespace() + "/" + id.getPath();
        this.startPage = ResourceLocation.fromNamespaceAndPath(defaultNamespace, "index.md");

        // Development sources folder
        var devSourcesFolderProperty = getSystemPropertyName(id, "sources");
        var devSourcesNamespaceProperty = getSystemPropertyName(id, "sourcesNamespace");
        var sourceFolder = System.getProperty(devSourcesFolderProperty);
        if (sourceFolder != null) {
            developmentSourceFolder = Paths.get(sourceFolder);
            // Allow overriding which Mod-ID is used for the sources in the given folder
            developmentSourceNamespace = System.getProperty(devSourcesNamespaceProperty, defaultNamespace);
        }

        // Add default indices
        index(new ItemIndex());
        index(new CategoryIndex());
    }

    /**
     * Allows the automated registration in the global Guide registry to be disabled. This is mostly useful for testing
     * purposes.
     * <p/>
     * Disabling registration of the guide will disable several features for this guide:
     * <ul>
     * <li>Automatically showing the guide on startup</li>
     * <li>The open hotkey</li>
     * <li>Automatically reloading pages on resource reload</li>
     * </ul>
     */
    public GuideBuilder register(boolean enable) {
        this.register = enable;
        return this;
    }

    /**
     * Sets the default resource namespace for this guide. This namespace is used for resources loaded from a plain
     * folder during development and defaults to the namespace of the guide id.
     */
    public GuideBuilder defaultNamespace(String defaultNamespace) {
        // Both folder and default namespace need to be valid resource paths
        if (!ResourceLocation.isValidNamespace(defaultNamespace)) {
            throw new IllegalArgumentException("The default namespace for a guide needs to be a valid namespace");
        }
        this.defaultNamespace = defaultNamespace;
        return this;
    }

    /**
     * Sets the folder within the resource pack, from which pages for this guide will be loaded. Please note that this
     * name must be unique across all namespaces, since it would otherwise cause pages from guides added by other mods
     * to show up in yours.
     * <p/>
     * This defaults to {@code guides/<namespace>/<path>} with namespace and path coming from the guide id, which should
     * implicitly make it unique.
     */
    public GuideBuilder folder(String folder) {
        if (!ResourceLocation.isValidPath(folder)) {
            throw new IllegalArgumentException("The folder for a guide needs to be a valid resource location");
        }
        this.folder = folder;
        return this;
    }

    /**
     * Stops the builder from adding any of the default extensions. Use
     * {@link #disableDefaultExtensions(ExtensionPoint)} to disable the default extensions only for one of the extension
     * points.
     */
    public GuideBuilder disableDefaultExtensions() {
        this.disableDefaultExtensions = true;
        return this;
    }

    /**
     * Disables the global open hotkey from using this guide.
     */
    public GuideBuilder disableOpenHotkey() {
        this.availableToOpenHotkey = false;
        return this;
    }

    /**
     * Stops the builder from adding any of the default extensions to the given extension point.
     * {@link #disableDefaultExtensions()} takes precedence and will disable all extension points.
     */
    public GuideBuilder disableDefaultExtensions(ExtensionPoint<?> extensionPoint) {
        this.disableDefaultsForExtensionPoints.add(extensionPoint);
        return this;
    }

    /**
     * Set the page to show when this guide is being opened without any previous page or target page. Defaults to
     * {@code index.md} in the {@link #defaultNamespace(String) default namespace}.
     */
    public GuideBuilder startPage(ResourceLocation pageId) {
        this.startPage = pageId;
        return this;
    }

    /**
     * See {@linkplain #developmentSources(Path, String)}. Uses the default namespace of the guide as the namespace for
     * the pages and resources in the folder.
     */
    public GuideBuilder developmentSources(@Nullable Path folder) {
        return developmentSources(folder, defaultNamespace);
    }

    /**
     * Load additional page resources and assets from the given folder. Useful during development in conjunction with
     * {@link #watchDevelopmentSources} to automatically reload pages during development.
     * <p/>
     * All resources in the given folder are treated as if they were in the given namespace and the folder given to
     * {@link Guide#builder}.
     * <p/>
     * The default values for folder and namespace will be taken from the system properties:
     * <ul>
     * <li><code>guideDev.&lt;FOLDER>.sources</code></li>
     * <li><code>guideDev.&lt;FOLDER>.sourcesNamespace</code></li>
     * </ul>
     */
    public GuideBuilder developmentSources(Path folder, String namespace) {
        this.developmentSourceFolder = folder;
        this.developmentSourceNamespace = namespace;
        return this;
    }

    /**
     * If development sources are used ({@linkplain #developmentSources(Path, String)}, the given folder will
     * automatically be watched for change. This method can be used to disable this behavior.
     */
    public GuideBuilder watchDevelopmentSources(boolean enable) {
        this.watchDevelopmentSources = enable;
        return this;
    }

    /**
     * Adds a page index to this guide, to be updated whenever the pages in the guide change.
     */
    public GuideBuilder index(PageIndex index) {
        this.indices.put(index.getClass(), index);
        return this;
    }

    /**
     * Adds a page index to this guide, to be updated whenever the pages in the guide change. Allows the class token
     * under which the index can be retrieved to be specified.
     */
    public <T extends PageIndex> GuideBuilder index(Class<? super T> clazz, T index) {
        this.indices.put(clazz, index);
        return this;
    }

    /**
     * Adds an extension to the given extension point for this guide.
     */
    public <T extends Extension> GuideBuilder extension(ExtensionPoint<T> extensionPoint, T extension) {
        extensionsBuilder.add(extensionPoint, extension);
        return this;
    }

    /**
     * Configure the generic guide item provided by GuideME. If you are using this code API to register your guide, you
     * are encouraged to register your own guide item instead of using the generic one.
     */
    public GuideBuilder itemSettings(GuideItemSettings settings) {
        this.itemSettings = settings;
        return this;
    }

    /**
     * Creates the guide.
     */
    public MutableGuide build() {
        var extensionCollection = buildExtensions();

        var guide = new MutableGuide(
                id,
                defaultNamespace,
                folder,
                startPage,
                developmentSourceFolder,
                developmentSourceNamespace,
                indices,
                extensionCollection,
                availableToOpenHotkey,
                itemSettings);

        if (developmentSourceFolder != null && watchDevelopmentSources) {
            guide.watchDevelopmentSources();
        }

        if (register) {
            GuideRegistry.registerStatic(guide);
        }

        return guide;
    }

    private ExtensionCollection buildExtensions() {
        var builder = ExtensionCollection.builder();

        if (!disableDefaultExtensions) {
            DefaultExtensions.addAll(builder, disableDefaultsForExtensionPoints);
        }

        builder.addAll(extensionsBuilder);

        return builder.build();
    }

    private static String getSystemPropertyName(ResourceLocation guideId, String property) {
        return String.format(Locale.ROOT, "guideme.%s.%s.%s", guideId.getNamespace(), guideId.getPath(), property);
    }

}
