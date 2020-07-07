package scraper.nodes.dev.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import scraper.annotations.NotNull;
import scraper.annotations.node.FlowKey;
import scraper.annotations.node.NodePlugin;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.StreamNodeContainer;
import scraper.api.node.type.StreamNode;
import scraper.api.template.L;
import scraper.api.template.T;

import java.util.List;

/**
 * Executes a css query on a html String.
 */
@NodePlugin("0.2.0")
public final class HtmlCssQueryNode implements StreamNode {

    /** Raw html String */
    @FlowKey(mandatory = true)
    private final T<String> html = new T<>(){};

    /** Puts the parsed element as text to this key */
    @FlowKey(defaultValue = "\"result\"")
    private final L<String> put = new L<>(){};

    /** Css Query for selecting elements */
    @FlowKey(mandatory = true)
    private String query;

    /** If enabled, stops after first found element */
    @FlowKey(defaultValue = "false")
    private Boolean onlyFirst;

    /** If enabled, inserts text instead of inner html */
    @FlowKey(defaultValue = "true")
    private Boolean textOnly;

    @Override
    public void process(@NotNull StreamNodeContainer n, @NotNull FlowMap o) {
        // get html data at location
        String rawHtml = o.eval(html);

        Document doc = Jsoup.parse(rawHtml);
        Elements elements = doc.select(query);
        for (Element element : elements) {
            FlowMap copy = o.copy();
            copy.output(put, textOnly ? element.text() : element.html());

            n.streamFlowMap(o, copy);

            if(onlyFirst) break;
        }
    }

}
