package scraper.nodes.dev.io;

import scraper.annotations.NotNull;
import scraper.annotations.node.EnsureFile;
import scraper.annotations.node.FlowKey;
import scraper.annotations.node.NodePlugin;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.FunctionalNodeContainer;
import scraper.api.node.type.FunctionalNode;
import scraper.api.template.L;
import scraper.api.template.T;

import java.io.IOException;

/**
 * Checks string duplicates persistently
 *
 * @author Albert Schimpf
 */
@NodePlugin("0.1.0")
public final class PersistentDuplicateCheckNode implements FunctionalNode {

    /** file path */
    @FlowKey(mandatory = true) @EnsureFile
    private String persistentStore;

    @FlowKey(defaultValue = "\"{content}\"")
    private T<String> content = new T<>(){};

    @FlowKey(defaultValue = "\"exists\"")
    private final L<Boolean> result = new L<>(){};

    @Override
    public void modify(@NotNull FunctionalNodeContainer n, @NotNull FlowMap o) throws NodeException {
        String line = o.eval(content);
        try {
            String check = n.getJobInstance().getFileService().getFirstLineStartsWith(persistentStore, line);

            if(check == null) {
                o.output(result, false);
            } else {
                o.output(result, true);
            }
        } catch (IOException e) {
            throw new NodeException(e, "Could not access IO");
        }
    }
}