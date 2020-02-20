package scraper.nodes.dev.api.pr0gramm;


import scraper.annotations.NotNull;
import scraper.annotations.node.FlowKey;
import scraper.annotations.node.NodePlugin;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.NodeContainer;
import scraper.api.node.container.NodeLogLevel;
import scraper.api.node.type.Node;
import scraper.api.reflect.T;
import scraper.api.service.proxy.ProxyMode;
import scraper.api.service.proxy.ReservationToken;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Provides log-in functionality and returns required 'me' and 'pp' cookies
 */
@NodePlugin("0.1.0")
public class Pr0grammLoginNode implements Node {

    private static final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/67.0.3396.99 Safari/537.36";

    /** User name */
    @FlowKey(defaultValue = "\"name\"") @NotNull
    private final T<String> user = new T<>(){};

    /** Password */
    @FlowKey(defaultValue = "\"password\"") @NotNull
    private final T<String> password = new T<>(){};

    @Override @NotNull
    public FlowMap process(@NotNull NodeContainer<? extends Node> n, @NotNull FlowMap o) throws NodeException {
        HttpRequest request = buildLoginRequest(o);

        ReservationToken token;
        try {
            token = n.getJobInstance().getProxyReservation().reserveToken("local", ProxyMode.LOCAL,0, 500);
        } catch (InterruptedException | TimeoutException e) {
            throw new NodeException(e, "Could not reserve local transport");
        }

        try {
            HttpResponse<?> body = n.getJobInstance().getHttpService().send(request, HttpResponse.BodyHandlers.ofString(), token);

            List<String> cookies = body.headers().allValues("set-cookie");
            if(cookies.isEmpty()) {
                n.log(NodeLogLevel.ERROR, "set-cookie headers missing, login api changed?");
                throw new NodeException("set-cookies missing, login api changed?");
            }

            for (String cookie : cookies) {
                String key = cookie.substring(0, cookie.indexOf("="));
                String value = cookie.substring(cookie.indexOf("=")+1, cookie.indexOf(";"));
                n.log(NodeLogLevel.INFO, "Cookie: {} -> {}", key, value);

                o.output(key, value);
            }

        } catch (IOException | InterruptedException | TimeoutException | ExecutionException e) {
            n.log(NodeLogLevel.ERROR, "Could not login: {}", e);
            throw new NodeException(e, "Could not login");
        } finally {
            token.close();
        }

        return o;
    }

    private HttpRequest buildLoginRequest(FlowMap o) throws NodeException {
        try {
            URI uri = new URI(o.get("api-base") + "user/login");
            HttpRequest.Builder request = HttpRequest.newBuilder(uri);

            String formData = "name=" + URLEncoder.encode(o.eval(user), StandardCharsets.UTF_8) +
                    "&password=" + URLEncoder.encode(o.eval(password), StandardCharsets.UTF_8);

            request.header("User-Agent", userAgent)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "text/plain");


            request.POST(HttpRequest.BodyPublishers.ofString(formData));

            return request.build();
        } catch (Exception e) {
            throw new NodeException(e, "Could not build http login request");
        }
    }

}
