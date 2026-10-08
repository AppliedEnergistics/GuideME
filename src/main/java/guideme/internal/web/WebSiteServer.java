package guideme.internal.web;

import com.sun.net.httpserver.SimpleFileServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Implementation of {@link guideme.web.WebSiteServer}.
 */
public final class WebSiteServer {
    private WebSiteServer() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: <website-folder>");
            System.exit(1);
        }

        var root = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            System.err.println("Website folder " + root + " does not exist.");
            System.exit(1);
        }

        // Only listen locally, on a random free port
        var server = SimpleFileServer.createFileServer(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                root,
                SimpleFileServer.OutputLevel.INFO);
        server.start();

        System.out.println("Serving " + root + " at http://localhost:" + server.getAddress().getPort() + "/");
        System.out.println("Press Ctrl+C to stop.");
    }
}
