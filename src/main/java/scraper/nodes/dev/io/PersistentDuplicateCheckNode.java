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
import java.util.Optional;

/**
 * Checks string duplicates persistently
 *
 * @author Albert Schimpf
 */
@NodePlugin("0.2.0")
public final class PersistentDuplicateCheckNode implements FunctionalNode {

    /** file path */
    @FlowKey(mandatory = true) @EnsureFile
    private String persistentStore;

    @FlowKey(defaultValue = "\"{content}\"")
    private T<String> content = new T<>(){};

    @FlowKey(defaultValue = "\"exists\"")
    private final L<Boolean> result = new L<>(){};

    @FlowKey
    private final T<String> appendIfNotFound = new T<>(){};

    @Override
    public void modify(@NotNull FunctionalNodeContainer n, @NotNull FlowMap o) throws NodeException {
        String line = o.eval(content);
        try {

            Optional<String> maybeAppend = o.evalMaybe(appendIfNotFound);
            if(maybeAppend.isPresent()) {
                String append = maybeAppend.get();
                boolean existedBefore = n.getJobInstance().getFileService().ifNoLineEqualsFoundAppend(persistentStore, line, () -> append);

                if(existedBefore) {
                    o.output(result, true);
                } else {
                    o.output(result, false);
                }
            }

        } catch (IOException e) {
            throw new NodeException(e, "Could not access IO");
        }
    }
}