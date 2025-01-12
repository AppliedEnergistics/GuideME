package guideme.guidebook;

import guideme.GuideME;
import guideme.guidebook.compiler.PageCompiler;
import guideme.guidebook.compiler.ParsedGuidePage;
import guideme.guidebook.extensions.DefaultExtensions;
import guideme.guidebook.extensions.Extension;
import guideme.guidebook.extensions.ExtensionCollection;
import guideme.guidebook.extensions.ExtensionPoint;
import guideme.guidebook.indices.CategoryIndex;
import guideme.guidebook.indices.ItemIndex;
import guideme.guidebook.indices.PageIndex;
import guideme.guidebook.navigation.NavigationTree;
import guideme.guidebook.screen.GlobalInMemoryHistory;
import guideme.guidebook.screen.GuideScreen;
import guideme.util.Platform;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates a Guide, which consists of a collection of Markdown pages and associated content, loaded from a
 * guide-specific subdirectory of resource packs.
 */
public final class Guide implements PageCollection {
    private static final Logger LOG = LoggerFactory.getLogger(Guide.class);

    private final ResourceLocation id;
    private final String defaultNamespace;
    private final String folder;
    private final Map<ResourceLocation, ParsedGuidePage> developmentPages = new HashMap<>();
    private final Map<Class<?>, PageIndex> indices;
    private NavigationTree navigationTree = new NavigationTree();
    private Map<ResourceLocation, ParsedGuidePage> pages;
    private final ExtensionCollection extensions;
    private final boolean availableToOpenHotkey;

    @Nullable
    private final Path developmentSourceFolder;
    @Nullable
    private final String developmentSourceNamespace;

    private Guide(ResourceLocation id,
            String defaultNamespace,
            String folder,
            @Nullable Path developmentSourceFolder,
            @Nullable String developmentSourceNamespace,
            Map<Class<?>, PageIndex> indices,
            ExtensionCollection extensions,
            boolean availableToOpenHotkey) {
        this.defaultNamespace = defaultNamespace;
        this.folder = folder;
        this.id = id;
        this.developmentSourceFolder = developmentSourceFolder;
        this.developmentSourceNamespace = developmentSourceNamespace;
        this.indices = indices;
        this.extensions = extensions;
        this.availableToOpenHotkey = availableToOpenHotkey;
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    @Override
    public <T extends PageIndex> T getIndex(Class<T> indexClass) {
        var index = indices.get(indexClass);
        if (index == null) {
            throw new IllegalArgumentException("No index of type " + indexClass + " is registered with this guide.");
        }
        return indexClass.cast(index);
    }

    public static Builder builder(IEventBus modEventBus, ResourceLocation id) {
        return new Builder(modEventBus, id);
    }

    /**
     * Creates a build without listening for mod events. This disables all features that rely on being able to register
     * mod events, such as {@linkplain Builder#registerReloadListener the reload listener},
     * {@linkplain Builder#validateAllAtStartup validation} or the {@linkplain Builder#startupPage startup page}.
     */
    public static Builder builder(ResourceLocation id) {
        return new Builder(null, id)
                .registerReloadListener(false)
                .validateAllAtStartup(false)
                .startupPage(null);
    }

    private static CompletableFuture<Minecraft> afterClientStart(IEventBus modEventBus) {
        var future = new CompletableFuture<Minecraft>();

        modEventBus.addListener((FMLClientSetupEvent evt) -> {
            var client = Minecraft.getInstance();
            CompletableFuture<?> reload;

            if (client.getOverlay() instanceof LoadingOverlay loadingOverlay) {
                reload = loadingOverlay.reload.done();
            } else {
                reload = CompletableFuture.completedFuture(null);
            }

            reload.whenCompleteAsync((o, throwable) -> {
                if (throwable != null) {
                    future.completeExceptionally(throwable);
                } else {
                    future.complete(client);
                }
            }, client);
        });

        return future;
    }

    // Run a fake datapack reload to properly compile the page (Recipes, Tags, etc.)
    // Only used when we try to compile pages before entering a world (validation, show on startup)
    public static void runDatapackReload() {
        try {
            var layeredAccess = RegistryLayer.createRegistryAccess();

            PackRepository packRepository = new PackRepository(
                    new ServerPacksSource(new DirectoryValidator(path -> false)));
            // This fires AddPackFindersEvent but it's probably ok.
            ResourcePackLoader.populatePackRepository(packRepository, PackType.SERVER_DATA, true);
            packRepository.reload();
            packRepository.setSelected(packRepository.getAvailableIds());

            var resourceManager = new MultiPackResourceManager(PackType.SERVER_DATA,
                    packRepository.openAllSelected());

            var worldgenLayer = RegistryDataLoader.load(
                    resourceManager,
                    layeredAccess.getAccessForLoading(RegistryLayer.WORLDGEN),
                    RegistryDataLoader.WORLDGEN_REGISTRIES);
            layeredAccess = layeredAccess.replaceFrom(RegistryLayer.WORLDGEN, worldgenLayer);

            var stuff = ReloadableServerResources.loadResources(
                    resourceManager,
                    layeredAccess,
                    FeatureFlagSet.of(),
                    Commands.CommandSelection.ALL,
                    0,
                    Util.backgroundExecutor(),
                    command -> {
                        try {
                            command.run();
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw e;
                        }
                    }).get();
            stuff.updateRegistryTags();
            Platform.fallbackClientRecipeManager = stuff.getRecipeManager();
            Platform.fallbackClientRegistryAccess = layeredAccess.compositeAccess();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Nullable
    public ParsedGuidePage getParsedPage(ResourceLocation id) {
        if (pages == null) {
            LOG.warn("Can't get page {}. Pages not loaded yet.", id);
            return null;
        }

        return developmentPages.getOrDefault(id, pages.get(id));
    }

    @Override
    @Nullable
    public GuidePage getPage(ResourceLocation id) {
        var page = getParsedPage(id);

        return page != null ? PageCompiler.compile(this, extensions, page) : null;
    }

    public Collection<ParsedGuidePage> getPages() {
        if (pages == null) {
            throw new IllegalStateException("Pages are not loaded yet.");
        }

        var pages = new HashMap<>(this.pages);
        pages.putAll(developmentPages);
        return pages.values();
    }

    @Override
    public byte[] loadAsset(ResourceLocation id) {
        // Also load images from the development sources folder, if it exists and contains the asset namespace
        if (developmentSourceFolder != null && id.getNamespace().equals(developmentSourceNamespace)) {
            var path = developmentSourceFolder.resolve(id.getPath());
            try (var in = Files.newInputStream(path)) {
                return in.readAllBytes();
            } catch (FileNotFoundException ignored) {
            } catch (IOException e) {
                LOG.error("Failed to open guidebook asset {}", path);
                return null;
            }
        }

        // Transform id such that the path is prefixed with "ae2assets", the source folder for the guidebook assets
        id = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), folder + "/" + id.getPath());

        var resource = Minecraft.getInstance().getResourceManager().getResource(id).orElse(null);
        if (resource == null) {
            return null;
        }
        try (var input = resource.open()) {
            return input.readAllBytes();
        } catch (IOException e) {
            LOG.error("Failed to open guidebook asset {}", id);
            return null;
        }
    }

    @Override
    public NavigationTree getNavigationTree() {
        return navigationTree;
    }

    /**
     * @return True if this guide should be considered for use in the global open guide hotkey.
     */
    public boolean isAvailableToOpenHotkey() {
        return availableToOpenHotkey;
    }

    @Override
    public boolean pageExists(ResourceLocation pageId) {
        return developmentPages.containsKey(pageId) || pages != null && pages.containsKey(pageId);
    }

    /**
     * Returns the on-disk path for a given guidebook resource (i.e. page, asset) if development mode is enabled and the
     * resource exists in the development source folder.
     *
     * @return null if development mode is not enabled or the resource doesn't exist in the development sources.
     */
    @Nullable
    public Path getDevelopmentSourcePath(ResourceLocation id) {
        if (developmentSourceFolder != null && id.getNamespace().equals(developmentSourceNamespace)) {
            var path = developmentSourceFolder.resolve(id.getPath());
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    @Nullable
    public Path getDevelopmentSourceFolder() {
        return developmentSourceFolder;
    }

    public ExtensionCollection getExtensions() {
        return extensions;
    }

    private class ReloadListener extends SimplePreparableReloadListener<Map<ResourceLocation, ParsedGuidePage>> {
        private final ResourceLocation id;

        public ReloadListener(ResourceLocation id) {
            this.id = id;
        }

        @Override
        protected Map<ResourceLocation, ParsedGuidePage> prepare(ResourceManager resourceManager,
                ProfilerFiller profiler) {
            profiler.startTick();
            Map<ResourceLocation, ParsedGuidePage> pages = new HashMap<>();

            var resources = resourceManager.listResources(folder,
                    location -> location.getPath().endsWith(".md"));

            for (var entry : resources.entrySet()) {
                var pageId = ResourceLocation.fromNamespaceAndPath(
                        entry.getKey().getNamespace(),
                        entry.getKey().getPath().substring((folder + "/").length()));

                String sourcePackId = entry.getValue().sourcePackId();
                try (var in = entry.getValue().open()) {
                    pages.put(pageId, PageCompiler.parse(sourcePackId, pageId, in));
                } catch (IOException e) {
                    LOG.error("Failed to load guidebook page {} from pack {}", pageId, sourcePackId, e);
                }
            }

            profiler.endTick();
            return pages;
        }

        @Override
        protected void apply(Map<ResourceLocation, ParsedGuidePage> pages, ResourceManager resourceManager,
                ProfilerFiller profiler) {
            profiler.startTick();
            Guide.this.pages = pages;
            profiler.push("indices");
            var allPages = new ArrayList<ParsedGuidePage>();
            allPages.addAll(pages.values());
            allPages.addAll(developmentPages.values());
            for (var index : indices.values()) {
                index.rebuild(allPages);
            }
            profiler.pop();
            profiler.push("navigation");
            navigationTree = buildNavigation();
            profiler.pop();
            profiler.endTick();
        }

        @Override
        public String getName() {
            return id.toString();
        }
    }

    private void watchDevelopmentSources() {
        var watcher = new GuideSourceWatcher(developmentSourceNamespace, developmentSourceFolder);

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre evt) -> {
            var changes = watcher.takeChanges();
            if (!changes.isEmpty()) {
                applyChanges(changes);
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(watcher::close));
        for (var page : watcher.loadAll()) {
            developmentPages.put(page.getId(), page);
        }
    }

    private void applyChanges(List<GuidePageChange> changes) {
        // Enrich each change with the previous page data while we process them
        for (int i = 0; i < changes.size(); i++) {
            var change = changes.get(i);
            var pageId = change.pageId();

            var oldPage = change.newPage() != null ? developmentPages.put(pageId, change.newPage())
                    : developmentPages.remove(pageId);
            changes.set(i, new GuidePageChange(pageId, oldPage, change.newPage()));
        }

        // Allow indices to rebuild
        var allPages = new ArrayList<ParsedGuidePage>(pages.size() + developmentPages.size());
        allPages.addAll(pages.values());
        allPages.addAll(developmentPages.values());
        for (var index : indices.values()) {
            if (index.supportsUpdate()) {
                index.update(allPages, changes);
            } else {
                index.rebuild(allPages);
            }
        }

        // Rebuild navigation
        this.navigationTree = buildNavigation();

        // Reload the current page if it has been changed
        if (Minecraft.getInstance().screen instanceof GuideScreen guideScreen) {
            var currentPageId = guideScreen.getCurrentPageId();
            if (changes.stream().anyMatch(c -> c.pageId().equals(currentPageId))) {
                guideScreen.reloadPage();
            }
        }
    }

    private NavigationTree buildNavigation() {
        if (developmentPages.isEmpty()) {
            return NavigationTree.build(pages.values());
        } else {
            var allPages = new HashMap<>(pages);
            allPages.putAll(developmentPages);
            return NavigationTree.build(allPages.values());
        }
    }

    private static String getSystemPropertyName(ResourceLocation guideId, String property) {
        return String.format(Locale.ROOT, "guideDev.%s.%s.%s", guideId.getNamespace(), guideId.getPath(), property);
    }

    public static class Builder {
        @Nullable
        private final IEventBus modEventBus;
        private final ResourceLocation id;
        private final Map<Class<?>, PageIndex> indices = new IdentityHashMap<>();
        private final ExtensionCollection.Builder extensionsBuilder = ExtensionCollection.builder();
        private boolean registerReloadListener = true;
        private String defaultNamespace;
        private String folder;
        @Nullable
        private ResourceLocation startupPage;
        private boolean validateAtStartup;
        private Path developmentSourceFolder;
        private String developmentSourceNamespace;
        private boolean watchDevelopmentSources = true;
        private boolean disableDefaultExtensions = false;
        private boolean availableToOpenHotkey = true;
        private final Set<ExtensionPoint<?>> disableDefaultsForExtensionPoints = Collections
                .newSetFromMap(new IdentityHashMap<>());

        private Builder(@Nullable IEventBus modEventBus, ResourceLocation id) {
            this.modEventBus = modEventBus;
            this.id = Objects.requireNonNull(id, "id");
            this.defaultNamespace = id.getNamespace();
            this.folder = id.getPath();

            var startupPageProperty = getSystemPropertyName(id, "startupPage");
            try {
                var startupPageIdText = System.getProperty(startupPageProperty);
                if (startupPageIdText != null) {
                    this.startupPage = ResourceLocation.parse(startupPageIdText);
                }
            } catch (Exception e) {
                LOG.error("Specified invalid page id in system property {}", startupPageProperty);
            }

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
         * Allows the automatic resource reload listener registration to be disabled.
         */
        public Builder registerReloadListener(boolean enable) {
            this.registerReloadListener = enable;
            return this;
        }

        /**
         * Sets the default resource namespace for this guide. This namespace is used for resources loaded from a plain
         * folder during development and defaults to the namespace of the guide id.
         */
        public Builder defaultNamespace(String defaultNamespace) {
            // Both folder and default namespace need to be valid resource paths
            if (!ResourceLocation.isValidNamespace(defaultNamespace)) {
                throw new IllegalArgumentException("The default namespace for a guide needs to be a valid namespace");
            }
            this.defaultNamespace = defaultNamespace;
            return this;
        }

        /**
         * Sets the folder within the resource pack, from which pages for this guide will be loaded. Please note that
         * this name must be unique across all namespaces, since it would otherwise cause pages from guides added by
         * other mods to show up in yours.
         * <p/>
         * This defaults to {@code guides/<namespace>/<path>} with namespace and path coming from the guide id, which
         * should implicitly make it unique.
         */
        public Builder folder(String folder) {
            if (!ResourceLocation.isValidPath(folder)) {
                throw new IllegalArgumentException("The folder for a guide needs to be a valid resource location");
            }
            this.folder = folder;
            return this;
        }

        /**
         * Stops the builder from adding any of the default extensions. Use
         * {@link #disableDefaultExtensions(ExtensionPoint)} to disable the default extensions only for one of the
         * extension points.
         */
        public Builder disableDefaultExtensions() {
            this.disableDefaultExtensions = true;
            return this;
        }

        /**
         * Disables the global open hotkey from using this guide.
         */
        public Builder disableOpenHotkey() {
            this.availableToOpenHotkey = false;
            return this;
        }

        /**
         * Stops the builder from adding any of the default extensions to the given extension point.
         * {@link #disableDefaultExtensions()} takes precedence and will disable all extension points.
         */
        public Builder disableDefaultExtensions(ExtensionPoint<?> extensionPoint) {
            this.disableDefaultsForExtensionPoints.add(extensionPoint);
            return this;
        }

        /**
         * Sets the page that should be shown directly after launching the client. This will cause a datapack reload to
         * happen automatically so that recipes can be shown. This page can also be set via system property
         * <code>guideDev.&lt;FOLDER>.startupPage</code>, where &lt;FOLDER> is replaced with the folder given to
         * {@link Guide#builder}.
         * <p/>
         * Setting the page to null will disable this feature and overwrite a page set via system properties.
         */
        public Builder startupPage(@Nullable ResourceLocation pageId) {
            this.startupPage = pageId;
            return this;
        }

        /**
         * Enables or disables validation of all discovered pages at startup. This will cause a datapack reload to
         * happen automatically so that recipes can be validated. This page can also be set via system property
         * <code>guideDev.&lt;FOLDER>.validateAtStartup</code>, where &lt;FOLDER> is replaced with the folder given to
         * {@link Guide#builder}.
         * <p/>
         * Changing this setting overrides the system property.
         * <p/>
         * Validation results will be written to the log.
         */
        public Builder validateAllAtStartup(boolean enable) {
            this.validateAtStartup = enable;
            return this;
        }

        /**
         * See {@linkplain #developmentSources(Path, String)}. Uses the default namespace of the guide as the namespace
         * for the pages and resources in the folder.
         */
        public Builder developmentSources(@Nullable Path folder) {
            return developmentSources(folder, defaultNamespace);
        }

        /**
         * Load additional page resources and assets from the given folder. Useful during development in conjunction
         * with {@link #watchDevelopmentSources} to automatically reload pages during development.
         * <p/>
         * All resources in the given folder are treated as if they were in the given namespace and the folder given to
         * {@link #builder}.
         * <p/>
         * The default values for folder and namespace will be taken from the system properties:
         * <ul>
         * <li><code>guideDev.&lt;FOLDER>.sources</code></li>
         * <li><code>guideDev.&lt;FOLDER>.sourcesNamespace</code></li>
         * </ul>
         */
        public Builder developmentSources(Path folder, String namespace) {
            this.developmentSourceFolder = folder;
            this.developmentSourceNamespace = namespace;
            return this;
        }

        /**
         * If development sources are used ({@linkplain #developmentSources(Path, String)}, the given folder will
         * automatically be watched for change. This method can be used to disable this behavior.
         */
        public Builder watchDevelopmentSources(boolean enable) {
            this.watchDevelopmentSources = enable;
            return this;
        }

        /**
         * Adds a page index to this guide, to be updated whenever the pages in the guide change.
         */
        public Builder index(PageIndex index) {
            this.indices.put(index.getClass(), index);
            return this;
        }

        /**
         * Adds a page index to this guide, to be updated whenever the pages in the guide change. Allows the class token
         * under which the index can be retrieved to be specified.
         */
        public <T extends PageIndex> Builder index(Class<? super T> clazz, T index) {
            this.indices.put(clazz, index);
            return this;
        }

        /**
         * Adds an extension to the given extension point for this guide.
         */
        public <T extends Extension> Builder extension(ExtensionPoint<T> extensionPoint, T extension) {
            extensionsBuilder.add(extensionPoint, extension);
            return this;
        }

        /**
         * Creates the guide.
         */
        public Guide build() {
            var extensionCollection = buildExtensions();

            var guide = new Guide(id, defaultNamespace, folder, developmentSourceFolder, developmentSourceNamespace,
                    indices, extensionCollection, availableToOpenHotkey);

            if (registerReloadListener) {
                if (this.modEventBus == null) {
                    throw new IllegalStateException(
                            "Cannot register the reload listener, since no mod event bus was supplied to the builder.");
                }
                guide.registerReloadListener(modEventBus);
            }

            if (developmentSourceFolder != null && watchDevelopmentSources) {
                guide.watchDevelopmentSources();
            }

            if (validateAtStartup || startupPage != null) {
                if (this.modEventBus == null) {
                    throw new IllegalStateException(
                            "Cannot enable the startup page/validation, since no mod event bus was supplied to the builder.");
                }
                var guideOpenedOnce = new MutableBoolean(false);
                NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening e) -> {
                    if (e.getNewScreen() instanceof TitleScreen && !guideOpenedOnce.booleanValue()) {
                        guideOpenedOnce.setTrue();
                        runDatapackReload();
                        if (validateAtStartup) {
                            guide.validateAll();
                        }
                        if (startupPage != null) {
                            e.setNewScreen(GuideScreen.openNew(guide, PageAnchor.page(startupPage),
                                    GlobalInMemoryHistory.INSTANCE));
                        }
                    }
                });
            }

            if (availableToOpenHotkey && this.modEventBus == null) {
                throw new IllegalStateException(
                        "This guide will be unavailable to the open hotkey without providing a modbus for registration");
            }

            if (this.modEventBus != null) {
                this.modEventBus.addListener((RegisterEvent e) -> {
                    if (e.getRegistryKey() == GuideME.GUIDES_REGISTRY) {
                        Registry.register(GuideME.GUIDES, id, guide);
                    }
                });
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
    }

    private void validateAll() {
        // Iterate and compile all pages to warn about errors on startup
        for (var entry : developmentPages.entrySet()) {
            LOG.info("Compiling {}", entry.getKey());
            getPage(entry.getKey());
        }
    }

    private void registerReloadListener(IEventBus modEventBus) {
        modEventBus.addListener((RegisterClientReloadListenersEvent evt) -> {
            evt.registerReloadListener(
                    new ReloadListener(ResourceLocation.fromNamespaceAndPath(defaultNamespace, folder)));
        });
    }
}
