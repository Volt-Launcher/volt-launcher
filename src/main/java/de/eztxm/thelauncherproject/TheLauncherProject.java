package de.eztxm.thelauncherproject;

import de.eztxm.thelauncherproject.rest.RestServer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class TheLauncherProject extends Application {

    private RestServer restServer;

    @Override
    public void start(Stage stage) throws Exception {
        restServer = new RestServer(7070);
        restServer.start();

        Thread.sleep(500L);

        WebView webView = createConfiguredWebView(stage, false);
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

    static void main(String[] args) {
        launch(TheLauncherProject.class, args);
    }

    private WebView createConfiguredWebView(Stage stage, boolean popupWindow) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        engine.setCreatePopupHandler(config -> createPopupWindow(stage));

        if (popupWindow) {
            engine.setOnVisibilityChanged(event -> {
                if (!event.getData()) {
                    stage.close();
                }
            });
        }

        return webView;
    }

    private WebEngine createPopupWindow(Stage owner) {
        Stage popupStage = new Stage();
        popupStage.initOwner(owner);
        popupStage.setTitle("Microsoft Login");

        WebView popupView = createConfiguredWebView(popupStage, true);
        Scene popupScene = new Scene(popupView, 520.0, 760.0);
        popupStage.setScene(popupScene);
        popupStage.show();

        return popupView.getEngine();
    }
}
