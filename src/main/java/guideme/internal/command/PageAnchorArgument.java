package guideme.internal.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import guideme.Guides;
import guideme.PageAnchor;
import guideme.compiler.ParsedGuidePage;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * An argument for commands that identifies a registered GuideME guide.
 */
public class PageAnchorArgument implements ArgumentType<PageAnchor> {
    private static final List<String> EXAMPLES = List.of("index.md", "index.md#anchor");
    private static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(
            Component.translatable("argument.resource_or_id.invalid"));

    public static PageAnchorArgument argument() {
        return new PageAnchorArgument();
    }

    public PageAnchor parse(StringReader reader) throws CommandSyntaxException {
        var i = reader.getCursor();
        var pageId = ResourceLocation.read(reader);
        if (hasConsumedWholeArg(reader)) {
            return PageAnchor.page(pageId);
        } else if (reader.peek() == '#') {
            reader.read();
            var fragment = reader.readString();
            if (!hasConsumedWholeArg(reader)) {
                reader.setCursor(i);
                throw ERROR_INVALID.createWithContext(reader);
            }

            return new PageAnchor(pageId, fragment);
        } else {
            reader.setCursor(i);
            throw ERROR_INVALID.createWithContext(reader);
        }
    }

    private static boolean hasConsumedWholeArg(StringReader reader) {
        return !reader.canRead() || reader.peek() == ' ';
    }

    public static PageAnchor getPageAnchor(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, PageAnchor.class);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        var guideId = GuideIdArgument.getGuide(context, "guide");
        var guide = Guides.getById(guideId);
        var ids = guide.getPages().stream()
                .map(ParsedGuidePage::getId)
                .map(ResourceLocation::toString);
        SharedSuggestionProvider.suggest(ids, builder);
        return builder.buildFuture();
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
