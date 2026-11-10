package garden;

/**
 * In-game calendar: simulated seconds turn into hours, days, months, seasons and years.
 * One day lasts {@link #SECONDS_PER_DAY} simulated seconds; months are 30 days and a year is 12 months.
 */
public class GameClock {

    public static final double SECONDS_PER_DAY = 10;
    public static final int DAYS_PER_MONTH = 30;
    public static final int MONTHS_PER_YEAR = 12;
    public static final int DAYS_PER_YEAR = DAYS_PER_MONTH * MONTHS_PER_YEAR;

    /** Each season nudges weather, growth and pests. */
    public enum Season {
        //        label     rain  drought  wind  growth  drain  roaches
        SPRING   ("Spring",  3,    1,       1,    1.15,   0.9,   1.0),
        SUMMER   ("Summer",  1,    3,       1,    1.0,    1.25,  1.6),
        AUTUMN   ("Autumn",  1,    1,       3,    0.85,   1.0,   1.0),
        WINTER   ("Winter",  1,    0,       2,    0.45,   0.6,   0.3);

        public final String label;
        /** Relative chances of each weather event in this season. */
        public final int rainWeight;
        public final int droughtWeight;
        public final int windWeight;
        /** Multiplies how fast trees grow. */
        public final double growth;
        /** Multiplies how fast soil dries out. */
        public final double drain;
        /** Multiplies how often cockroaches show up. */
        public final double roaches;

        Season(String label, int rainWeight, int droughtWeight, int windWeight,
               double growth, double drain, double roaches) {
            this.label = label;
            this.rainWeight = rainWeight;
            this.droughtWeight = droughtWeight;
            this.windWeight = windWeight;
            this.growth = growth;
            this.drain = drain;
            this.roaches = roaches;
        }
    }

    // start at 8am on the first day of spring
    private double days = 8 / 24.0;

    public void advance(double seconds) {
        days += seconds / SECONDS_PER_DAY;
    }

    /** How many in-game hours pass in this many simulated seconds. */
    public static double hoursIn(double seconds) {
        return seconds / SECONDS_PER_DAY * 24;
    }

    /** Total days since the farm started, with the fraction of the current day. */
    public double getDays() { return days; }

    public int getYear() { return (int) (days / DAYS_PER_YEAR) + 1; }

    /** 1..12 */
    public int getMonth() { return (int) (days % DAYS_PER_YEAR / DAYS_PER_MONTH) + 1; }

    /** 1..30 */
    public int getDayOfMonth() { return (int) (days % DAYS_PER_MONTH) + 1; }

    /** 0..24 */
    public double getHour() { return days % 1 * 24; }

    /** Months 1-3 are spring, 4-6 summer, 7-9 autumn, 10-12 winter. */
    public Season getSeason() {
        return Season.values()[(getMonth() - 1) / 3];
    }

    /** 0 at noon, 1 at midnight, easing through dusk and dawn. */
    public double getDarkness() {
        double h = getHour();
        double fromNoon = Math.abs(h - 12) / 12;      // 0 at noon, 1 at midnight
        return Math.max(0, Math.min(1, (fromNoon - 0.45) / 0.4));
    }

    public String format() {
        int hour = (int) getHour();
        int minute = (int) (getHour() % 1 * 60) / 10 * 10;
        return String.format("Year %d  ·  %s  ·  Month %d, Day %d  ·  %02d:%02d",
                getYear(), getSeason().label, getMonth(), getDayOfMonth(), hour, minute);
    }

    /** A readable age for something that started on {@code startDay}. */
    public String describeAge(double startDay) {
        int age = (int) (days - startDay);
        int years = age / DAYS_PER_YEAR;
        int months = age % DAYS_PER_YEAR / DAYS_PER_MONTH;
        int d = age % DAYS_PER_MONTH;
        if (years > 0) return years + "y " + months + "m";
        if (months > 0) return months + "m " + d + "d";
        return d + (d == 1 ? " day" : " days");
    }
}
