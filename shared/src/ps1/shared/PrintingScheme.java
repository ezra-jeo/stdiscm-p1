package ps1.shared;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

public class PrintingScheme {
    private final boolean immediate; 
    private final List<PrintingSchemeObject> messageBuffer;

    public PrintingScheme(boolean immediate) {
        this.immediate = immediate;
        this.messageBuffer = new ArrayList<PrintingSchemeObject>();
    }

    public synchronized void report(PrintingSchemeObject message) {
        if (this.immediate) {
            System.out.println("Thread " + message.threadId()
                    + " found prime number " + message.prime() + "!"
                    + " Timestamp: " + message.timestamp());
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
        messageBuffer.sort(Comparator.comparing(PrintingSchemeObject::prime));
        for (PrintingSchemeObject message : messageBuffer) {
            System.out.println("Found prime " + message.prime() + "!");
        }
    }
}

