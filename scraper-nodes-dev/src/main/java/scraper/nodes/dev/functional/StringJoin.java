package scraper.nodes.dev.functional;

import scraper.annotations.NotNull;
import scraper.annotations.node.FlowKey;
import scraper.annotations.node.NodePlugin;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.FunctionalNodeContainer;
import scraper.api.node.type.FunctionalNode;
import scraper.api.template.L;
import scraper.api.template.T;

import java.util.List;

/**
 * Flattens a list of strings into a string
 */
@NodePlugin("0.1.0")
public final class StringJoin implements FunctionalNode {

    /** The list with strings to join */
    @FlowKey(mandatory = true)
    private final T<List<String>> list = new T<>(){};

    /** Where the flattened string is stored */
    @FlowKey(defaultValue = "\"_\"")
    private final L<String> output = new L<>(){};

    /** The join delimiter */
    @FlowKey(defaultValue = "\"\"")
    private final T<String> delimiter = new T<>(){};

    @Override
    public void modify(@NotNull FunctionalNodeContainer n, @NotNull final FlowMap o) {
        String delimiter = o.eval(this.delimiter);
        String joined = String.join(delimiter, o.eval(this.list));
        o.output(output, joined);
    }
}
