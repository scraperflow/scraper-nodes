package scraper.nodes.dev.functional;

import scraper.annotations.node.FlowKey;
import scraper.annotations.node.NodePlugin;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.NodeContainer;
import scraper.api.node.type.Node;
import scraper.api.template.T;

import java.util.List;

/**
 * Stops forwarding if empty list is provided
 */
@NodePlugin("0.1.0")
public final class FilterEmptyList<A> implements Node {

    /** List to filter */
    @FlowKey
    private final T<List<A>> list = new T<>(){};

    @Override
    public FlowMap process(NodeContainer<? extends Node> n, FlowMap o) throws NodeException {
        List<A> list = o.eval(this.list);
        if(!list.isEmpty()) return n.forward(o);
        else throw new BreakException();
    }

}
