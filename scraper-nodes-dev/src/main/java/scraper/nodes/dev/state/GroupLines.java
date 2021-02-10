package scraper.nodes.dev.state;

import scraper.annotations.node.FlowKey;
import scraper.annotations.node.NodePlugin;
import scraper.annotations.node.Stateful;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.StreamNodeContainer;
import scraper.api.node.type.StreamNode;
import scraper.api.template.L;
import scraper.api.template.T;

/**
 * Groups lines of Strings.
 * Can forward when encountering an end of group delimiter.
 */
@NodePlugin("0.0.1")
@Stateful
public final class GroupLines implements StreamNode {

    /** Line to group */
    @FlowKey(mandatory = true)
    private final T<String> line = new T<>(){};

    /** Group by line count */
    @FlowKey(defaultValue = "10")
    private Integer groupSize;

    /** End of group delimiter, emits last group if line count is not reached */
    @FlowKey(mandatory = true)
    private final T<String> endOfGroup = new T<>(){};

    /** Where the output grouped line will be put */
    @FlowKey(defaultValue = "\"_\"")
    private final L<String> grouped = new L<>(){};

    /** Join lines with this string. Can be empty. */
    @FlowKey(defaultValue = "\"\\n\"")
    private String join;

    final StringBuilder group = new StringBuilder();
    int lines = 0;

    @Override
    public void process(StreamNodeContainer n, FlowMap o) throws NodeException {
        String endOfGroup = o.eval(this.endOfGroup);
        String line = o.eval(this.line);

        synchronized (group) {
            if(line.equals(endOfGroup)) {
                System.out.println("END OF GROUP REACHED");
                if(!group.isEmpty()) {
                    System.out.println("EMITTING last");
                }

                lines = 0;
                group.setLength(0);
                return;
            }

            lines ++;
            group.append(line);

            if(lines >= groupSize) {
                System.out.println("EMITTING group");
                n.streamElement(o, grouped, group.toString());
                group.setLength(0);
                lines = 0;
            }
        }
    }
}
