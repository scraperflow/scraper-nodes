package scraper.nodes.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import scraper.annotations.NotNull;
import scraper.annotations.node.*;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.api.flow.impl.FlowMapImpl;
import scraper.api.node.Address;
import scraper.api.node.container.FunctionalNodeContainer;
import scraper.api.node.type.FunctionalNode;
import scraper.util.TemplateUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;


/**
 */
@NodePlugin(value = "0.1.0", customFlowAfter = true)
@Stateful
@Io
public final class DistributedRequest implements FunctionalNode {

    /** Port of the server */
    @FlowKey(defaultValue = "9081") @Argument
    private Integer port;
    @FlowKey(mandatory = true) @Argument
    private String host;

    // mapper to generate JSON exception responses
    private static final ObjectMapper mapper = new ObjectMapper();

    /** True target address */
    @FlowKey
    @Flow(dependent = true, crossed = false, label = "request")
    private Address distTarget;

    public void modify(@NotNull FunctionalNodeContainer n, @NotNull FlowMap o) throws NodeException {
        try {
            final @NotNull HttpClient localClient = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();

            URI uri = new URI("http://"+host+":"+port);
            HttpRequest.Builder request = HttpRequest.newBuilder(uri);
            String payload = mapper.writeValueAsString(((FlowMapImpl) o).getPrivateMap());
            request.POST(HttpRequest.BodyPublishers.ofString(payload));

            HttpResponse<String> response = localClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            System.out.println(response.body());
            Map m = mapper.readValue(response.body(), Map.class);
            m.forEach((k,v) -> o.output(TemplateUtil.locationOf(((String) k)), v));
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }
}
