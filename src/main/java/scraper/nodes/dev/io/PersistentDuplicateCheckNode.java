package scraper.nodes.dev.io;

import scraper.annotations.NotNull;
import scraper.annotations.node.EnsureFile;
import scraper.annotations.node.FlowKey;
import scraper.annotations.node.NodePlugin;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.core.AbstractNode;
import scraper.core.Template;

import java.io.IOException;

/**
 * Checks string duplicates persistently
 *
 * @author Albert Schimpf
 */
@NodePlugin("1.0.0")
public final class PersistentDuplicateCheckNode extends AbstractNode {

    /** file path */
    @FlowKey(mandatory = true) @EnsureFile
    private String persistentStore;

    @FlowKey(defaultValue = "\"{content}\"")
    private Template<String> content = new Template<>(){};

    @FlowKey(defaultValue = "\"exists\"", output = true) @NotNull
    private final Template<Boolean> result = new Template<>(){};

    @Override @NotNull
    public FlowMap process(@NotNull final FlowMap o) throws NodeException {
        String line = content.eval(o);
        try {
            String check = getJobPojo().getFileService().getFirstLineStartsWith(persistentStore, line);

            if(check == null) {
                result.output(o, false);
            } else {
                result.output(o, true);
            }
        } catch (IOException e) {
            throw new NodeException(e, "Could not access IO");
        }

        return forward(o);
    }
}