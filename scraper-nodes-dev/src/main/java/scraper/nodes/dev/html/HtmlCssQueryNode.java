package scraper.nodes.dev.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import scraper.annotations.NotNull;
import scraper.annotations.node.FlowKey;
import scraper.annotations.node.NodePlugin;
import scraper.api.exceptions.ValidationException;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.NodeContainer;
import scraper.api.node.container.StreamNodeContainer;
import scraper.api.node.type.Node;
import scraper.api.node.type.StreamNode;
import scraper.api.specification.ScrapeInstance;
import scraper.api.template.L;
import scraper.api.template.T;

/**
 * Executes a css query on a html String.
 */
@NodePlugin("0.3.0")
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

    /** What to get from each element. TEXT, HTML, ATTR */
    @FlowKey(defaultValue = "\"TEXT\"")
    private ElementOutput elementOutput;

    /** If ATTR is enabled, fetches that attribute for every element */
    @FlowKey
    private String attr;

    @Override
    public void init(NodeContainer<? extends Node> n, ScrapeInstance instance) throws ValidationException {
        if(elementOutput.equals(ElementOutput.ATTR) && attr == null)
            throw new ValidationException("attr must be defined");
    }

    @Override
    public void process(@NotNull StreamNodeContainer n, @NotNull FlowMap o) {
        // get html data at location
        String rawHtml = o.eval(html);

        Document doc = Jsoup.parse(rawHtml);
        Elements elements = doc.select(query);
        for (Element element : elements) {
            FlowMap copy = o.copy();

            switch (elementOutput) {
                case TEXT:
                    copy.output(put, element.text());
                    break;
                case HTML:
                    copy.output(put, element.html());
                    break;
                case ATTR:
                    copy.output(put, element.attr(attr));
                    break;
            }

            n.streamFlowMap(o, copy);

            if(onlyFirst) break;
        }
    }


    enum ElementOutput { TEXT, HTML, ATTR }
}
