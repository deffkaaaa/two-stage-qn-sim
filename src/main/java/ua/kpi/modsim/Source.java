package ua.kpi.modsim;

/** Джерело надходження деталей I0: інтервали exp(meanArrival). */
public class Source extends Element {
    private final Model model;
    private final Sampler smp;
    private final double meanArrival;

    public Source(Model model, Sampler smp, double meanArrival) {
        super("I0");
        this.model = model;
        this.smp = smp;
        this.meanArrival = meanArrival;
    }

    public void start(double t0) {
        tNext = t0 + smp.expo("I0", meanArrival);
    }

    @Override
    public void outAct() {
        model.onArrival();
        tNext = model.now() + smp.expo("I0", meanArrival);
    }
}
