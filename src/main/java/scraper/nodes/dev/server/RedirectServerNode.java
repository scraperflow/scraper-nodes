package scraper.nodes.dev.server;

import org.eclipse.jetty.rewrite.handler.RedirectPatternRule;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.RewriteRegexRule;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import scraper.annotations.NotNull;
import scraper.annotations.node.Argument;
import scraper.annotations.node.FlowKey; import scraper.annotations.node.NodePlugin;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.core.AbstractFunctionalNode;
import scraper.core.AbstractNode;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static scraper.core.NodeLogLevel.*;

/**
 * Redirects urls to other urls.
 *
 * @see AbstractNode
 * @since 0.1
 * @author Albert Schimpf
 */
@NodePlugin("0.1.0")
public final class RedirectServerNode extends AbstractFunctionalNode {

    /** Port of the server */
    @FlowKey(defaultValue = "8081") @Argument
    private Integer port;

    /**
     * Regex to url mapping, e.g
     *   "/reverse/([^/]*)/(.*)" -> "/reverse/$2/$1"
     */
    @FlowKey(defaultValue = "{}")
    private Map<String, String> regexRedirect;

    /**
     * Pattern to url mapping, e.g
     *   "/reverse/*" -> "http://redirected.org"
     */
    @FlowKey(defaultValue = "{}")
    private Map<String, String> patternRedirect;

    private AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public void modify(@NotNull final FlowMap o) throws NodeException {
        if(!started.getAndSet(true)) {
            log(DEBUG,"Starting redirect server...");
            startServer(port);
            log(INFO,"Started redirect server on port {}", port);
        }
    }

    private void startServer(Integer port) throws NodeException {
        Server server = new Server();
        server.setStopAtShutdown(true);
        server.setStopTimeout(5000);

        // HTTP connector
        ServerConnector http = new ServerConnector(server);
        http.setPort(port);
        http.setIdleTimeout(30000);
        // Set the connector
        server.addConnector(http);

        RewriteHandler rewrite = new RewriteHandler();
        rewrite.setRewriteRequestURI(true);
        rewrite.setRewritePathInfo(false);
        rewrite.setOriginalPathAttribute("requestedPath");

        patternRedirect.forEach((pattern, target) -> {
            RedirectPatternRule redirect = new RedirectPatternRule();
            redirect.setPattern(pattern);
            redirect.setLocation(target);
            rewrite.addRule(redirect);
        });

        regexRedirect.forEach((regex, replacement) -> {
            RewriteRegexRule reverse = new RewriteRegexRule();
            reverse.setRegex(regex);
            reverse.setReplacement(replacement);
            rewrite.addRule(reverse);
        });

        server.setHandler(rewrite);

        try {
            server.start();
//            server.join();
        } catch (Exception e) {
            log(ERROR,"Jetty server failed to start: {}", e.getMessage());
            throw new NodeException(e,"Fix server implementation");
        }
    }
}
