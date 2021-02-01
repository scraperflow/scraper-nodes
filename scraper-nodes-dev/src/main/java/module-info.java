import scraper.api.node.type.Node;
import scraper.nodes.dev.io.ReadFile;

open module scraper.nodes.dev {
    requires scraper.core;

    requires org.jsoup;

    // FIXME why is this needed so that reflections can find all nodes?
    provides Node with ReadFile;
}
