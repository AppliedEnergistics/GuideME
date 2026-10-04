package guideme.internal.command;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import guideme.internal.GuideRegistry;
import guideme.internal.GuidebookText;
import guideme.internal.MutableGuide;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements commands that help with the workflow to create and edit structures for use in the guidebook. The commands
 * will not be used directly by users, but rather by command blocks built by
 * {@link appeng.server.testplots.GuidebookPlot}.
 */
public final class StructureCommands {

    private static final Logger LOG = LoggerFactory.getLogger(StructureCommands.class);

    private StructureCommands() {
    }

    @Nullable
    private static String lastOpenedOrSavedPath;

    private static final String FILE_PATTERNS = "snbt;nbt";

    private static final String FILE_PATTERN_DESC = "Structure NBT Files (*.snbt, *.nbt)";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var rootCommand = Commands.literal("guideme");

        registerPlaceAllStructures(rootCommand);

        registerImportCommand(rootCommand);

        registerExportCommand(rootCommand);

        dispatcher.register(rootCommand);
    }

    @Nullable
    private static ServerLevel getIntegratedServerLevel(CommandContext<CommandSourceStack> context) {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.hasSingleplayerServer()) {
            context.getSource().sendFailure(GuidebookText.CommandOnlyWorksInSinglePlayer.text());
            return null;
        }
        return minecraft.getSingleplayerServer().getLevel(
                Minecraft.getInstance().player.level().dimension());
    }

    private static void registerPlaceAllStructures(LiteralArgumentBuilder<CommandSourceStack> rootCommand) {
        LiteralArgumentBuilder<CommandSourceStack> subcommand = literal("placeallstructures");
        // Only usable on singleplayer worlds and only by the local player (in case it is opened to LAN)
        subcommand = subcommand.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        subcommand.then(Commands.argument("origin", BlockPosArgument.blockPos())
                .executes(context -> {
                    var level = getIntegratedServerLevel(context);
                    if (level == null) {
                        return 1;
                    }

                    var origin = BlockPosArgument.getBlockPos(context, "origin");
                    placeAllStructures(context.getSource(), level, origin);
                    return 0;
                }));

        subcommand
                .then(Commands.argument("origin", BlockPosArgument.blockPos())
                        .then(Commands.argument("guide", GuideIdArgument.argument())
                                .executes(context -> {
                                    var level = getIntegratedServerLevel(context);
                                    if (level == null) {
                                        return 1;
                                    }

                                    var guideId = GuideIdArgument.getGuide(context, "guide");
                                    var guide = GuideRegistry.getById(guideId);
                                    if (guide == null) {
                                        return 1;
                                    }

                                    var origin = BlockPosArgument.getBlockPos(context, "origin");
                                    placeAllStructures(context.getSource(), level, new MutableObject<>(origin), guide);
                                    return 0;
                                })));

        rootCommand.then(subcommand);
    }

    private static void registerImportCommand(LiteralArgumentBuilder<CommandSourceStack> rootCommand) {
        LiteralArgumentBuilder<CommandSourceStack> importSubcommand = literal("importstructure");
        // Only usable on singleplayer worlds and only by the local player (in case it is opened to LAN)
        importSubcommand
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("origin", BlockPosArgument.blockPos())
                        .executes(context -> {
                            var level = getIntegratedServerLevel(context);
                            if (level == null) {
                                return 1;
                            }

                            var origin = BlockPosArgument.getBlockPos(context, "origin");
                            importStructure(context.getSource(), getIntegratedServerLevel(context), origin);
                            return 0;
                        }));
        rootCommand.then(importSubcommand);
    }

    private static void placeAllStructures(CommandSourceStack source, ServerLevel level, BlockPos origin) {

        var currentPos = new MutableObject<>(origin);

        for (var guide : GuideRegistry.getAll()) {
            placeAllStructures(source, level, currentPos, guide);
        }

    }

    private static void placeAllStructures(CommandSourceStack source, ServerLevel level, MutableObject<BlockPos> origin,
            MutableGuide guide) {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }

        var sourceFolder = guide.getDevelopmentSourceFolder();

        List<Pair<String, Supplier<String>>> structures = new ArrayList<>();
        if (sourceFolder == null) {
            var resourceManager = Minecraft.getInstance().getResourceManager();
            var resources = resourceManager.listResources(
                    guide.getContentRootFolder(),
                    location -> location.getPath().endsWith(".snbt"));
            for (var entry : resources.entrySet()) {
                structures.add(Pair.of(entry.getKey().toString(), () -> {
                    try (var in = entry.getValue().open()) {
                        return new String(in.readAllBytes());
                    } catch (IOException e) {
                        LOG.error("Failed to read structure {}", entry.getKey(), e);
                        return null;
                    }
                }));
            }
        } else {
            try (var s = Files.walk(sourceFolder)
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".snbt"))) {
                s.forEach(path -> {
                    structures.add(Pair.of(
                            path.toString(),
                            () -> {
                                try {
                                    return Files.readString(path);
                                } catch (IOException e) {
                                    LOG.error("Failed to read structure {}", path, e);
                                    return null;
                                }
                            }));
                });
            } catch (IOException e) {
                LOG.error("Failed to find all structures.", e);
                source.sendFailure(Component.literal(e.toString()));
                return;
            }
        }

        for (var pair : structures) {
            var snbtFile = pair.getLeft();
            var contentSupplier = pair.getRight();
            LOG.info("Placing {}", snbtFile);
            try {
                CompoundTag compound;
                var textInFile = contentSupplier.get();
                if (textInFile == null) {
                    continue;
                }
                compound = NbtUtils.snbtToStructure(textInFile);

                var structure = readStructure(level, compound);
                var pos = origin.getValue();
                if (!structure.placeInWorld(
                        level,
                        pos,
                        pos,
                        new StructurePlaceSettings(),
                        new SingleThreadedRandomSource(0L),
                        Block.UPDATE_CLIENTS)) {
                    source.sendFailure(Component.literal("Failed to place " + snbtFile));
                }

                origin.setValue(origin.getValue().offset(structure.getSize().getX() + 2, 0, 0));
            } catch (Exception e) {
                LOG.error("Failed to place {}.", snbtFile, e);
                source.sendFailure(Component.literal("Failed to place " + snbtFile + ": " + e));
            }
        }

        source.sendSuccess(() -> Component.literal("Placed " + structures.size() + " structures"), true);
    }

    private static StructureTemplate readStructure(ServerLevel level, CompoundTag tag) {
        HolderGetter<Block> blockLookup = level.registryAccess()
                .lookupOrThrow(Registries.BLOCK)
                .filterFeatures(level.enabledFeatures());

        StructureTemplate structureTemplate = new StructureTemplate();
        int version = NbtUtils.getDataVersion(tag, 500);
        structureTemplate.load(blockLookup,
                DataFixTypes.STRUCTURE.updateToCurrentVersion(level.getServer().getFixerUpper(), tag, version));
        return structureTemplate;
    }

    private static void importStructure(CommandSourceStack source, ServerLevel level, BlockPos origin) {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }

        CompletableFuture
                .supplyAsync(StructureCommands::pickFileForOpen, minecraft)
                .thenCompose(f -> f)
                .thenApplyAsync(selectedPath -> {
                    if (selectedPath == null) {
                        return null;
                    }

                    lastOpenedOrSavedPath = selectedPath; // remember for save dialog
                    try {
                        if (placeStructure(level, origin, selectedPath)) {
                            source.sendSuccess(() -> Component.literal("Placed structure"), true);
                        } else {
                            source.sendFailure(Component.literal("Failed to place structure"));
                        }
                    } catch (Exception e) {
                        LOG.error("Failed to place structure.", e);
                        source.sendFailure(Component.literal(e.toString()));
                    }

                    return null;
                }, server)
                .thenRunAsync(() -> {
                    if (minecraft.gui.screen() instanceof PauseScreen) {
                        minecraft.gui.setScreen(null);
                    }
                }, minecraft);
    }

    private static boolean placeStructure(ServerLevel level,
            BlockPos origin,
            String structurePath) throws CommandSyntaxException, IOException {
        CompoundTag compound;
        if (structurePath.toLowerCase(Locale.ROOT).endsWith(".snbt")) {
            var textInFile = Files.readString(Paths.get(structurePath), StandardCharsets.UTF_8);
            compound = NbtUtils.snbtToStructure(textInFile);
        } else {
            try (var is = new BufferedInputStream(new FileInputStream(structurePath))) {
                compound = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
            }
        }
        var structure = readStructure(level, compound);
        return structure.placeInWorld(
                level,
                origin,
                origin,
                new StructurePlaceSettings(),
                new SingleThreadedRandomSource(0L),
                Block.UPDATE_CLIENTS);
    }

    private static void registerExportCommand(LiteralArgumentBuilder<CommandSourceStack> rootCommand) {
        LiteralArgumentBuilder<CommandSourceStack> exportSubcommand = literal("exportstructure");
        // Only usable on singleplayer worlds and only by the local player (in case it is opened to LAN)
        exportSubcommand
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("origin", BlockPosArgument.blockPos())
                        .then(Commands.argument("sizeX", IntegerArgumentType.integer(1))
                                .then(Commands.argument("sizeY", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("sizeZ", IntegerArgumentType.integer(1))
                                                .executes(context -> {
                                                    var level = getIntegratedServerLevel(context);
                                                    if (level == null) {
                                                        return 1;
                                                    }

                                                    var origin = BlockPosArgument.getBlockPos(context, "origin");
                                                    var sizeX = IntegerArgumentType.getInteger(context, "sizeX");
                                                    var sizeY = IntegerArgumentType.getInteger(context, "sizeY");
                                                    var sizeZ = IntegerArgumentType.getInteger(context, "sizeZ");
                                                    var size = new Vec3i(sizeX, sizeY, sizeZ);
                                                    exportStructure(context.getSource(), level, origin, size);
                                                    return 0;
                                                })))));
        rootCommand.then(exportSubcommand);
    }

    private static void exportStructure(CommandSourceStack source, ServerLevel level, BlockPos origin, Vec3i size) {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        var player = minecraft.player;
        if (server == null || player == null) {
            return;
        }

        CompletableFuture
                .supplyAsync(StructureCommands::pickFileForSave, minecraft)
                .thenCompose(f -> f)
                .thenApplyAsync(selectedPath -> {
                    if (selectedPath == null) {
                        return null;
                    }

                    try {
                        // Find the smallest box containing the placed blocks
                        var to = BlockPos
                                .betweenClosedStream(origin,
                                        origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))
                                .filter(pos -> !level.getBlockState(pos).isAir())
                                .reduce(
                                        origin,
                                        (blockPos, blockPos2) -> new BlockPos(
                                                Math.max(blockPos.getX(), blockPos2.getX()),
                                                Math.max(blockPos.getY(), blockPos2.getY()),
                                                Math.max(blockPos.getZ(), blockPos2.getZ())));
                        var actualSize = new BlockPos(
                                1 + to.getX() - origin.getX(),
                                1 + to.getY() - origin.getY(),
                                1 + to.getZ() - origin.getZ());

                        var structureTemplate = new StructureTemplate();
                        structureTemplate.fillFromWorld(
                                level,
                                origin,
                                actualSize,
                                false,
                                List.of());

                        var compound = structureTemplate.save(new CompoundTag());
                        if (selectedPath.toLowerCase(Locale.ROOT).endsWith(".snbt")) {
                            Files.writeString(
                                    Paths.get(selectedPath),
                                    NbtUtils.structureToSnbt(compound),
                                    StandardCharsets.UTF_8);
                        } else {
                            NbtIo.writeCompressed(compound, Paths.get(selectedPath));
                        }

                        source.sendSuccess(() -> Component.literal("Saved structure"), true);
                    } catch (IOException e) {
                        LOG.error("Failed to save structure.", e);
                        source.sendFailure(Component.literal(e.toString()));
                    }

                    return null;
                }, server)
                .thenRunAsync(() -> {
                    if (minecraft.gui.screen() instanceof PauseScreen) {
                        minecraft.gui.setScreen(null);
                    }
                }, minecraft);
    }

    private static CompletableFuture<String> pickFileForOpen() {
        return showFileDialog(false);
    }

    private static CompletableFuture<String> pickFileForSave() {
        return showFileDialog(true);
    }

    /**
     * SDL file dialogs are asynchronous. The filter and callback have to stay alive until the callback is invoked,
     * which may happen on a different thread.
     */
    private static CompletableFuture<String> showFileDialog(boolean save) {
        setDefaultFolder();

        var result = new CompletableFuture<String>();
        var name = MemoryUtil.memUTF8(FILE_PATTERN_DESC);
        var pattern = MemoryUtil.memUTF8(FILE_PATTERNS);
        var filters = SDL_DialogFileFilter.calloc(1);
        filters.get(0).name(name).pattern(pattern);

        var callbackHolder = new SDL_DialogFileCallback[1];
        callbackHolder[0] = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
            try {
                String selectedPath = null;
                if (filelist != MemoryUtil.NULL) {
                    var firstFile = MemoryUtil.memGetAddress(filelist);
                    if (firstFile != MemoryUtil.NULL) {
                        selectedPath = MemoryUtil.memUTF8(firstFile);
                    }
                }
                result.complete(selectedPath);
            } catch (Throwable e) {
                result.completeExceptionally(e);
            } finally {
                filters.free();
                MemoryUtil.memFree(name);
                MemoryUtil.memFree(pattern);
                callbackHolder[0].free();
            }
        });

        var window = Minecraft.getInstance().getWindow().handle();
        if (save) {
            SDLDialog.SDL_ShowSaveFileDialog(callbackHolder[0], MemoryUtil.NULL, window, filters,
                    lastOpenedOrSavedPath);
        } else {
            SDLDialog.SDL_ShowOpenFileDialog(callbackHolder[0], MemoryUtil.NULL, window, filters,
                    lastOpenedOrSavedPath, false);
        }
        return result;
    }

    private static void setDefaultFolder() {
        // If any guide has development sources, default to that folder
        if (lastOpenedOrSavedPath == null) {
            for (var guide : GuideRegistry.getAll()) {
                if (guide.getDevelopmentSourceFolder() != null) {
                    lastOpenedOrSavedPath = guide.getDevelopmentSourceFolder().toString();
                    if (!lastOpenedOrSavedPath.endsWith("/") && !lastOpenedOrSavedPath.endsWith("\\")) {
                        lastOpenedOrSavedPath += File.separator;
                    }
                    break;
                }
            }
        }
    }
}
