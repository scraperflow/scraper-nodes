package scraper.nodes.dev.io;

import scraper.annotations.node.FlowKey;
import scraper.annotations.node.NodePlugin;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.StreamNodeContainer;
import scraper.api.node.type.StreamNode;
import scraper.api.template.L;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.Consumer;

/**
 */
@NodePlugin("0.1.0")
public final class PathGlobFile implements StreamNode {

    /** Where the output file path will be put. If there's already a list at that key, it will be replaced. */
    @FlowKey(defaultValue = "\"file\"")
    private L<String> output = new L<>(){};

    @FlowKey(mandatory = true)
    private String glob;

    @FlowKey(mandatory = true)
    private String root;

    @FlowKey(defaultValue = "false")
    private Boolean includeRoot;

    @Override
    public void process(StreamNodeContainer n, FlowMap o) throws NodeException {
        n.collect(o, List.of(o.eval(output)));

        try {
            match(glob, root, matchedStringPath -> n.streamElement(o, output, matchedStringPath));
        } catch (IOException e) {
            throw new NodeException(e, "Path error");
        }
    }

    private void match(String glob, String location, Consumer<String> consumer) throws IOException {

        final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(
                glob);

        Files.walkFileTree(Paths.get(location), new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path path,
                                             BasicFileAttributes attrs) {
                if (pathMatcher.matches(path)) {
                    if(includeRoot) {
                        consumer.accept(path.toString());
                    } else {
                        consumer.accept(path.toString().substring(root.length()+1));
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
