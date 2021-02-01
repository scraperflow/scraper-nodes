import scraper.api.node.type.Node;
import scraper.nodes.erlang.ErlangDependencyGraph;

open module scraper.nodes.erlang {
    requires scraper.core;

    provides Node with ErlangDependencyGraph;
}
