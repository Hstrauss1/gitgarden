package garden;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Entry point: loads garden.fxml and shows the window. */
public class GardenApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(GardenApp.class.getResource("garden.fxml"));
        Parent root = loader.load();
        GardenController controller = loader.getController();
        controller.setHostServices(getHostServices());

        stage.setTitle("Computerized Bonsai Garden");
        stage.setScene(new Scene(root));
        stage.setOnHidden(e -> controller.shutdown());
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        // last line of defence: anything that escapes is logged, never shown as a crash
        Thread.setDefaultUncaughtExceptionHandler((thread, problem) ->
                GardenLog.error(GardenLog.Category.SYSTEM, "Uncaught on thread " + thread.getName(), problem));
        launch(args);
    }
}
