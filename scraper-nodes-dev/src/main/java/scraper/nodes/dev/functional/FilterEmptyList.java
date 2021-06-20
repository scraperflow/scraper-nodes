package scraper.nodes.dev.functional;

import scraper.annotations.*;
import scraper.api.*;

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
    public void process(NodeContainer<? extends Node> n, FlowMap o) {
        List<A> list = o.eval(this.list);
        if(list.isEmpty()) throw new NodeException("Empty List");
    }

}
