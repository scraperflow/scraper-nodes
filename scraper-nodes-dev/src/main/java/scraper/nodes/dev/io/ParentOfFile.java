package scraper.nodes.dev.io;

import scraper.annotations.*;
import scraper.api.*;

import java.io.File;

/**
 * Returns the parent of a file
 * Example:
 * <pre>
 * type: ParentOfFileNode
 * </pre>
 */
@NodePlugin("0.2.0")
@Io
public final class ParentOfFile implements FunctionalNode {

    /** path to get the parent. */
    @FlowKey(mandatory = true)
    private final T<String> path = new T<>(){};

    /** Parent of a file. */
    @FlowKey(mandatory = true)
    private final L<String> output = new L<>(){};

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
