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

import static scraper.api.node.container.NodeLogLevel.ERROR;

/**
 */
@NodePlugin("1.0.0")
public final class ReadChunkFileNode implements StreamNode {

    /** Input file path */
    @FlowKey(mandatory = true) @EnsureFile
    private final T<String> inputFile = new T<>(){};

    /** Where the output line will be put */
    @FlowKey(defaultValue = "\"output\"", output = true)
    private T<String> output = new T<>(){};

    @FlowKey(defaultValue = "\"ISO_8859_1\"")
    private String charset;

    @FlowKey(defaultValue = "1000")
    private Integer split;

    @Override
    public void process(StreamNodeContainer n, FlowMap o) throws NodeException {
        String file = o.eval(inputFile);
        n.collect(o, List.of(output.getRawJson()));

        try(BufferedReader reader = new BufferedReader(new FileReader(file, Charset.forName(charset)))) {
            StringBuilder splitContent = new StringBuilder();

            int current = 1;
            String line = reader.readLine();
            while (line != null) {
                try {
                    splitContent.append(line).append("\n");
                    if (current > split) {
                        FlowMap out = NodeUtil.flowOf(o);
                        out.output(output, splitContent.toString());
                        n.stream(o, out);
                        splitContent = new StringBuilder();
                        current = 0;
                    }

                    line = reader.readLine();
                } catch (Exception e) { // can happen if the line(s) to be added causes overflow of the StringBuilder
                    n.log(ERROR, "Skipping line: {}", e.getMessage());
                    skipLine(reader);
                }

                current++;
            }

            if (splitContent.length() > 0) {
                FlowMap out = NodeUtil.flowOf(o);
                out.output(output, splitContent.toString());
                n.stream(o, out);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void skipLine(BufferedReader br) throws IOException {
        while(true) {
            int c = br.read();
            if(c == -1 || c == '\n')
                return;
            if(c == '\r') {
                br.mark(1);
                c = br.read();
                if(c != '\n')
                    br.reset();
                return;
            }
        }
    }
}
