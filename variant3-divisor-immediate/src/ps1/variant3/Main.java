package ps1.variant3;

import ps1.shared.RunSupport;

public class Main {
    public static void main(String[] args) {
        // Select this variant. Config and worker coordination are shared.
        RunSupport.run(args, true, true);
    }
}


