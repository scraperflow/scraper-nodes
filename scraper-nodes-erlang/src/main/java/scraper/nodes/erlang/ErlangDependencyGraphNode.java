package scraper.nodes.erlang;

import scraper.annotations.node.FlowKey;
import scraper.annotations.node.Io;
import scraper.annotations.node.NodePlugin;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.FunctionalNodeContainer;
import scraper.api.node.container.NodeLogLevel;
import scraper.api.node.type.FunctionalNode;
import scraper.api.template.T;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@NodePlugin("0.0.1")
@Io
public final class ErlangDependencyGraphNode implements FunctionalNode {

    /** static */
    @FlowKey(defaultValue = "\"{static-mod-calls}\"")
    private final T<List<String>> staticCalls = new T<>(){};

    /** variable */
    @FlowKey(defaultValue = "\"{variable-mod-calls}\"")
    private final T<List<String>> variableCalls = new T<>(){};

    /** supervisor */
    @FlowKey(defaultValue = "\"{supervisor-calls}\"")
    private final T<List<String>> supervisorCalls = new T<>(){};

    /** modules */
    @FlowKey(defaultValue = "\"{modules}\"")
    private final T<List<String>> modules = new T<>(){};

    /** path */
    @FlowKey(defaultValue = "\"mods.dot\"")
    private final T<String> file = new T<>(){};

    @Override
    public void modify(FunctionalNodeContainer n, FlowMap o) throws NodeException {

        List<String> scalls = o.eval(staticCalls);
        List<String> vcalls = o.eval(variableCalls);
        List<String> supcalls = o.eval(supervisorCalls);
        List<String> mods = o.eval(modules);

        n.log(NodeLogLevel.INFO, "Static: {}", scalls);
        n.log(NodeLogLevel.INFO, "Variable: {}", vcalls);
        n.log(NodeLogLevel.INFO, "Supervisor: {}", supcalls);


        String graph = visualize(o, mods, scalls, vcalls, supcalls);

        try (PrintWriter writer = new PrintWriter(new FileOutputStream(new File(o.eval(file)), false))) {
            writer.write(graph);
        } catch (Exception e) {
            throw new NodeException(e, "Could not create output graph");
        }
    }


    @SafeVarargs
    private String visualize(FlowMap o, List<String> mods, List<String>... calls) {
        StringBuilder b = new StringBuilder();

        StringBuilder vargraph = new StringBuilder();
        write(vargraph, subgraphTemplateA);

        write(b, "digraph G {");

        Arrays.stream(calls)
                .flatMap(List::stream)
                .forEach(arrow -> {
                    String source = arrow.split("->")[0];
                    String target = arrow.split("->")[1];
                    if(mods.contains(target)) {
                        write(b, arrow);
                    } else {
                        // DEFINE?
                        Optional<?> define = o.get(target);
                        if(define.isPresent()) {
                            write(b, source+"->"+define.get());
                        } else {
                            // starts with upper case, variable call?
                            if(Character.isUpperCase(target.charAt(0))) {
                                write(b, arrow);
                                write(vargraph, target);
                            }
                        }
                    }
                });

        write(vargraph, subgraphTemplateB);

        write(b, vargraph.toString());
        write(b, "}");

        return b.toString();
    }

    private static void write(StringBuilder graph, String line) {
        graph.append(line).append("\n");
    }

    private static final String subgraphTemplateA = "\t\tsubgraph \"cluster_variable_mods\" {\n" +
            "\t\t\tstyle=filled;\n" +
            "\t\t\tcolor=lightgrey;\n" +
            "\t\t\tnode [style=filled,color=white];\n"
            ;

    private static final String subgraphTemplateB =
            "\t\t\tlabel = \"variable mod calls\";\n" +
            "\t\t}";
}
