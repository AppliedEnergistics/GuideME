package guideme.libs.micromark.extensions.gfmstrikethrough;

import guideme.libs.micromark.Assert;
import guideme.libs.micromark.ClassifyCharacter;
import guideme.libs.micromark.Construct;
import guideme.libs.micromark.Extension;
import guideme.libs.micromark.ListUtils;
import guideme.libs.micromark.State;
import guideme.libs.micromark.Token;
import guideme.libs.micromark.TokenProperty;
import guideme.libs.micromark.TokenizeContext;
import guideme.libs.micromark.Tokenizer;
import guideme.libs.micromark.Types;
import guideme.libs.micromark.symbol.Codes;
import guideme.libs.micromark.symbol.Constants;
import java.util.ArrayList;
import java.util.List;

public class GfmStrikethroughSyntax extends Extension {
    public static final Extension INSTANCE = new GfmStrikethroughSyntax();
    public static final TokenProperty<Boolean> OPEN = new TokenProperty<>();
    public static final TokenProperty<Boolean> CLOSE = new TokenProperty<>();
    private static final String TYPE_STRIKETHROUGH_SEQUENCE_TEMPORARY = "strikethroughSequenceTemporary";
    static final String TOKEN = "strikethroughSequence";

    private final boolean singleTilde;

    public GfmStrikethroughSyntax() {
        this(Options.DEFAULT);
    }

    public GfmStrikethroughSyntax(Options options) {
        this.singleTilde = options.singleTilde();

        var tokenizer = new Construct();
        tokenizer.name = "strikethrough";
        tokenizer.tokenize = this::tokenizeStrikethrough;
        tokenizer.resolveAll = this::resolveAllStrikethrough;

        // Set up the extension configuration
        text.put(Codes.tilde, List.of(tokenizer));
        nullInsideSpan.add(tokenizer.resolveAll);
        nullAttentionMarkers.add(Codes.tilde);
    }

    private List<Tokenizer.Event> resolveAllStrikethrough(List<Tokenizer.Event> events, TokenizeContext context) {
        int index = -1;

        // Walk through all events
        while (++index < events.size()) {
            // Find a token that can close
            var event = events.get(index);
            var token = event.token();

            if (event.isEnter() &&
                    token.type.equals(TYPE_STRIKETHROUGH_SEQUENCE_TEMPORARY) &&
                    Boolean.TRUE.equals(token.get(CLOSE))) {

                int open = index;

                // Walk back to find an opener
                while (open-- > 0) {
                    var openEvent = events.get(open);
                    var openToken = openEvent.token();

                    if (openEvent.isExit() &&
                            openToken.type.equals(TYPE_STRIKETHROUGH_SEQUENCE_TEMPORARY) &&
                            Boolean.TRUE.equals(openToken.get(OPEN)) &&
                            // If the sizes are the same
                            token.size() == openToken.size()) {

                        events.get(index).token().type = TOKEN;
                        events.get(open).token().type = TOKEN;

                        var strikethrough = new Token();
                        strikethrough.type = "strikethrough";
                        strikethrough.start = openToken.start;
                        strikethrough.end = token.end;

                        var text = new Token();
                        text.type = "strikethroughText";
                        text.start = openToken.end;
                        text.end = token.start;

                        // Create next events array
                        List<Tokenizer.Event> nextEvents = new ArrayList<>();
                        nextEvents.add(Tokenizer.Event.enter(strikethrough, context));
                        nextEvents.add(Tokenizer.Event.enter(openToken, context));
                        nextEvents.add(Tokenizer.Event.exit(openToken, context));
                        nextEvents.add(Tokenizer.Event.enter(text, context));

                        // Handle inside span
                        var insideSpan = context.getParser().constructs.nullInsideSpan;
                        if (insideSpan != null) {
                            nextEvents.addAll(Construct.resolveAll(
                                    insideSpan,
                                    ListUtils.slice(events, open + 1, index),
                                    context));
                        }

                        // Add closing events
                        nextEvents.add(Tokenizer.Event.exit(text, context));
                        nextEvents.add(Tokenizer.Event.enter(token, context));
                        nextEvents.add(Tokenizer.Event.exit(token, context));
                        nextEvents.add(Tokenizer.Event.exit(strikethrough, context));

                        // Replace events
                        ListUtils.splice(events, open - 1, index - open + 3, nextEvents);

                        index = open + nextEvents.size() - 2;
                        break;
                    }
                }
            }
        }

        // Convert remaining temporary sequences to data
        for (var event : events) {
            if (event.token().type.equals(TYPE_STRIKETHROUGH_SEQUENCE_TEMPORARY)) {
                event.token().type = Types.data;
            }
        }

        return events;
    }

    private State tokenizeStrikethrough(TokenizeContext context, Tokenizer.Effects effects, State ok, State nok) {
        class StateMachine {
            final int previous = context.getPrevious();
            final List<Tokenizer.Event> events = context.getEvents();
            int size = 0;

            State start(int code) {
                Assert.check(code == Codes.tilde, "expected `~`");

                if (previous == Codes.tilde &&
                        !events.getLast().token().type.equals(Types.characterEscape)) {
                    return nok.step(code);
                }

                effects.enter(TYPE_STRIKETHROUGH_SEQUENCE_TEMPORARY);
                return more(code);
            }

            State more(int code) {
                var before = ClassifyCharacter.classifyCharacter(previous);

                if (code == Codes.tilde) {
                    // If this is the third marker, exit
                    if (size > 1) {
                        return nok.step(code);
                    }
                    effects.consume(code);
                    size++;
                    return this::more;
                }

                if (size < 2 && !singleTilde) {
                    return nok.step(code);
                }

                var token = effects.exit(TYPE_STRIKETHROUGH_SEQUENCE_TEMPORARY);
                var after = ClassifyCharacter.classifyCharacter(code);

                token.set(OPEN, after == 0 ||
                        (after == Constants.attentionSideAfter && before != 0));
                token.set(CLOSE, before == 0 ||
                        (before == Constants.attentionSideAfter && after != 0));

                return ok.step(code);
            }
        }

        return new StateMachine()::start;
    }

    public record Options(boolean singleTilde) {
        public static Options DEFAULT = new Options(true);
    }
}
