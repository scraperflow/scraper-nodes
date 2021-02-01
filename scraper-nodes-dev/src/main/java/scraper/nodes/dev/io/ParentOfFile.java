package scraper.nodes.dev.io;

import scraper.annotations.node.FlowKey;
import scraper.annotations.node.Io;
import scraper.annotations.node.NodePlugin;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.FunctionalNodeContainer;
import scraper.api.node.type.FunctionalNode;
import scraper.api.template.L;
import scraper.api.template.T;

import java.io.File;

/**
 * Returns the parent of a file
 * Example:
 * <pre>
 * type: ParentOfFileNode
 * </pre>
 */
@NodePlugin("0.1.0")
@Io
public final class ParentOfFile implements FunctionalNode {

    /** Parent of a file. */
    @FlowKey(defaultValue = "\"parent\"")
    private final L<String> output = new L<>(){};

    /** path to get the parent. */
    @FlowKey(defaultValue = "\"{path}\"")
    private final T<String> path = new T<>(){};

    @Override
    public void modify(FunctionalNodeContainer n, FlowMap o) throws NodeException {
        String path = o.eval(this.path);
        try {
            String parent = new File(path).getParent();
            if(parent == null) parent = ".";
            o.output(output, parent);
        } catch (Exception e) {
            throw new NodeException(e, "Could not get parent");
        }
    }
}
