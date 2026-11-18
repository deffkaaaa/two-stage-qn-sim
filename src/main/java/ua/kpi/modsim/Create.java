package ua.kpi.modsim;

/** Створення заявок (елемент Create формалізму): джерело I0 з інтервалами exp(meanArrival). */
public class Create extends Element {
    private final Sampler smp;
    private final double meanArrival;
    private Element next;
    private double tNext = Double.POSITIVE_INFINITY;
    private long total;
    private long window;

    public Create(String name, Model model, Sampler smp, double meanArrival) {
        super(name, model);
        this.smp = smp;
        this.meanArrival = meanArrival;
    }

    public void setNext(Element next) {
        this.next = next;
    }

    /** Планує першу заявку. */
    public void start(double t0) {
        tNext = t0 + smp.expo(name, meanArrival);
    }

    @Override
    public double tNext() {
        return tNext;
    }

    @Override
    public void inAct(Part p) {
        throw new UnsupportedOperationException("У джерело заявки не надходять");
    }

    @Override
    public void outAct() {
        Part x = model.newPart();
        total++;
        window++;
        model.log("  " + x + ": " + name + " -> " + next.name());
        tNext = model.now() + smp.expo(name, meanArrival);
        next.inAct(x);
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
