package ps1.variant1;

import java.util.List;
import java.util.ArrayList;

public class PrintingScheme {
    private final boolean immediate; 
    private final List<String> messageBuffer;

    public PrintingScheme(boolean immediate) {
        this.immediate = immediate;
        this.messageBuffer = new ArrayList<String>();
    }

    public synchronized void report(String message) {
        if (this.immediate) {
            System.out.println(message);
        } else {
            messageBuffer.add(message);
        }
    }

    public void displayBuffer() {
        /**
         * Not synchronized because this is not expected to be printed by threads.
         * Only the main thread runs this after all threads are joined.
         * 
         */

        if (immediate) {
            System.out.println("Error: Immediate mode is used, no buffer is present.");
            return;
        }

        for (String message : messageBuffer) {
            System.out.println(message);
        }
    }
}


