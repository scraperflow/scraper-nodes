package scraper.nodes.server;

import org.eclipse.jetty.rewrite.handler.RedirectPatternRule;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.RewriteRegexRule;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import scraper.annotations.NotNull;
import scraper.annotations.node.*;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.FunctionalNodeContainer;
import scraper.api.node.container.NodeContainer;
import scraper.api.node.type.FunctionalNode;
import scraper.api.node.type.Node;
import scraper.api.template.T;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static scraper.api.node.container.NodeLogLevel.*;


/**
 * Redirects urls to other urls.
 */
@NodePlugin(value = "0.1.0", customFlowAfter = true)
@Io
@Stateful
public final class RedirectServer implements FunctionalNode {

    /** Port of the server */
    @FlowKey(defaultValue = "8081") @Argument
    private Integer port;

    /**
     * Regex to url mapping, e.g
     * <pre>
     *  "/reverse/([^/]*)/(.*)": "/reverse/$2/$1" </pre>
     */
    @FlowKey(defaultValue = "{}")
    private final T<Map<String, String>> regexRedirect = new T<>(){};

    /**
     * Pattern to url mapping, e.g
     * <pre>
     *  "/reverse/*": "http://redirected.org" </pre>
     */
    @FlowKey(defaultValue = "{}")
    private final T<Map<String, String>> patternRedirect = new T<>(){};

    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public void modify(@NotNull FunctionalNodeContainer n, @NotNull FlowMap o) throws NodeException {
        if(!started.getAndSet(true)) {
            n.log(DEBUG,"Starting redirect server...");
            startServer(n, port, o);
            n.log(INFO,"Started redirect server on port {}", port);
        }
    }

    private void startServer(NodeContainer<? extends Node> n, Integer port, FlowMap o) throws NodeException {
        Map<String, String> regexRedirect = o.eval(this.regexRedirect);
        Map<String, String> patternRedirect = o.eval(this.patternRedirect);

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
            n.log(ERROR,"Jetty server failed to start: {}", e.getMessage());
            throw new NodeException(e,"Fix server implementation");
        }
    }

}
