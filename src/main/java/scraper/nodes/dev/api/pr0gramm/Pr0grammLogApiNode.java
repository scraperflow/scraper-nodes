package scraper.nodes.dev.api.pr0gramm;

import scraper.annotations.node.FlowKey;
import scraper.annotations.node.NodePlugin;
import scraper.api.exceptions.NodeException;
import scraper.api.flow.FlowMap;
import scraper.api.node.container.FunctionalNodeContainer;
import scraper.api.node.container.NodeLogLevel;
import scraper.api.node.type.FunctionalNode;
import scraper.api.reflect.T;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

/**
 * Converts an encoded log string to personal metadata
 */
@NodePlugin("0.1.0")
public class Pr0grammLogApiNode implements FunctionalNode {
    /** Encoded log string */
    @FlowKey(defaultValue = "\"{log}\"")
    private final T<String> logString = new T<>(){};

    /** Item favorites are saved here in a ID list if specified */
    @FlowKey(output = true)
    private final T<List<Long>> itemFavorites = new T<>(){};

    /** Comment favorites are saved here in a ID list if specified */
    @FlowKey(output = true)
    private final T<List<Long>> commentFavorites = new T<>(){};


    /** Item likes are saved here in a ID list */
    @FlowKey(output = true)
    private final T<List<Long>> itemLikes = new T<>(){};

    /** Comment likes are saved here in a ID list */
    @FlowKey(output = true)
    private final T<List<Long>> commentLikes = new T<>(){};

    /** Tag likes are saved here in a ID list */
    @FlowKey(output = true)
    private final T<List<Long>> tagLikes = new T<>(){};


    /** Item dislikes are saved here in a ID list */
    @FlowKey(output = true)
    private final T<List<Long>> itemDislikes = new T<>(){};

    /** Comment dislikes are saved here in a ID list */
    @FlowKey(output = true)
    private final T<List<Long>> commentDislikes = new T<>(){};

    /** Tag dislikes are saved here in a ID list */
    @FlowKey(output = true)
    private final T<List<Long>> tagDislikes = new T<>(){};

    /** Used to decode Base64 content */
    private final Base64.Decoder base64 = Base64.getDecoder();

    @Override
    public void modify(FunctionalNodeContainer n, FlowMap o) {
        String encodedMessage = o.eval(logString);

        // Decode Base64
        ByteBuffer byteData = ByteBuffer.wrap(base64.decode(encodedMessage));
        byteData.order(ByteOrder.LITTLE_ENDIAN);

        // API: 32bit ID, 8bit Action
        // ACTION:
        // 1 | 2 | 3 | 10 -> -1 | 0 | +1 | +2 (items)
        // 4 | 5 | 6 | 11 -> -1 | 0 | +1 | +2 (comments)
        // 7 | 8 | 9 -> -1 | 0 | +1 (tags)

        List<Long> iLikes = new LinkedList<>();
        List<Long> iDislikes = new LinkedList<>();
        List<Long> iFavorites = new LinkedList<>();

        List<Long> cLikes = new LinkedList<>();
        List<Long> cDislikes = new LinkedList<>();
        List<Long> cFavorites = new LinkedList<>();

        List<Long> tLikes = new LinkedList<>();
        List<Long> tDislikes = new LinkedList<>();

        for (int i = 0; i < byteData.capacity()/5; i++) {
            long id = byteData.getInt(i*5);
            int action = byteData.get(i*5 + 4);

            switch (action) {
                case 1: iDislikes.add(id); break;
                case 2: break;
                case 3: iLikes.add(id); break;
                case 10: iFavorites.add(id); break;

                case 4: cDislikes.add(id); break;
                case 5: break;
                case 6: cLikes.add(id); break;
                case 11: cFavorites.add(id); break;

                case 7: tDislikes.add(id); break;
                case 9: tLikes.add(id); break;
                default:
                    n.log(NodeLogLevel.WARN, "Unknown action: {}. Has the API changed?", action);
            }
        }

        insertIfNotNull(itemFavorites, iFavorites, o);
        insertIfNotNull(itemLikes, iLikes, o);
        insertIfNotNull(itemDislikes, iDislikes, o);

        insertIfNotNull(commentFavorites, cFavorites, o);
        insertIfNotNull(commentLikes, cLikes, o);
        insertIfNotNull(commentDislikes, cDislikes, o);

        insertIfNotNull(tagLikes, tLikes, o);
        insertIfNotNull(tagDislikes, tDislikes, o);
    }

    private void insertIfNotNull(T<List<Long>> key, List<Long> result, FlowMap o) {
        List<Long> outt = o.eval(key);
        if(outt != null) o.output(key, result);
    }

}
