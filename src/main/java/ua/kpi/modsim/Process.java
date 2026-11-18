package ua.kpi.modsim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * СМО формалізму (елемент Process): одноканальний пристрій обслуговування зі своєю чергою.
 * Максимальна довжина черги задається параметром (0 - СМО без черги, {@link Integer#MAX_VALUE} - необмежена).
 * Час обслуговування розподілено за експоненціальним законом із середнім meanTime (0 - миттєве обслуговування).
 * Після обслуговування заявка йде одним із відкритих маршрутів: якщо відкритих кілька - за імовірностями,
 * якщо жодного - залишається в каналі, і СМО чекає, поки відкриється якийсь маршрут.
 */
public class Process extends Element {
    private final Sampler smp;
    private final String stream;
    private final double meanTime;
    private final int maxQueue;
    private final ArrayDeque<Part> queue = new ArrayDeque<>();
    private final List<Route> routes = new ArrayList<>();
    private Part current;
    private double tService = Double.POSITIVE_INFINITY;

    private double busyTime;
    private double queueArea;
    private long inTotal;
    private final long[] inWindow = new long[3]; // за типом заявки θ (індекси 1 і 2)

    /**
     * @param stream потік випадкових чисел для часів обслуговування; СМО S1 і S2 мають спільний потік,
     *               тож k-та заявка, що починає вторинну обробку, має однаковий час за будь-якого H
     */
    public Process(String name, String stream, Model model, Sampler smp, double meanTime, int maxQueue) {
        super(name, model);
        this.stream = stream;
        this.smp = smp;
        this.meanTime = meanTime;
        this.maxQueue = maxQueue;
    }

    public void addRoute(Route r) {
        routes.add(r);
    }

    public boolean isBusy() {
        return current != null;
    }

    public Part current() {
        return current;
    }

    public int queueSize() {
        return queue.size();
    }

    // ------------------------------------------------------------------
    // Дії елемента
    // ------------------------------------------------------------------

    /** Надходження заявки: на обслуговування, якщо канал вільний, інакше в чергу. */
    @Override
    public void inAct(Part p) {
        inTotal++;
        inWindow[p.theta()]++;
        if (current == null) {
            startService(p);
        } else if (queue.size() < maxQueue) {
            queue.addLast(p);
        } else {
            throw new IllegalStateException("СМО " + name + " не може прийняти заявку " + p);
        }
    }

    /**
     * Момент найближчої події: кінець обслуговування. Якщо обслуговування завершене, а жоден маршрут
     * не відкритий (заявка заблокована в каналі), подій немає, доки умови блокування не зміняться.
     */
    @Override
    public double tNext() {
        if (current == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (model.now() < tService) {
            return tService;
        }
        return openRoutes(current).isEmpty() ? Double.POSITIVE_INFINITY : model.now();
    }

    /** Подія СМО: завершення обслуговування, вибір маршруту, передача заявки далі. */
    @Override
    public void outAct() {
        Part x = current;
        Route r = chooseRoute(x);
        current = null;
        tService = Double.POSITIVE_INFINITY;
        if (!queue.isEmpty()) {
            startService(queue.pollFirst());
        }
        model.log("  " + x + ": " + name + " -> " + r.target().name());
        if (r.action() != null) {
            r.action().accept(x);
        }
        r.target().inAct(x);
    }

    private void startService(Part x) {
        current = x;
        tService = model.now() + (meanTime > 0 ? smp.expo(stream, meanTime) : 0.0);
    }

    // ------------------------------------------------------------------
    // Маршрутизація
    // ------------------------------------------------------------------

    private List<Route> openRoutes(Part x) {
        List<Route> open = new ArrayList<>();
        for (Route r : routes) {
            if (!r.blocked().test(x)) {
                open.add(r);
            }
        }
        return open;
    }

    /** Вибір серед відкритих маршрутів за імовірностями (випадкове число витрачається, лише якщо вибір є). */
    private Route chooseRoute(Part x) {
        List<Route> open = openRoutes(x);
        if (open.isEmpty()) {
            throw new IllegalStateException("СМО " + name + ": немає відкритого маршруту для " + x);
        }
        if (open.size() == 1) {
            return open.get(0);
        }
        double rest = 0;
        for (Route r : open) {
            rest += r.prob();
        }
        if (open.size() == 2) {
            // два маршрути: другий обирається з імовірністю p2 / (p1 + p2) (брак), формула (2.9)
            return smp.defect(name, open.get(1).prob() / rest) ? open.get(1) : open.get(0);
        }
        for (int i = 0; i < open.size() - 1; i++) {
            if (smp.defect(name, open.get(i).prob() / rest)) {
                return open.get(i);
            }
            rest -= open.get(i).prob();
        }
        return open.get(open.size() - 1);
    }

    // ------------------------------------------------------------------
    // Статистика
    // ------------------------------------------------------------------

    @Override
    public void doStatistics(double dt) {
        if (current != null) {
            busyTime += dt;
        }
        queueArea += queue.size() * dt;
    }

    @Override
    public void resetStatistics() {
        busyTime = 0;
        queueArea = 0;
        inWindow[1] = inWindow[2] = 0;
    }

    /** Завантаження: частка часу, коли канал зайнятий, формули (2.10), (2.11). */
    public double utilization(double observedTime) {
        return observedTime > 0 ? busyTime / observedTime : 0;
    }

    /** Середня довжина черги за спостережений час (нерівні інтервали). */
    public double meanQueue(double observedTime) {
        return observedTime > 0 ? queueArea / observedTime : 0;
    }

    public long inTotal() {
        return inTotal;
    }

    /** Кількість заявок типу theta, що надійшли за період спостереження. */
    public long inWindow(int theta) {
        return inWindow[theta];
    }

    public long inWindow() {
        return inWindow[1] + inWindow[2];
    }
}
