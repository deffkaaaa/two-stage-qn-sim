package ua.kpi.modsim;

import java.util.function.Consumer;

/** Одноканальний пристрій без власної черги (верстат P1, P2, S1, S2) з обліком часу зайнятості. */
public class Server extends Element {
    private final Model model;
    private final Sampler smp;
    private final double meanTime;
    private final String stream;
    private final Consumer<Part> onFinish;
    private Part current;
    private double busyTime;

    /**
     * @param stream потік випадкових чисел для тривалостей обробки; верстати S1 і S2 мають спільний потік,
     *               тож k-та деталь, що починає вторинну обробку, має однакову тривалість за будь-якого H
     */
    public Server(String name, String stream, Model model, Sampler smp, double meanTime, Consumer<Part> onFinish) {
        super(name);
        this.stream = stream;
        this.model = model;
        this.smp = smp;
        this.meanTime = meanTime;
        this.onFinish = onFinish;
    }

    public boolean isBusy() {
        return current != null;
    }

    public Part current() {
        return current;
    }

    /** Початок обробки деталі: тривалість exp(meanTime). */
    public void start(Part p) {
        if (current != null) {
            throw new IllegalStateException("Верстат " + name + " вже зайнятий");
        }
        current = p;
        tNext = model.now() + smp.expo(stream, meanTime);
    }

    @Override
    public void outAct() {
        Part done = current;
        current = null;
        tNext = Double.POSITIVE_INFINITY;
        onFinish.accept(done);
    }

    @Override
    public void doStatistics(double dt) {
        if (current != null) {
            busyTime += dt;
        }
    }

    @Override
    public void resetStatistics() {
        busyTime = 0;
    }

    /** Завантаження (частка часу зайнятості) за спостережений час, формули (2.10), (2.11). */
    public double utilization(double observedTime) {
        return observedTime > 0 ? busyTime / observedTime : 0;
    }
}
