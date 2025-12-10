package de.eztxm.thelauncherproject;

import de.eztxm.thelauncherproject.rest.RestServer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class TheLauncherProject extends Application {

    private RestServer restServer;

    @Override
    public void start(Stage stage) throws Exception {
        restServer = new RestServer(7070);
        restServer.start();

        Thread.sleep(500L);

        WebView webView = new WebView();
        webView.getEngine().load("http://localhost:7070/");

        Scene scene = new Scene(webView, 1280.0, 720.0);
        stage.setTitle("TheLauncherProject");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        if (restServer != null) {
            restServer.stop();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(TheLauncherProject.class, args);
    }
}
