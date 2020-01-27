package scraper.nodes.dev.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import scraper.annotations.NotNull;
import scraper.annotations.node.FlowKey;
import scraper.annotations.node.NodePlugin;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.core.AbstractNode;
import scraper.core.AbstractStreamNode;
import scraper.core.Template;
import scraper.util.NodeUtil;

import java.util.List;

/**
 */
@NodePlugin("1.0.0")
public final class HtmlCssQueryNode extends AbstractStreamNode {

    /** Expect a raw html string at key defined by 'html' */
    @FlowKey(mandatory = true)
    private final Template<String> html = new Template<>(){};

    /** Puts the parsed element as text to this key */
    @FlowKey(defaultValue = "\"result\"")
    private String put;

    /** Css Query for selecting elements */
    @FlowKey(mandatory = true)
    private String query;

    /** If enabled, stops after first found element */
    @FlowKey(defaultValue = "false")
    private Boolean onlyFirst;

    /** If enabled, inserts text instead of inner html */
    @FlowKey(defaultValue = "true")
    private Boolean textOnly;

    @Override @NotNull
    public FlowMap process(@NotNull FlowMap o) throws NodeException {
        // get html data at location
        String rawHtml = html.eval(o);

        Document doc = Jsoup.parse(rawHtml);
        Elements elements = doc.select(query);
        for (Element element : elements) {
            FlowMap copy = NodeUtil.flowOf(o);
            copy.put(put, textOnly ? element.text() : element.html());

            stream(o, copy, List.of(put));

            if(onlyFirst) break;
        }

        return forward(o);
    }
}
