package scraper.nodes.dev.io;

import scraper.annotations.*;
import scraper.api.*;

import java.io.IOException;

/**
 * Checks string duplicates persistently
 */
@NodePlugin("0.2.0")
@Io
public final class PersistentDuplicateCheck implements FunctionalNode {

    /** File path to the store used to check duplicates */
    @FlowKey(mandatory = true) @EnsureFile
    private String persistentStore;

    /** Content to check in the store */
    @FlowKey(mandatory = true)
    private final T<String> content = new T<>(){};

    /** Append this content if not found in store */
    @FlowKey
    private final T<String> appendIfNotFound = new T<>(){};

    /** Where the result is stored */
    @FlowKey(mandatory = true)
    private final L<Boolean> result = new L<>(){};

    @Override
    public void modify(@NotNull FunctionalNodeContainer n, @NotNull FlowMap o) throws NodeException {
        String line = o.eval(content);
        try {

            String maybeAppend = o.eval(appendIfNotFound);
            if(maybeAppend != null) {
                boolean existedBefore = n.getJobInstance().getFileService().ifNoLineEqualsFoundAppend(persistentStore, line, () -> maybeAppend);

                o.output(result, existedBefore);
            }

        } catch (IOException e) {
            throw new NodeException(e, "Could not access IO");
        }
    }
}