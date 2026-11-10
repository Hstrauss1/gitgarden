package garden;

import java.util.ArrayList;
import java.util.List;

/** One overhead irrigation pipe: it runs along a row of plots and has a valve. */
public class Pipe {

    private final String name;
    private final List<Plot> plots = new ArrayList<Plot>();
    private boolean open = true;

    public Pipe(String name) {
        this.name = name;
    }

    public void add(Plot plot) {
        plots.add(plot);
    }

    public void toggle() {
        open = !open;
    }

    public String getName() { return name; }
    public List<Plot> getPlots() { return plots; }
    public boolean isOpen() { return open; }
}
