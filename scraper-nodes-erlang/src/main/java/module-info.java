import scraper.api.node.type.Node;
import scraper.nodes.erlang.ErlangDependencyGraphNode;

open module scraper.nodes.erlang {
    requires scraper.core;

    provides Node with ErlangDependencyGraphNode;
}
