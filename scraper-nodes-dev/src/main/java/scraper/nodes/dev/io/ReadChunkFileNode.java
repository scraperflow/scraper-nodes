package scraper.nodes.dev.io;

import scraper.annotations.NotNull;
import scraper.annotations.node.EnsureFile;
import scraper.annotations.node.FlowKey;
import scraper.annotations.node.Io;
import scraper.annotations.node.NodePlugin;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.StreamNodeContainer;
import scraper.api.node.type.StreamNode;
import scraper.api.template.L;
import scraper.api.template.T;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Reads an input file in chunks.
 * Splits after <var>splitAfterLines</var> many lines read.
 */
@NodePlugin("0.1.0")
@Io
public final class ReadChunkFileNode implements StreamNode {

    /** Input file path */
    @FlowKey(mandatory = true) @EnsureFile
    private final T<String> inputFile = new T<>(){};

    /** Where the output line will be put */
    @FlowKey(defaultValue = "\"output\"")
    private final L<String> output = new L<>(){};

    /** Charset of the file */
    @FlowKey(defaultValue = "\"ISO_8859_1\"")
    private String charset;

    /** Split after this many lines */
    @FlowKey(defaultValue = "1000")
    private Integer splitAfterLines;

    @Override
    public void process(@NotNull StreamNodeContainer n, @NotNull FlowMap o) {
        String file = o.eval(inputFile);
        n.collect(o, List.of(o.evalLocation(output)));

        try(BufferedReader reader = new BufferedReader(new FileReader(file, Charset.forName(charset)))) {
            StringBuilder splitContent = new StringBuilder();

            int current = 1;

            String line = reader.readLine();

            while (line != null) {
                splitContent.append(line).append("\n");
                if (current > splitAfterLines) {
                    FlowMap out = o.copy();
                    out.output(output, splitContent.toString());
                    n.streamFlowMap(o, out);
                    splitContent = new StringBuilder();
                    current = 0;
                }

                line = reader.readLine();

                current++;
            }

            if (splitContent.length() > 0) {
                FlowMap out = o.copy();
                out.output(output, splitContent.toString());
                n.streamFlowMap(o, out);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
