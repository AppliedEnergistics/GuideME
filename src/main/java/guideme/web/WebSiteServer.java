package guideme.web;

import org.jetbrains.annotations.ApiStatus;

/**
 * Serves the content in a folder locally over HTTP on a random port, to preview it in a browser. Opening the pages
 * directly from disk does not work, since browsers block the scripts used by the website.
 * <p>
 * Takes the website folder as its only argument and runs until it is stopped.
 */
@ApiStatus.Experimental
public final class WebSiteServer {
    private WebSiteServer() {
    }

    static void main(String[] args) {
        guideme.internal.web.WebSiteServer.main(args);
    }
}
