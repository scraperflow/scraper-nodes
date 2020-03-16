package scraper.nodes.dev.io;

import scraper.annotations.NotNull;
import scraper.annotations.node.FlowKey;
import scraper.annotations.node.Io;
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
 * Provides a path glob starting from a given root and streams all matches.
 * Example:
 * <pre>
 * type: PathGlobFileNode
 * root: .
 * glob: "glob:**&#47;*.java"
 * </pre>
 */
@NodePlugin("0.1.0")
@Io
public final class PathGlobFileNode implements StreamNode {

    /** Where the output file path will be put. */
    @FlowKey(defaultValue = "\"file\"")
    private L<String> output = new L<>(){};

    /** Syntax and pattern, see Javas PathMatcher.getPathMatcher documentation. */
    @FlowKey(mandatory = true)
    private String glob;

    /** The root folder from where to start */
    @FlowKey(mandatory = true)
    private String root;

    /** Includes the root as a match or not */
    @FlowKey(defaultValue = "false")
    private Boolean includeRoot;

    @Override
    public void process(@NotNull StreamNodeContainer n, @NotNull FlowMap o) throws NodeException {
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
