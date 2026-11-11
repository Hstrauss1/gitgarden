package garden;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * The garden's log: every event goes both to a plain-text file in {@code logs/} and to
 * an in-memory list that the Log tab shows. Each line has fixed-width columns:
 *
 * <pre>
 * [real date and time] [in-game date] LEVEL CATEGORY  message
 * [2026-11-05 14:03:22] [Y1 Spr M01 D03 08:40] INFO  WATERING  Plot 14: sprinkler ON (soil 28%)
 * </pre>
 *
 * Logging never throws: if the file can't be written, the log keeps working in memory
 * and the garden keeps running.
 */
public final class GardenLog {

    /** How serious an entry is. */
    public enum Level {
        /** Normal activity: sprinklers, harvests, planting. */
        INFO,
        /** Something needs attention: wilting, a pest arrived, a tank is low. */
        WARN,
        /** Something bad happened: a tree died, frost, a pest is eating. */
        ALERT,
        /** A software problem that was caught and handled; the garden kept running. */
        ERROR
    }

    /** Which part of the garden an entry is about. */
    public enum Category {
        SYSTEM, TIME, WEATHER, WATERING, HEATING, PEST, REPLANT, PLANT, WILDLIFE, INTRUDER, USER
    }

    /** One log line. */
    public static final class Entry {
        public final String realTime;
        public final String gameTime;
        public final Level level;
        public final Category category;
        public final String message;

        Entry(String realTime, String gameTime, Level level, Category category, String message) {
            this.realTime = realTime;
            this.gameTime = gameTime;
            this.level = level;
            this.category = category;
            this.message = message;
        }

        public String toLine() {
            return String.format("[%s] [%s] %-5s %-9s %s", realTime, gameTime, level, category, message);
        }

        @Override
        public String toString() {
            return toLine();
        }
    }

    /** The Log tab keeps this many recent entries; the file keeps everything. */
    public static final int MEMORY_LIMIT = 5000;
    /** Start a new file part after this many bytes so no single file gets huge. */
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private static final ObservableList<Entry> RECENT = FXCollections.observableArrayList();
    private static final SimpleDateFormat REAL_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat FILE_STAMP = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    private static GameClock clock;
    private static BufferedWriter out;
    private static File directory;
    private static File currentFile;
    private static String baseName;
    private static long bytesWritten;
    private static int part = 1;
    private static int errorCount;

    private GardenLog() {
    }

    /** Opens a new log file in {@code logs/} next to where the program was started. */
    public static synchronized void start(GameClock gameClock) {
        clock = gameClock;
        directory = new File(System.getProperty("user.dir"), "logs");
        baseName = "garden_" + FILE_STAMP.format(new Date());
        openFile();
        info(Category.SYSTEM, "Bonsai Farm started. Log file: %s", currentFile == null ? "(unavailable)" : currentFile.getAbsolutePath());
    }

    public static void info(Category category, String format, Object... args) {
        log(Level.INFO, category, format, args);
    }

    public static void warn(Category category, String format, Object... args) {
        log(Level.WARN, category, format, args);
    }

    public static void alert(Category category, String format, Object... args) {
        log(Level.ALERT, category, format, args);
    }

    /** Logs a caught exception with where it happened, so it can be traced afterwards. */
    public static void error(Category category, String where, Throwable problem) {
        errorCount++;
        StackTraceElement[] trace = problem.getStackTrace();
        String at = trace.length > 0 ? " at " + trace[0] : "";
        log(Level.ERROR, category, "%s failed and was recovered: %s%s", where, problem, at);
    }

    public static synchronized void log(Level level, Category category, String format, Object... args) {
        String message;
        try {
            message = args.length == 0 ? format : String.format(format, args);
        } catch (RuntimeException badFormat) {
            message = format;
        }
        final Entry entry = new Entry(REAL_TIME.format(new Date()), gameTime(), level, category, message);
        write(entry.toLine());

        if (Platform.isFxApplicationThread()) {
            remember(entry);
        } else {
            try {
                Platform.runLater(() -> remember(entry));
            } catch (IllegalStateException toolkitNotRunning) {
                // no UI yet (or any more); the file still has it
            }
        }
    }

    /** A divider line in the file, used for daily reports so the log is easy to skim. */
    public static synchronized void divider(String title) {
        write("");
        write("==================== " + title + " ====================");
    }

    /** Pushes buffered lines to disk; called about once a second. */
    public static synchronized void flush() {
        if (out == null) return;
        try {
            out.flush();
        } catch (IOException e) {
            disableFile(e);
        }
    }

    public static synchronized void stop() {
        info(Category.SYSTEM, "Bonsai Farm closed.");
        flush();
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        out = null;
    }

    public static ObservableList<Entry> getRecent() { return RECENT; }
    public static File getCurrentFile() { return currentFile; }
    public static File getDirectory() { return directory; }
    public static int getErrorCount() { return errorCount; }

    private static void remember(Entry entry) {
        RECENT.add(entry);
        if (RECENT.size() > MEMORY_LIMIT) {
            RECENT.remove(0, RECENT.size() - MEMORY_LIMIT);
        }
    }

    private static String gameTime() {
        if (clock == null) return "----------------------";
        String[] seasons = {"Spr", "Sum", "Aut", "Win"};
        int hour = (int) clock.getHour();
        int minute = (int) (clock.getHour() % 1 * 60);
        return String.format("Y%d %s M%02d D%02d %02d:%02d", clock.getYear(),
                seasons[clock.getSeason().ordinal()], clock.getMonth(), clock.getDayOfMonth(), hour, minute);
    }

    private static void write(String line) {
        if (out == null) return;
        try {
            out.write(line);
            out.newLine();
            bytesWritten += line.length() + 1;
            if (bytesWritten > MAX_FILE_BYTES) {
                out.close();
                part++;
                openFile();
            }
        } catch (IOException e) {
            disableFile(e);
        }
    }

    private static void openFile() {
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("can't create " + directory);
            }
            currentFile = new File(directory, baseName + (part > 1 ? "_part" + part : "") + ".log");
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(currentFile), StandardCharsets.UTF_8));
            bytesWritten = 0;
            for (String line : HEADER) {
                write(line);
            }
        } catch (IOException e) {
            disableFile(e);
        }
    }

    private static void disableFile(IOException e) {
        out = null;
        System.err.println("Garden log file disabled, keeping the log in memory only: " + e);
    }

    /** Printed at the top of every log file so it can be read without the manual. */
    private static final String[] HEADER = {
        "BONSAI FARM LOG",
        "",
        "Each line:  [real date & time] [in-game date] LEVEL CATEGORY  message",
        "  in-game date:  Y<year> <season> M<month> D<day> <hour:minute>   (1 in-game day = 10 s at 1x speed)",
        "  LEVEL:     INFO  normal activity        WARN  needs attention",
        "             ALERT something bad happened ERROR a software problem that was caught; the garden kept running",
        "  CATEGORY:  SYSTEM    start/stop, speed, errors     TIME      new day/month/season/year, daily reports",
        "             WEATHER   rain, drought, windstorm      WATERING  moisture sensors, sprinklers, valves",
        "             HEATING   temperature sensor, heaters   PEST      pest sensors, pesticide spraying",
        "             REPLANT   auto-replanting dead trees    PLANT     planting, growth, wilting, death, harvest",
        "             WILDLIFE  cockroaches arriving          INTRUDER  Hellfire Rider and Green Brute events",
        "             USER      everything a person clicked",
        "Tip: search for 'ALERT' to find problems, or '====' to jump between daily reports.",
        "------------------------------------------------------------------------------------------------",
    };
}
