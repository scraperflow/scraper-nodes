package scraper.nodes.dev.io;

import scraper.annotations.node.EnsureFile;
import scraper.annotations.node.FlowKey;
import scraper.annotations.node.NodePlugin;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.StreamNodeContainer;
import scraper.api.node.type.StreamNode;
import scraper.api.reflect.T;
import scraper.util.NodeUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 */
@NodePlugin("0.1.0")
public final class ReadChunkAndFilterNode implements StreamNode {

    /** Input file path */
    @FlowKey(mandatory = true) @EnsureFile
    private final T<String> inputFile = new T<>(){};

    /** Where the output line will be put */
    @FlowKey(defaultValue = "\"output\"", output = true)
    private T<String> output = new T<>(){};

    @FlowKey(defaultValue = "\"ISO_8859_1\"")
    private String charset;

    @FlowKey
    private String filter;

    @FlowKey
    private Integer includeAfterMatch;

    @Override
    public void process(StreamNodeContainer n, FlowMap o) throws NodeException {
        String file = o.eval(inputFile);
        n.collect(o, List.of(String.valueOf(output.getTerm().getRaw())));

        try(BufferedReader reader = new BufferedReader(new FileReader(file, Charset.forName(charset)))) {

            int current = -1;
            StringBuilder builder = new StringBuilder();

            String line = reader.readLine();

            while (line != null) {
//                if(!line.contains(filter) && current == -1) {
//                    // does not contain filter
//                    // no include after match, skip
                if(line.contains(filter)) {
                    // contains filter, possibly resets builder
                    builder = new StringBuilder();
                    current = includeAfterMatch;
                    builder.append(line).append("\n");
                } else if(current >= 0) {
                    // no match, but leftover to add
                    builder.append(line).append("\n");
                    current--;

                    // finished
                    if(current == 0) {
                        current--;

                        FlowMap out = o.copy();
                        out.output(output, builder.toString());
                        n.streamFlowMap(o, out);
                    }
                }

                line = reader.readLine();

            }
        } catch (IOException e) {
            throw new NodeException(e, "Failed: " + e.getMessage());
        }
    }
}
