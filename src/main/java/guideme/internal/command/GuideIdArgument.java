package guideme.internal.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import guideme.internal.GuideMEProxy;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * An argument for commands that identifies a registered GuideME guide.
 */
public class GuideIdArgument implements ArgumentType<ResourceLocation> {
    private static final List<String> EXAMPLES = List.of("ae2:guide");

    public static GuideIdArgument argument() {
        return new GuideIdArgument();
    }

    public ResourceLocation parse(StringReader reader) throws CommandSyntaxException {
        return ResourceLocation.read(reader);
    }

    public static ResourceLocation getGuide(CommandContext<?> context, String name) {
        return context.getArgument(name, ResourceLocation.class);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        SharedSuggestionProvider.suggestResource(GuideMEProxy.instance().getAvailableGuides(), builder);
        return builder.buildFuture();
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
