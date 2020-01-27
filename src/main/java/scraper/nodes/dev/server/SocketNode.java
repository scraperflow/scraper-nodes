package scraper.nodes.dev.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
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
import scraper.annotations.node.Argument;
import scraper.annotations.node.FlowKey;
import scraper.annotations.node.NodePlugin;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.core.AbstractFunctionalNode;
import scraper.core.AbstractNode;
import scraper.core.Template;
import scraper.util.NodeUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.net.URLDecoder.decode;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static scraper.core.NodeLogLevel.*;
import static scraper.util.NodeUtil.flowOf;

/**
 * A socket node intercepts incoming GET requests at the specified port with format
 * <pre>
 *     /?q=...
 * </pre>
 * Puts the requests (everything after the '=') at the field 'put' if specified. <p>
 * It responds with a string representation of the result Object (at the 'expected' field) or a JSON response if an exception occurred.
 *<p>
 *  If caching is enabled, queries are cached and return the same result if queried twice. Caching is on by default.
 *</p>
 *<p>
 *  Requests can either be specified by hosts or arguments. If neither, the next node is used.
 *</p>
 *
 * <p>
 *     Example args .scrape definition:
 *
 * <pre>
 * {
 *   "type"      : "SocketNode",
 *   "__comment" : "Forwards actions to the appropriate nodes",
 *   "port"      : "{socket-port}",
 *   "cache"     : false,
 *   "expected"  : "result",
 *   "args"      : {
 *     "STATE"  : "getElapsedErrorTime",
 *     "TIMEOUT": "getTimeLeft",
 *     "OFF"    : "fridgeOff",
 *     "ON"     : "fridgeOn"
 *   },
 *   "goTo": "initPings",
 *   "ignoreLogs" : ["STATE", "TIMEOUT"]
 * },
 * </pre>
 *</p>
 *
 * @see AbstractNode
 * @since 0.1
 * @author Albert Schimpf
 * @author Marco Meides
 */
@NodePlugin("0.5.1")
public final class SocketNode extends AbstractFunctionalNode {

    /** Port of the server */
    @FlowKey(defaultValue = "8080") @Argument
    private Integer port;

    /** After the return of the forward call, the result object is expected at this key */
    @FlowKey
    private final Template<String> expected = new Template<>(){};

    /** If expected is a file */
    @FlowKey(defaultValue = "false") @Argument
    private Boolean isFile;

    /** Request is saved at this key location, if any */
    @FlowKey
    private String put;

    /** POST body is saved at this key location, if any
     * @since 0.4 */
    @FlowKey(defaultValue = "\"body\"")
    private String putBody;

    /** Content type if result is a file */
    @FlowKey(defaultValue = "\"text/plain\"")
    private Template<String> contentType = new Template<>(){};

    /** Additional GET-request parameters, if any, are saved as a parameter list at this key location */
    @FlowKey
    private String putParamsPrefix;

    @FlowKey(defaultValue = "{}")
    private final Template<Map<String, String>> responseHeaders = new Template<>(){};

    /** Caches {@link #expected} for same requests. Should not be used together with zip output. */
    @FlowKey(defaultValue = "false")
    private Boolean cache;

    /** hostname to target label mapping, if any */
    @FlowKey
    private Map<String, String> hostMap;

    /** Literal request to target label mapping, if any */
    @FlowKey
    private Map<String, String> args;

    /** Limits requests to one at a time if true */
    @FlowKey(defaultValue = "false")
    private Boolean queue;

    /** Ignores logging for specified requests */
    @FlowKey(defaultValue = "[]")
    private List<String> ignoreLogs;

    /** basic auth, name password pairs */
    @FlowKey(defaultValue = "{}")
    private Template<Map<String, String>> basicAuth = new Template<>(){};

    // caching
    private final Map<String, Object> resultCache = new ConcurrentHashMap<>();
    // manage concurrent requests
    private final List<String> ongoingRequests = Collections.synchronizedList(new ArrayList<>());
    // mapper to generate JSON exception responses
    private final ObjectMapper mapper = new ObjectMapper();

    private FlowMap currentArgs;
    private AtomicBoolean started = new AtomicBoolean(false);

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

        if (putParamsPrefix == null) putParamsPrefix = "";
        for (NameValuePair parameter : parameters) {
            args.put(putParamsPrefix + parameter.getName(), parameter.getValue());
        }


        return decode(parameters.get(0).getValue(), StandardCharsets.UTF_8);
    }


    private void handleInternal(
            final HttpServletResponse response,
            final FlowMap args,
            final String param
    ) throws
            IOException, ExecutionException, RequestMappingException, NodeException {


        if(!ignoreLogs.contains(param))
            log(INFO,"Request for query '{}'", param);

        if(put != null)
            args.put(put, param);

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
            resultString = createRequest(param, args);
        }
        response.setStatus(HttpServletResponse.SC_OK);

        responseHeaders.eval(args).forEach(response::setHeader);
        response.setContentType(contentType.eval(args));

        if(!isFile) {
            response.getWriter().print(((resultString == null ? "null" : resultString.toString())));
        } else {
            streamContent(response, args);
        }

    }

    private void streamContent(HttpServletResponse response, FlowMap o) throws IOException, NodeException {
        String filePath = getJobPojo().getFileService().getTemporaryDirectory()+File.separator+expected.eval(o);
        try (FileInputStream fs = new FileInputStream(filePath)) {
            IOUtils.copy(fs, response.getOutputStream());
        }
        if(!new File(filePath).delete()) {
            log(WARN,"Could not delete streamed zip file: {}", filePath);
        }
    }


     // socket node is never interrupted while waiting for the future
    private Object createRequest(final String url, final FlowMap o)
            throws MalformedURLException, RequestMappingException,
            ExecutionException, NodeException {

        Object process;
        if (hostMap != null) {
            process = hostMap.get(new URL(url).getHost());
            if(process == null) throw new RequestMappingException("Host mapping not defined: " + url);
        } else if (args != null){
            process = args.get(url);
            if(process == null) throw new RequestMappingException("Request mapping not defined: " + url);
        } else {
            process = getStageIndex() + 1;
        }


        // submit request
        Future<?> future = getService().submit((Callable<Void>) () -> {
            log(TRACE,"Delegating request to process '{}'", process);
            eval(o, NodeUtil.addressOf(String.valueOf(process)));
            return null;
        });

        // wait for request to finish
        try {
            future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Object output = null;
        if(!isFile && expected.eval(o) != null) output = o.get(expected.eval(o));
        if(cache != null && cache && output != null) resultCache.put(url, output);
        return output;
    }

    @Override
    public void modify(@NotNull FlowMap o) throws NodeException {
        //save map
        currentArgs = NodeUtil.flowOf(o);

        if(!started.getAndSet(true)) {
            log(DEBUG,"Starting socket server...");
            startServer(port, o);
            log(INFO,"Started socket server on port {}", port);
        }
    }

    private void startServer(Integer port, FlowMap o) throws NodeException {
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
        context.addServlet(new ServletHolder(new SocketHandler(this)),"/*");

        if(!basicAuth.eval(o).isEmpty())
            context.setSecurityHandler(basicAuth(o));

        context.setContextPath("/");
        server.setHandler(context);



        try {
            server.start();
//            server.join();
        } catch (Exception e) {
            log(ERROR,"Jetty server failed to start: {}", e.getMessage());
            throw new NodeException(e,"Fix server implementation");
        }
    }

    private void wrapException(HttpServletResponse response, Exception e, String message, int status, String... args) throws IOException {
        ObjectNode node = mapper.createObjectNode();

        if(e != null) {
            node.put("exception", String.valueOf(e));
            node.put("message", String.valueOf(e.getMessage()));
        }

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
        private final SocketNode node;
        private final Boolean queue;
        private final String putBody;

        private final AtomicBoolean waitingForFinish = new AtomicBoolean(false);



        SocketHandler(SocketNode socketNode) {
            this.node = socketNode;
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
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            sync();

            FlowMap args = flowOf(node.currentArgs);
            String body = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            args.put(putBody, body);

            handle(req, resp, args);
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            sync();

            FlowMap args = flowOf(node.currentArgs);
            handle(request,response, args);
        }

        private void handle(HttpServletRequest request, HttpServletResponse response, FlowMap args) throws IOException {
            try{

                String req = node.getRequest(request, response, args);
                response.setHeader("Access-Control-Allow-Origin", "*");

                if(req == null) return;

                try {
                    node.start(args);

                    node.handleInternal(response, args, req);

                    node.finish(args);
                }
                catch (RequestMappingException e) {
                    node.log(WARN, "Received unknown request!", e.getMessage());
                    node.wrapException(response, e, "Bad request: %s", SC_BAD_REQUEST, e.getMessage());
                }
                catch (MalformedURLException e) {
                    node.log(INFO,"Request not encoded properly or not a valid host: "+req);
                    node.wrapException(response, e, "Request was not encoded correctly or host is not valid! %s", SC_BAD_REQUEST, e.getMessage());
                }
                catch (ExecutionException e) {
                    if(e.getCause() != null && e.getCause() instanceof NodeException) {
                        int code = 500;
                        String message = e.getCause().getMessage();
                        String fixMessage = e.getMessage();

                        node.log(WARN,"{}; {}", message, fixMessage);
                        node.wrapException(response, e, "Error during request execution. %s: %s", code, message, fixMessage);
                    } else {
//                        e.printStackTrace();
                        node.log(ERROR,"Unexpected exception '"+e.getCause().getClass().getSimpleName()+"' thrown inside node processes!", e.getCause().getCause());
                        node.wrapException(response, e, "Error during request execution, unknown cause.",
                                SC_INTERNAL_SERVER_ERROR, String.valueOf(e.getCause()));
                    }
                }
//                catch (ReservationException e) {
//                    node.log(ERROR,"Could not put result into map, reserved field!");
//                    node.wrapException(response, e, "Error during request execution, field was already reserved in map.",
//                            SC_INTERNAL_SERVER_ERROR);
//                }
                catch (NodeException e) {
                    node.log(ERROR,"Failed argument template substitution!");
                    node.wrapException(response, e, "Severe scrape definition error.",
                            SC_INTERNAL_SERVER_ERROR);
                } finally {
                    synchronized (node.ongoingRequests) {
                        node.ongoingRequests.remove(req);
                    }
                }

            }
            catch (URISyntaxException e) {
                node.log(ERROR,"Failed reservation or not an URI!");
                node.wrapException(response, e, "Severe scrape definition error.", SC_INTERNAL_SERVER_ERROR);
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

        basicAuth.eval(o).forEach((username, password) ->
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
