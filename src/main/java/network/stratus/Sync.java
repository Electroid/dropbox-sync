package network.stratus;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Sync {

    public static void main(String[] args) throws InterruptedException {
        assert args.length >= 3;
        Location.setRoot(Paths.get(args[1]), Paths.get(args[2]));
        Location root = Location.root();
        List<Thread> threads = new ArrayList<>();
        System.out.println("Starting Dropbox sync...");
        System.out.println(" > Version...  " + "1.0");
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

    private static Thread watch(Runnable block) {
        Thread thread = new Thread(block::run);
        thread.start();
        return thread;
    }

    interface Runnable {
        void execute() throws Throwable;
        default void run() {
            try {
                execute();
            } catch(Throwable t) {
                t.printStackTrace();
            }
        }
    }

}
