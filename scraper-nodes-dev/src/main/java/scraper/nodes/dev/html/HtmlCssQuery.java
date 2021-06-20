package scraper.nodes.dev.html;

import scraper.annotations.*;
import scraper.api.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Executes a css query on a html String.
 */
@NodePlugin("0.5.0")
public final class HtmlCssQuery implements StreamNode {

    /** Raw html String */
    @FlowKey(mandatory = true)
    private final T<String> html = new T<>(){};

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

    /** Puts the parsed element as text to this key */
    @FlowKey(mandatory = true)
    private final L<String> put = new L<>(){};

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
                case OUTERHTML:
                    copy.output(put, element.outerHtml());
                    break;
            }

            n.streamFlowMap(o, copy);

            if(onlyFirst) break;
        }
    }


    enum ElementOutput { TEXT, HTML, ATTR, OUTERHTML }
}
