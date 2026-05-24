package download.manager;

import download.manager.view.View;

public class Main {
    public static void main(String[] args) {
        // launch() is a static method from Application
        // it starts JavaFX and calls View's start() method
        View.launch(View.class, args);
    }
}