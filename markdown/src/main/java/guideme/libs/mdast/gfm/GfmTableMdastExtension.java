package guideme.libs.mdast.gfm;

import guideme.libs.mdast.MdastContext;
import guideme.libs.mdast.MdastContextProperty;
import guideme.libs.mdast.MdastExtension;
import guideme.libs.mdast.gfm.model.GfmTable;
import guideme.libs.mdast.gfm.model.GfmTableCell;
import guideme.libs.mdast.gfm.model.GfmTableRow;
import guideme.libs.mdast.model.MdAstInlineCode;
import guideme.libs.micromark.Token;
import guideme.libs.micromark.extensions.gfm.GfmTableSyntax;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public final class GfmTableMdastExtension {

    private static final MdastContextProperty<Boolean> IN_TABLE = new MdastContextProperty<>();

    public static final MdastExtension INSTANCE = MdastExtension.builder()
            .enter("table", GfmTableMdastExtension::enterTable)
            .enter("tableData", GfmTableMdastExtension::enterCell)
            .enter("tableHeader", GfmTableMdastExtension::enterCell)
            .enter("tableRow", GfmTableMdastExtension::enterRow)
            .exit("codeText", GfmTableMdastExtension::exitCodeText)
            .exit("table", GfmTableMdastExtension::exitTable)
            .exit("tableData", GfmTableMdastExtension::exit)
            .exit("tableHeader", GfmTableMdastExtension::exit)
            .exit("tableRow", GfmTableMdastExtension::exit)
            .build();

    private GfmTableMdastExtension() {
    }

    private static void enterTable(MdastContext context, Token token) {
        var align = token.get(GfmTableSyntax.ALIGN);

        var table = new GfmTable();
        table.align = align;

        context.enter(table, token);
        context.set(IN_TABLE, true);
    }

    private static void exitTable(MdastContext context, Token token) {
        context.exit(token);
        context.remove(IN_TABLE);
    }

    private static void enterRow(MdastContext context, Token token) {
        context.enter(new GfmTableRow(), token);
    }

    private static void exit(MdastContext context, Token token) {
        context.exit(token);
    }

    private static void enterCell(MdastContext context, Token token) {
        context.enter(new GfmTableCell(), token);
    }

    private static final Pattern ESCAPED_PIPE_PATERN = Pattern.compile("\\\\([\\\\|])");

    // Overwrite the default code text data handler to unescape escaped pipes when
    // they are in tables.
    private static void exitCodeText(MdastContext context, Token token) {
        var value = context.resume();

        if (Boolean.TRUE.equals(context.get(IN_TABLE))) {
            value = ESCAPED_PIPE_PATERN.matcher(value).replaceAll(GfmTableMdastExtension::replace);
        }

        var stack = context.getStack();
        var node = (MdAstInlineCode) stack.get(stack.size() - 1);
        node.value = value;
        context.exit(token);
    }

    private static String replace(MatchResult result) {
        // Pipes work, backslashes don’t (but can’t escape pipes).
        return result.group(1).equals("|") ? "|" : result.group();
    }

}
