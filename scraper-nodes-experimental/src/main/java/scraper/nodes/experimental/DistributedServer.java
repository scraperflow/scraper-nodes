package scraper.nodes.experimental;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpStatus;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import scraper.annotations.NotNull;
import scraper.annotations.node.*;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.api.flow.impl.FlowMapImpl;
import scraper.api.node.Address;
import scraper.api.node.container.FunctionalNodeContainer;
import scraper.api.node.container.NodeContainer;
import scraper.api.node.type.FunctionalNode;
import scraper.api.node.type.Node;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static scraper.api.node.container.NodeLogLevel.ERROR;


/**
 */
@NodePlugin(value = "0.1.0", customFlowAfter = true)
@Stateful
@Io
public final class DistributedServer implements FunctionalNode {

    /** Port of the server */
    @FlowKey(defaultValue = "8091") @Argument
    private Integer port;
    // mapper to generate JSON exception responses
    private static final ObjectMapper mapper = new ObjectMapper();

    /** True target address */
    @FlowKey
    @Flow(dependent = true, crossed = false, label = "request")
    private Address distTarget;

    private final AtomicBoolean started = new AtomicBoolean(false);

    public void modify(@NotNull FunctionalNodeContainer n, @NotNull FlowMap o) throws NodeException {
        if(!started.getAndSet(true)) startServer(n, port);
    }

    private void startServer(NodeContainer<? extends Node> n, Integer port) throws NodeException {
        Server server = new Server();
        server.setStopAtShutdown(true);
        server.setStopTimeout(5000);

        // HTTP connector
        ServerConnector http = new ServerConnector(server);
        http.setPort(port);
        http.setIdleTimeout(30000);
        // Set the connector
        server.addConnector(http);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.addServlet(new ServletHolder(new SocketHandler(n, this)),"/*");

        context.setContextPath("/");
        server.setHandler(context);
        try {
            server.start();
        } catch (Exception e) {
            n.log(ERROR,"Jetty server failed: {}", e.getMessage());
            throw new NodeException(e, "Fix server implementation");
        }
    }

    private static void wrapException(HttpServletResponse response, Exception e, String message, int status, String... args) throws IOException {
        ObjectNode node = mapper.createObjectNode();

        if(e != null) {
            node.put("exception", String.valueOf(e));
            node.put("message", String.valueOf(e.getMessage()));
        }

        //noinspection RedundantCast
        node.put("description", String.format(message, (Object[]) args));

        response.setStatus(status);
        response.setContentType("application/json; charset=utf-8");
        response.getWriter().println(node.toString());
    }

    static class SocketHandler extends HttpServlet {
        private final NodeContainer<? extends Node> nodeC;
        private final DistributedServer node;

        SocketHandler(NodeContainer<? extends Node> container, DistributedServer node) {
            this.nodeC = container;
            this.node = node;
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            try {
                String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
                Map m = mapper.readValue(body, Map.class);
                FlowMap i = FlowMapImpl.origin(m);

                CompletableFuture<FlowMap> futureFlow = nodeC.forkDepend(i, node.distTarget);
                FlowMap result = futureFlow.get();
                response.setStatus(HttpServletResponse.SC_OK);
//                args.eval(responseHeaders).forEach(response::setHeader);
//                response.setContentType(args.eval(contentType));
                response.getWriter().print(mapper.writeValueAsString(((FlowMapImpl) result).getPrivateMap()));
            } catch (Exception e) {
                wrapException(response, e, "Request failed on server side! %s", HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }
        }

    }
}
