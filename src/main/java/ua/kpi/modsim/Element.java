package ua.kpi.modsim;

/** Елемент мережі, що породжує події: має момент найближчої події tNext. */
public abstract class Element {
    protected final String name;
    protected double tNext = Double.POSITIVE_INFINITY;

    protected Element(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public double tNext() {
        return tNext;
    }

    /** Дія в момент tNext: завершення обробки або надходження деталі. */
    public abstract void outAct();

    /** Накопичення статистики за інтервал dt зі сталим станом. */
    public void doStatistics(double dt) {
    }

    public void resetStatistics() {
    }
}
