package scraper.nodes.dev.io;

import scraper.annotations.*;
import scraper.api.*;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static scraper.api.NodeLogLevel.ERROR;
import static scraper.api.NodeLogLevel.INFO;

/**
 * Map function for each file, async refill of tasks
 */
@NodePlugin("0.2.0")
@Io
@Stateful
public final class MapFolder implements FunctionalNode {

    /** Folder to watch */
    @FlowKey(mandatory = true) @EnsureFile @Argument
    private String folder;

    /** Target for each file */
    @FlowKey(mandatory = true)
    @Flow(label = "map")
    private Address fileTarget;

    /** File name */
    @FlowKey
    private final L<String> putFileName = new L<>(){};


    Thread watcher;
    boolean refresh = true;
    final Object notify = new Object();

    final Set<File> toProcess = new HashSet<>();
    final Set<File> processed = new HashSet<>();

    @Override
    public void modify(@NotNull FunctionalNodeContainer n, @NotNull FlowMap o) {
        synchronized (this) {
            refresh = true;
            maybeStartWatcher(n, o);
            synchronized (notify) {
                notify.notifyAll();
            }
        }
    }

    private void maybeStartWatcher(FunctionalNodeContainer n, FlowMap o) {
        if(watcher == null) {
            watcher = new Thread(() -> {
                while(true) {
                    while(refresh) {
                        refresh = false;
                        // refresh and remove already processed files
                        File[] files = new File(folder).listFiles();
                        assert files != null;
                        toProcess.addAll(Set.of(files));
                        toProcess.removeAll(processed);

                        n.log(INFO, "Processing {0} new files", toProcess.size());

                        // process new files
                        toProcess.forEach(newFile -> {
                            FlowMap copy = o.copy();
                            copy.output(putFileName, newFile.getName());
                            n.forward(copy, fileTarget);
                        });
                        processed.addAll(toProcess);
                        toProcess.clear();
                    }

                    synchronized (notify) {
                        try {
                            notify.wait();
                        } catch (InterruptedException e) {
                            n.log(ERROR, "Terminating folder watch thread: ", e.getMessage());
                            throw new RuntimeException(e);
                        }
                    }
                }
            });
            watcher.setDaemon(false);
            watcher.start();
        }
    }
}