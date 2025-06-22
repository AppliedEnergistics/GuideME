package guideme.internal.command;

import com.mojang.brigadier.CommandDispatcher;
import guideme.Guides;
import guideme.internal.GuideMEClient;
import guideme.internal.GuideRegistry;
import guideme.internal.GuidebookText;
import guideme.internal.siteexport.ExportFeedbackSink;
import guideme.internal.siteexport.SiteExporter;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class GuideClientCommand {
    private GuideClientCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var rootCommand = Commands.literal("guidemec");

        rootCommand.then(
                Commands.argument("guide", GuideIdArgument.argument())
                        .then(Commands.literal("export")
                                .executes(context -> {
                                    var guideId = GuideIdArgument.getGuide(context, "guide");
                                    var outputFolder = Minecraft.getInstance().gameDirectory.toPath()
                                            .resolve("guideme_exports").resolve(guideId.toDebugFileName());
                                    try {
                                        Files.createDirectories(outputFolder);
                                    } catch (IOException e) {
                                        context.getSource().sendFailure(Component
                                                .literal("Failed to create output folder for export: " + outputFolder));
                                        return 1;
                                    }

                                    var guide = GuideRegistry.getById(guideId);
                                    if (guide == null) {
                                        context.getSource()
                                                .sendFailure(Component.literal("Couldn't find guide " + guideId));
                                        return 1;
                                    }

                                    new SiteExporter(Minecraft.getInstance(), outputFolder, guide)
                                            .export(new ExportFeedbackSink() {
                                                @Override
                                                public void sendFeedback(Component message) {
                                                    context.getSource().sendSystemMessage(message);
                                                }

                                                @Override
                                                public void sendError(Component message) {
                                                    context.getSource().sendFailure(message);
                                                }
                                            });
                                    return 0;
                                }))
                        .then(Commands.literal("open")
                                .executes(context -> {
                                    var guideId = GuideIdArgument.getGuide(context, "guide");
                                    var guide = Guides.getById(guideId);
                                    if (guide == null) {
                                        context.getSource()
                                                .sendFailure(GuidebookText.ItemInvalidGuideId.text(guideId.toString()));
                                        return 1;
                                    }

                                    GuideMEClient.openGuideAtPreviousPage(guide, guide.getStartPage());
                                    return 0;
                                })
                                .then(
                                        Commands.argument("page", PageAnchorArgument.argument())
                                                .executes(context -> {
                                                    var guideId = GuideIdArgument.getGuide(context, "guide");
                                                    var guide = Guides.getById(guideId);
                                                    var anchor = PageAnchorArgument.getPageAnchor(context, "page");
                                                    GuideMEClient.openGuideAtAnchor(guide, anchor);
                                                    return 0;
                                                }))

                        ));

        dispatcher.register(rootCommand);
    }
}
