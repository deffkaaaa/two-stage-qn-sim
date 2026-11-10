package ua.kpi.modsim;

import java.io.PrintStream;

/** Протокол подій імітації (використовується для верифікації, додаток В). */
public class EventLog {
    private final PrintStream out;

    public EventLog(PrintStream out) {
        this.out = out;
    }

    public void println(String s) {
        out.println(s);
    }
}
