package garden;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The in-app user manual. It reads help.md from the classpath and shows its headings,
 * bullets and paragraphs. **Bold** and `code` spans are rendered too.
 */
public final class HelpWindow {

    private static Stage stage;

    private HelpWindow() {
    }

    /** Opens the manual, or brings it to the front if it is already open. */
    public static void show(Window owner) {
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("Bonsai Garden: Help");
            if (owner != null) stage.initOwner(owner);
            ScrollPane scroll = new ScrollPane(build());
            scroll.setFitToWidth(true);
            scroll.getStyleClass().add("help-scroll");
            Scene scene = new Scene(scroll, 720, 760);
            scene.getStylesheets().add(HelpWindow.class.getResource("garden.css").toExternalForm());
            stage.setScene(scene);
        }
        stage.show();
        stage.toFront();
    }

    private static VBox build() {
        VBox page = new VBox(6);
        page.getStyleClass().add("help-page");
        for (String line : readManual().split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("## ")) {
                page.getChildren().add(styled(new Label(trimmed.substring(3)), "help-h2"));
            } else if (trimmed.startsWith("# ")) {
                page.getChildren().add(styled(new Label(trimmed.substring(2)), "help-h1"));
            } else if (trimmed.startsWith("- ")) {
                TextFlow flow = inline("•  " + trimmed.substring(2));
                flow.getStyleClass().add("help-bullet");
                page.getChildren().add(flow);
            } else {
                TextFlow flow = inline(trimmed);
                flow.getStyleClass().add("help-paragraph");
                page.getChildren().add(flow);
            }
        }
        return page;
    }

    private static Label styled(Label label, String styleClass) {
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        return label;
    }

    /** Splits a line on ** and ` markers into plain, bold and code text. */
    private static TextFlow inline(String line) {
        TextFlow flow = new TextFlow();
        StringBuilder current = new StringBuilder();
        boolean bold = false;
        boolean code = false;
        for (int i = 0; i < line.length(); i++) {
            if (line.startsWith("**", i)) {
                addRun(flow, current, bold, code);
                bold = !bold;
                i++;
            } else if (line.charAt(i) == '`') {
                addRun(flow, current, bold, code);
                code = !code;
            } else {
                current.append(line.charAt(i));
            }
        }
        addRun(flow, current, bold, code);
        return flow;
    }

    private static void addRun(TextFlow flow, StringBuilder text, boolean bold, boolean code) {
        if (text.length() == 0) return;
        Text run = new Text(text.toString());
        run.getStyleClass().add(code ? "help-code" : bold ? "help-bold" : "help-text");
        flow.getChildren().add(run);
        text.setLength(0);
    }

    private static String readManual() {
        try (InputStream in = HelpWindow.class.getResourceAsStream("help.md")) {
            if (in == null) return "# Help\nThe manual (help.md) is missing from this build.";
            StringBuilder text = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
            return text.toString();
        } catch (IOException e) {
            GardenLog.error(GardenLog.Category.SYSTEM, "Reading help.md", e);
            return "# Help\nThe manual could not be read: " + e.getMessage();
        }
    }
}
