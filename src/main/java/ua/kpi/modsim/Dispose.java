package ua.kpi.modsim;

/** Вихід із мережі (елемент Dispose формалізму): знищує заявки та рахує їх. */
public class Dispose extends Element {
    private long total;
    private long window;

    public Dispose(String name, Model model) {
        super(name, model);
    }

    @Override
    public double tNext() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public void inAct(Part p) {
        total++;
        window++;
    }

    @Override
    public void outAct() {
        throw new UnsupportedOperationException("Вихід не має власних подій");
    }

    public long total() {
        return total;
    }

    public long window() {
        return window;
    }

    @Override
    public void resetStatistics() {
        window = 0;
    }
}
