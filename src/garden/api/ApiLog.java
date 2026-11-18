package garden.api;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import garden.GardenLog.Level;

/**
 * Writes the API's log.txt: one line per event, in fixed columns, flushed straight away
 * so nothing is lost if the program is stopped. Never throws; if the file can't be
 * written the lines still go to standard output.
 *
 * <pre>
 * 2026-11-17 14:00:00 | DAY 01 | INFO  | RAIN             | 25 mm              | 40/40 | Rain soaked every bed ...
 * </pre>
 */
final class ApiLog {

    /** Supplies the "plants alive" column. */
    interface Counter {
        String aliveOfTotal();
    }

    private static final String[] HEADER = {
        "GARDEN SIMULATION API LOG",
        "",
        "Each line:  real time | DAY dd | LEVEL | EVENT | EVENT_VALUE | PLANTS_ALIVE (alive/total) | details",
        "  DAY:    simulated day. Day 1 starts at initializeGarden(); each real hour is one day.",
        "  LEVEL:  INFO  normal activity          WARN  needs attention (bad input, a plant is suffering)",
        "          ALERT a plant died             and a fourth level for a software problem that was caught",
        "                                         (the garden kept running); a clean run has none of those",
        "  EVENT:  INIT, CONFIG             the garden was planted (and which plants)",
        "          RAIN, TEMPERATURE,       calls made by the test script, with the value it passed",
        "          PARASITE, GET_PLANTS",
        "          PEST_CONTROL             the pest control module found or cleared an infestation",
        "          CLIMATE                  the climate module switched bed heating or shade cloth on or off",
        "          DRAINAGE                 waterlogged beds had their drains opened",
        "          PLANT                    a plant is wilting, recovered, or died (and why)",
        "          END_OF_DAY               daily summary: health, infestations, sprinklers, water used",
        "          RAIN_RESET, TEMPERATURE_RESET   rain and temperature return to normal when the day ends",
        "          STATE                    getState(): the full report on every plant",
        "Tip: search for 'ALERT' to find plants that died, and 'END_OF_DAY' to step through the days.",
        "-----------------------------------------------------------------------------------------------------------",
    };

    private final File file;
    private final boolean echo;
    private final SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private BufferedWriter out;
    private Counter counter;
    private int errors;
    private int warnings;
    private int alerts;

    /**
     * Starts a fresh log file (an older one is replaced).
     *
     * @param echo also print every line to standard output
     */
    ApiLog(File file, boolean echo) {
        this.file = file;
        this.echo = echo;
        try {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("can't create " + parent);
            }
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8));
            for (String line : HEADER) {
                out.write(line);
                out.newLine();
            }
            out.flush();
        } catch (IOException problem) {
            out = null;
            System.err.println("log.txt could not be opened, logging to the console only: " + problem);
        }
    }

    void setCounter(Counter counter) {
        this.counter = counter;
    }

    void info(int day, String event, String value, String details) {
        log(Level.INFO, day, event, value, details);
    }

    void warn(int day, String event, String value, String details) {
        log(Level.WARN, day, event, value, details);
    }

    void alert(int day, String event, String value, String details) {
        log(Level.ALERT, day, event, value, details);
    }

    /** Logs a caught exception, with its stack trace on the following lines. */
    void error(int day, String where, Throwable problem) {
        StringWriter trace = new StringWriter();
        problem.printStackTrace(new PrintWriter(trace));
        log(Level.ERROR, day, "ERROR", where, problem + " (recovered; the garden kept running)");
        raw(trace.toString().trim());
    }

    synchronized void log(Level level, int day, String event, String value, String details) {
        if (level == Level.ERROR) errors++;
        if (level == Level.WARN) warnings++;
        if (level == Level.ALERT) alerts++;
        String alive = "-";
        try {
            if (counter != null) alive = counter.aliveOfTotal();
        } catch (RuntimeException ignored) {
            // the count is a nicety; never let it stop a log line
        }
        raw(String.format("%s | DAY %02d | %-5s | %-17s | %-20s | %5s | %s",
                time.format(new Date()), day, level, event, value == null ? "-" : value, alive,
                details == null ? "" : details));
    }

    /** A line exactly as given (blank lines and the getState report's indented rows). */
    synchronized void raw(String line) {
        line = line.replaceAll("[ \\t]+(?=\\R|$)", "");   // no trailing spaces from padded columns
        if (echo) System.out.println(line);
        if (out == null) return;
        try {
            out.write(line);
            out.newLine();
            out.flush();
        } catch (IOException problem) {
            out = null;
            System.err.println("log.txt stopped working, logging to the console only: " + problem);
        }
    }

    synchronized void close() {
        if (out == null) return;
        try {
            out.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        out = null;
    }

    File getFile() { return file; }
    synchronized int getErrors() { return errors; }
    synchronized int getWarnings() { return warnings; }
    synchronized int getAlerts() { return alerts; }
}
