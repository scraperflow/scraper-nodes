package scraper.nodes.dev.io;

import scraper.annotations.*;
import scraper.api.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Reads an input file in chunks.
 * Reads until the <var>filter</var> applies,
 * then includes that line and <var>includeAfterMatch</var> many lines
 * as one stream output String.
 */
@NodePlugin("0.1.0")
@Io
public final class ReadChunkAndFilter implements StreamNode {

    /** Input file path */
    @FlowKey(mandatory = true) @EnsureFile
    private final T<String> inputFile = new T<>(){};

    /** Charset of the file */
    @FlowKey(defaultValue = "\"UTF-8\"")
    private String charset;

    /** Line contains filter check */
    @FlowKey
    private String filter;

    /** How many lines after the matching line to include */
    @FlowKey
    private Integer includeAfterMatch;

    /** Where the output line will be put */
    @FlowKey(mandatory = true)
    private final L<String> output = new L<>(){};

    @Override
    public void process(@NotNull StreamNodeContainer n, @NotNull FlowMap o) throws NodeException {
        String file = o.eval(inputFile);

        try(BufferedReader reader = new BufferedReader(new FileReader(file, Charset.forName(charset)))) {

            int current = -1;
            StringBuilder builder = new StringBuilder();

            String line = reader.readLine();

            while (line != null) {
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
