package scraper.nodes.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import scraper.annotations.NotNull;
import scraper.annotations.node.*;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.api.node.Address;
import scraper.api.node.container.FunctionalNodeContainer;
import scraper.api.node.container.NodeContainer;
import scraper.api.node.type.FunctionalNode;
import scraper.api.node.type.Node;
import scraper.api.template.L;
import scraper.api.template.T;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.net.URLDecoder.decode;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static scraper.api.node.container.NodeLogLevel.*;


/**
 * A socket node intercepts incoming <code>GET</code> requests at the specified port with format
 * <pre>
 *     /?q=...
 * </pre>
 * Puts the requests (everything after the '=') at the field 'put' if specified.
 * <p>
 * It responds with a string representation of the result Object (at the 'expected' field) or a JSON response if an exception occurred.
 *<p>
 *  If caching is enabled, queries are cached and return the same result if queried twice. Caching is on by default.
 *</p>
 *<p>
 *  Requests can either be specified by hosts or arguments. If neither, the next node is used.
 *</p>
 *
 * <p>
 *     Example usage (yaml):
 *
 * <pre>
 * # Forwards actions to the appropriate nodes
 * type: SocketNode
 * port: "{socket-port}"
 * cache: false
 * expected: result
 * args:
 *   STATE: getElapsedErrorTime
 *   TIMEOUT: getTimeLeft
 *   OFF: fridgeOff
 *   ON: fridgeOn
 * goTo: initPings
 * ignoreLogs: [STATE, TIMEOUT]
 * </pre>
 *</p>
 *
 * Authors:
 * <ul>
 *     <li>Albert Schimpf</li>
 *     <li>Marco Meides</li>
 * </ul>
 */
@NodePlugin("0.7.0")
@Stateful
@Io
public final class SocketNode implements FunctionalNode {

    /** After the return of the forward call, the result object is expected at this key */
    @FlowKey
    private final T<String> expected = new T<>(){};

    /** Content type if result is a file */
    @FlowKey(defaultValue = "\"text/plain\"")
    private final T<String> contentType = new T<>(){};

    /** Hostname to target label mapping, if any */
    @FlowKey
    @Flow(dependent = true, crossed = true, label = "")
    private final T<Map<String, Address>> hostMap = new T<>(){};

    /** Literal request to target label mapping, if any */
    @FlowKey
    @Flow(dependent = true, crossed = true, label = "")
    private final T<Map<String, Address>> args = new T<>(){};

    @FlowKey(defaultValue = "{}")
    private final T<Map<String, String>> responseHeaders = new T<>(){};

    /** basic auth, name password pairs */
    @FlowKey(defaultValue = "{}")
    private final T<Map<String, String>> basicAuth = new T<>(){};



    /** Request is saved at this key location, if any */
    @FlowKey(defaultValue = "\"_\"")
    private final L<String> put = new L<>(){};

    /** <code>POST</code> body is saved at this key location, if any */
    @FlowKey(defaultValue = "\"_\"")
    private final L<String> putBody = new L<>(){};

    /** Additional GET request parameters, if any, are saved as a parameter map at this key location */
    @FlowKey(defaultValue = "\"_\"")
    private final L<Map<String, String>> putParamsPrefixMap = new L<>(){};



    /** Port of the server */
    @FlowKey(defaultValue = "8080") @Argument
    private Integer port;

    /** Prefix for additional parameters */
    @FlowKey(defaultValue = "\"\"")
    private String putParamsPrefix;

    /** Caches <code>expected</code> for same requests. Should not be used together with zip output. */
    @FlowKey(defaultValue = "false")
    private Boolean cache;

    /** Limits requests to one at a time if true */
    @FlowKey(defaultValue = "false")
    private Boolean queue;


    // caching
    private final Map<String, Object> resultCache = new ConcurrentHashMap<>();
    // manage concurrent requests
    private final List<String> ongoingRequests = Collections.synchronizedList(new ArrayList<>());
    // mapper to generate JSON exception responses
    private static final ObjectMapper mapper = new ObjectMapper();

    private FlowMap currentArgs;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private String getRequest(HttpServletRequest request, HttpServletResponse response, FlowMap args) throws IOException, URISyntaxException {
        final String uri = ((Request) request).getOriginalURI();

        if (!uri.startsWith("/?q=")) {
            wrapException(
                    response, new IllegalArgumentException(), "Invalid request format. Expected '/?q=...', got %s",
                    SC_BAD_REQUEST, uri
            );
            return null;
        }

        if(uri.substring(4).isEmpty()) {
            wrapException(
                    response, new IllegalArgumentException(), "Empty request", SC_BAD_REQUEST
            );
            return null;
        }

        List<NameValuePair> parameters;
        Request r = ((Request) request);
        URI uri2 = new URI(r.getOriginalURI());

        parameters = URLEncodedUtils.parse(uri2.getQuery(), StandardCharsets.UTF_8);

        args.output(putParamsPrefixMap, parameters.stream()
                .map(p -> Map.entry(putParamsPrefix + p.getName(), p.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );

        return decode(parameters.get(0).getValue(), StandardCharsets.UTF_8);
    }


    private void handleInternal(
            final NodeContainer<? extends Node> n,
            final HttpServletResponse response,
            final FlowMap args,
            final String param
    ) throws
            IOException, ExecutionException, RequestMappingException, NodeException, InterruptedException {
        n.log(INFO,"Request for query '{}'", param);

        args.output(put, param);

        // guard for multiple same requests
        boolean skip = false;
        synchronized (ongoingRequests) {
            //noinspection StatementWithEmptyBody // readability
            if(ongoingRequests.contains(param)) {
                // already ongoing, will get stuck in next loop
            }
            else
                //noinspection StatementWithEmptyBody // readability
                if (resultCache.get(param) != null){
                // result already computed
            } else {
                // result not computed and not ongoing
                // be the first to add it to ongoing requests, skip loop
                ongoingRequests.add(param);
                skip = true;
            }
        }

        while(!skip && ongoingRequests.contains(param)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Object resultString = resultCache.get(param);
        if(resultString == null) {
            resultString = createRequest(n, param, args);
        }
        response.setStatus(HttpServletResponse.SC_OK);

        args.eval(responseHeaders).forEach(response::setHeader);
        response.setContentType(args.eval(contentType));

//        if(!isFile) {
            response.getWriter().print(((resultString == null ? "null" : resultString.toString())));
//        } else {
//            streamContent(n, response, args);
//        }
    }

//    private void streamContent(NodeContainer<? extends Node> n, HttpServletResponse response, FlowMap o) throws IOException {
//        String filePath = n.getJobInstance().getFileService().getTemporaryDirectory()+File.separator+o.eval(expected);
//        try (FileInputStream fs = new FileInputStream(filePath)) {
//            IOUtils.copy(fs, response.getOutputStream());
//        }
//        if(!new File(filePath).delete()) {
//            n.log(WARN,"Could not delete streamed zip file: {}", filePath);
//        }
//    }


     // socket node is never interrupted while waiting for the future
    private Object createRequest(NodeContainer<?> n, final String url, final FlowMap o)
            throws MalformedURLException, RequestMappingException,
            ExecutionException, NodeException, InterruptedException {
        Map<String, Address> hostMap = o.evalIdentity(this.hostMap);
        Map<String, Address> args = o.evalIdentity(this.args);

        Address process;
        if (!hostMap.isEmpty()) {
            process = hostMap.get(new URL(url).getHost());
            if(process == null) throw new RequestMappingException("Host mapping not defined: " + url);
        } else if (!args.isEmpty()){
            process = args.get(url);
            if(process == null) throw new RequestMappingException("Request mapping not defined: " + url);
        } else {
            throw new NodeException("Neither a host mapping nor a request mapping is defined");
        }

        // submit request

        CompletableFuture<FlowMap> futureFlow = n.forkDepend(o, process);
        FlowMap result = futureFlow.get();
        String resultStr = result.eval(expected);

//        Object output = null;
//        if(!isFile && resultMaybe != null) output = result.get(resultMaybe);
        if(cache != null && cache) resultCache.put(url, resultStr);
        return resultStr;
    }

    public void modify(@NotNull FunctionalNodeContainer n, @NotNull FlowMap o) throws NodeException {
        //save map
        currentArgs = o.copy();

        if(!started.getAndSet(true)) {
            n.log(DEBUG,"Starting socket server...");
            startServer(n, port, o);
            n.log(INFO,"Started socket server on port {}", port);
        }
    }

    private void startServer(NodeContainer<? extends Node> n, Integer port, FlowMap o) throws NodeException {
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

        if(!o.eval(basicAuth).isEmpty())
            context.setSecurityHandler(basicAuth(o));

        context.setContextPath("/");
        server.setHandler(context);



        try {
            server.start();
//            server.join();
        } catch (Exception e) {
            n.log(ERROR,"Jetty server failed to start: {}", e.getMessage());
            throw new NodeException(e,"Fix server implementation");
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



    static class RequestMappingException extends Exception {
        RequestMappingException (String s) {
            super(s);
        }
    }


    static class SocketHandler extends HttpServlet {
        private final NodeContainer<? extends Node> nodeC;
        private final SocketNode node;
        private final Boolean queue;
        private final L<String> putBody;

        private final AtomicBoolean waitingForFinish = new AtomicBoolean(false);



        SocketHandler(NodeContainer<? extends Node> container, SocketNode node) {
            this.nodeC = container;
            this.node = node;
            this.queue = node.queue;
            this.putBody = node.putBody;
        }

        private void sync() {
            if(queue) synchronized (waitingForFinish) {
                while(waitingForFinish.get()) {
                    try {
                        waitingForFinish.wait();
                    } catch (InterruptedException e) { e.printStackTrace(); }
                }

                waitingForFinish.set(true);
            }
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            sync();

            FlowMap args = node.currentArgs.copy();

            String body = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            args.output(putBody, body);

            handle(req, resp, args);
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            sync();

            FlowMap args = node.currentArgs.copy();
            handle(request,response, args);
        }

        private void handle(HttpServletRequest request, HttpServletResponse response, FlowMap args) throws IOException {
            try{

                String req = node.getRequest(request, response, args);
                response.setHeader("Access-Control-Allow-Origin", "*");

                if(req == null) return;

                try {
                    node.handleInternal(nodeC, response, args, req);
                }
                catch (RequestMappingException e) {
                    nodeC.log(WARN, "Received unknown request!", e.getMessage());
                    wrapException(response, e, "Bad request: %s", SC_BAD_REQUEST, e.getMessage());
                }
                catch (MalformedURLException e) {
                    nodeC.log(INFO,"Request not encoded properly or not a valid host: "+req);
                    wrapException(response, e, "Request was not encoded correctly or host is not valid! %s", SC_BAD_REQUEST, e.getMessage());
                }
                catch (InterruptedException e) {
                    nodeC.log(WARN,"Request interrupted on server side: "+req);
                    wrapException(response, e, "Request was interrupted on server side! %s", SC_INTERNAL_SERVER_ERROR, e.getMessage());
                }
                catch (ExecutionException e) {
                    if(e.getCause() != null && e.getCause() instanceof NodeException) {
                        int code = 500;
                        String message = e.getCause().getMessage();
                        String fixMessage = e.getMessage();

                        nodeC.log(WARN,"{}; {}", message, fixMessage);
                        wrapException(response, e, "Error during request execution. %s: %s", code, message, fixMessage);
                    } else {
                        e.printStackTrace();
                        nodeC.log(ERROR,"Unexpected exception '"+e.getCause().getClass().getSimpleName()+"' thrown inside node processes!", e.getCause().getCause());
                        wrapException(response, e, "Error during request execution, unknown cause.",
                                SC_INTERNAL_SERVER_ERROR, String.valueOf(e.getCause()));
                    }
                }
                catch (NodeException e) {
                    nodeC.log(ERROR,"Failed argument template substitution!");
                    wrapException(response, e, "Severe scrape definition error.",
                            SC_INTERNAL_SERVER_ERROR);
                } finally {
                    synchronized (node.ongoingRequests) {
                        node.ongoingRequests.remove(req);
                    }
                }

            }
            catch (URISyntaxException e) {
                nodeC.log(ERROR,"Failed reservation or not an URI!");
                wrapException(response, e, "Severe scrape definition error.", SC_INTERNAL_SERVER_ERROR);
            }
            finally {
                if(queue) synchronized (waitingForFinish) {
                    waitingForFinish.set(false);
                    waitingForFinish.notifyAll();
                }
            }
        }
    }

    private SecurityHandler basicAuth(FlowMap o) {
        HashLoginService l = new HashLoginService();
        UserStore store = new UserStore();

        o.eval(basicAuth).forEach((username, password) ->
                store.addUser(username, Credential.getCredential(password), new String[]{"user"})
        );

        l.setUserStore(store);
        l.setName("private");

        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName("myrealm");
        csh.addConstraintMapping(cm);
        csh.setLoginService(l);

        return csh;

    }
}
