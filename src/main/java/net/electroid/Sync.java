package net.electroid;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Main class of the application to sync
 * files and directories between a local hard disk
 * and the remote Dropbox folders.
 *
 * Exceptions are passed up easily and will crash the
 * program to avoid illegal states. This makes it easier
 * to identify problems in a Docker environment.
 *
 * Highly recommended to run as a "sidecar" in a Kubernetes
 * environment to sync changes to a volume from Dropbox and back.
 */
public class Sync {

    /**
     * The main method of the syncing application.
     * @param args required arguments for running the application.
     *        [0] = access token with write access for Dropbox.
     *        [1] = absolute path where files should be downloaded to locally.
     *        [2] = absolute path where files should be downloaded from remotely.
     * @throws InterruptedException when the main thread cannot be paused.
     */
    public static void main(String[] args) throws InterruptedException {
        Location.setRoot(Paths.get(args[1]), Paths.get(args[2]));
        Location root = Location.root();
        List<Thread> threads = new ArrayList<>();
        System.out.println("Starting Dropbox sync...");
        System.out.println(" > Version...  " + "1.0.1");
        System.out.println(" > Remote...   " + root.remote().toString());
        System.out.println(" > Local...    " + root.local().toString());
        System.out.print(" > Batch...    ");
        threads.add(watch(() -> System.out.print(new Client(args[0]).downloadBatch(root) + "\n")));
        wait(threads, false);
        System.out.println("Starting Dropbox monitoring...");
        threads.add(watch(() -> new Client(args[0]).push(root)));
        threads.add(watch(() -> new Client(args[0]).pull(root)));
        wait(threads, true);
        System.out.println("Restarting...");
        main(args);
    }

    /**
     * Pause the main thread until all the given threads are finished.
     * @param threads list of threads to yield for until finished.
     * @param backoff whether the system should back off and try again.
     * @throws InterruptedException when the main thread cannot be paused.
     */
    private static void wait(List<Thread> threads, boolean backoff) throws InterruptedException {
        while(threads.stream().allMatch(Thread::isAlive)) {
            Thread.sleep(1000);
        }
        threads.clear();
        if(backoff) {
            System.out.println();
            System.out.println("Backing off...");
            Thread.sleep(10000);
        }
    }

    /**
     * Create a new thread given a runnable and start it in the background.
     * @param block the block of code to run.
     * @return the currently running thread.
     */
    private static Thread watch(Runnable block) {
        Thread thread = new Thread(block::run);
        thread.start();
        return thread;
    }

    /**
     * A simple java.lang.Runnable interface
     * that allows errors to be thrown as runtime exceptions.
     */
    interface Runnable {
        void execute() throws Throwable;
        default void run() {
            try {
                execute();
            } catch(Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

}
