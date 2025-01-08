package guideme.libs.micromark.commonmark;

import guideme.libs.micromark.Assert;
import guideme.libs.micromark.Construct;
import guideme.libs.micromark.State;
import guideme.libs.micromark.TokenizeContext;
import guideme.libs.micromark.Tokenizer;
import guideme.libs.micromark.Types;
import guideme.libs.micromark.symbol.Codes;

public final class LabelStartImage {
    private LabelStartImage() {
    }

    public static final Construct labelStartImage;

    static {
        labelStartImage = new Construct();
        labelStartImage.name = "labelStartImage";
        labelStartImage.tokenize = (context, effects, ok, nok) -> new StateMachine(context, effects, ok, nok)::start;
        labelStartImage.resolveAll = LabelEnd::resolveAll;
    }

    private static class StateMachine {
        private final TokenizeContext context;
        private final Tokenizer.Effects effects;
        private final State ok;
        private final State nok;

        public StateMachine(TokenizeContext context, Tokenizer.Effects effects, State ok, State nok) {

            this.context = context;
            this.effects = effects;
            this.ok = ok;
            this.nok = nok;
        }

        /**
         * Start of label (image) start.
         *
         * <pre>
         * > | a ![b] c
         *       ^
         * </pre>
         */
        private State start(int code) {
            Assert.check(code == Codes.exclamationMark, "expected `!`");
            effects.enter(Types.labelImage);
            effects.enter(Types.labelImageMarker);
            effects.consume(code);
            effects.exit(Types.labelImageMarker);
            return this::open;
        }

        /**
         * After `!`, before a `[`.
         *
         * <pre>
         * > | a ![b] c
         *        ^
         * </pre>
         */
        private State open(int code) {
            if (code == Codes.leftSquareBracket) {
                effects.enter(Types.labelMarker);
                effects.consume(code);
                effects.exit(Types.labelMarker);
                effects.exit(Types.labelImage);
                return this::after;
            }

            return nok.step(code);
        }

        private State after(int code) {
            /*
             * To do: remove in the future once we’ve switched from `micromark-extension-footnote` to
             * `micromark-extension-gfm-footnote`, which doesn’t need this
             */
            /* Hidden footnotes hook */
            /* c8 ignore next 3 */
            return code == Codes.caret &&
                    context.getParser().constructs._hiddenFootnoteSupport
                            ? nok.step(code)
                            : ok.step(code);
        }
    }
}
