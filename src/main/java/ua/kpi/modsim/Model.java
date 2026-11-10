package ua.kpi.modsim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Імітаційна модель двостадійної обробки деталей з браком (МСО з розділу 2).
 *
 * <p>Алгоритм: модельний час просувається до найближчої події; з одночасних подій
 * обирається випадкова; після кожної події виконуються всі переходи нульової тривалості
 * (метод {@link #settle()}), доки стан системи змінюється.
 */
public class Model {
    private final Params prm;
    private final Sampler smp;
    private final EventLog log;

    // стан і елементи мережі (рисунок 2.1)
    private double t = 0;
    private int idSeq = 0;
    private final PartQueue q0 = new PartQueue("Q0");
    private final PartQueue qR = new PartQueue("QR");
    private final PartQueue qS = new PartQueue("QS");
    private final Source source;
    private final Server p1, p2, s1, s2;
    private final Dispatcher d0, dS;
    private final List<Element> eventElements = new ArrayList<>();

    // лічильники за весь час (для балансу) та за період спостереження
    private long totArrived, totWaste, totOk, totDone;
    private long wArrived, wWaste, wOk, wFirstP1, wFirstP2;
    private double areaLs;
    private double windowStart;

    // облік за рівними часовими вікнами (для графіків перехідного періоду); не залежить від resetStatistics
    private double sampleStep = 0;
    private double winNext;
    private double winBusyS2, winAreaLs;
    private final List<Double> winUS2 = new ArrayList<>();
    private final List<Double> winLs = new ArrayList<>();

    public Model(Params prm, Sampler smp, EventLog log) {
        this.prm = prm;
        this.smp = smp;
        this.log = log;
        source = new Source(this, smp, prm.meanArrival());
        p1 = new Server("P1", "P1", this, smp, prm.meanP1(), this::afterP1);
        p2 = new Server("P2", "P2", this, smp, prm.meanP2(), this::afterP2);
        s1 = new Server("S1", "S", this, smp, prm.meanS(), this::afterS);
        s2 = new Server("S2", "S", this, smp, prm.meanS(), this::afterS);
        eventElements.addAll(List.of(source, p1, p2, s1, s2));

        d0 = new Dispatcher("D0", q0, this);
        d0.addRoute("P1", this::startP1, this::bP1);
        d0.addRoute("P2", this::startP2New, this::bP2);
        dS = new Dispatcher("DS", qS, this);
        dS.addRoute("S1", s1::start, this::bS1);
        dS.addRoute("S2", s2::start, this::bS2);
    }

    // ------------------------------------------------------------------
    // Умови блокування маршрутів, формули (2.1)-(2.7)
    // ------------------------------------------------------------------

    /** (2.1) L_S = q_S + g_S: деталі, що очікують на вторинну обробку (разом із деталлю в DS). */
    int lS() {
        return qS.size() + (dS.holds() ? 1 : 0);
    }

    /** (2.2) маршрут D0 -> P1 заблоковано, якщо P1 зайнятий. */
    boolean bP1() {
        return p1.isBusy();
    }

    /** (2.3) маршрут D0 -> P2 заблоковано, якщо P1 вільний, або P2 зайнятий, або в QR є деталі. */
    boolean bP2() {
        return !p1.isBusy() || p2.isBusy() || qR.size() > 0;
    }

    /** (2.4) повернення в QR заблоковано для деталі, що пройшла обробку вдруге. */
    boolean bRet(Part x) {
        return x.theta() == 2;
    }

    /** (2.5) маршрут до відходів заблоковано для деталі, що пройшла обробку вперше. */
    boolean bW(Part x) {
        return x.theta() == 1;
    }

    /** (2.6) маршрут DS -> S1 заблоковано, якщо S1 зайнятий. */
    boolean bS1() {
        return s1.isBusy();
    }

    /** (2.7) маршрут DS -> S2 заблоковано, якщо S1 вільний, або S2 зайнятий, або L_S <= H. */
    boolean bS2() {
        return !s1.isBusy() || s2.isBusy() || lS() <= prm.threshold();
    }

    // ------------------------------------------------------------------
    // Події та маршрутизація
    // ------------------------------------------------------------------

    double now() {
        return t;
    }

    void log(String s) {
        if (log != null) {
            log.println(s);
        }
    }

    /** Надходження нової деталі: стає в чергу Q0. */
    void onArrival() {
        Part x = new Part(++idSeq);
        q0.add(x);
        totArrived++;
        wArrived++;
        log("  " + x + ": надходження, черга Q0");
    }

    private void startP1(Part x) {
        wFirstP1++;
        p1.start(x);
    }

    private void startP2New(Part x) {
        wFirstP2++;
        p2.start(x);
    }

    /** Завершення обробки на P1: брак 0,04 -> QR (тип 2), інакше в накопичувач. */
    private void afterP1(Part x) {
        if (smp.defect("P1", prm.p1())) {
            x.setTheta(2);
            qR.add(x);
            log("  " + x + ": P1 -> брак, QR");
        } else {
            toStorage(x, "P1");
        }
    }

    /** Завершення обробки на P2: брак 0,08, далі маршрут за умовами (2.4), (2.5). */
    private void afterP2(Part x) {
        if (!smp.defect("P2", prm.p2())) {
            toStorage(x, "P2");
            return;
        }
        boolean toReturn = !bRet(x);
        boolean toWaste = !bW(x);
        if (toReturn == toWaste) {
            throw new IllegalStateException("Умови (2.4) і (2.5) мають бути взаємовиключними");
        }
        if (toReturn) {
            x.setTheta(2);
            qR.add(x);
            log("  " + x + ": P2 -> брак (перший), QR");
        } else {
            totWaste++;
            wWaste++;
            log("  " + x + ": P2 -> брак (повторний), ВІДХОДИ");
        }
    }

    private void toStorage(Part x, String from) {
        qS.add(x);
        totOk++;
        wOk++;
        log("  " + x + ": " + from + " -> придатна, накопичувач QS");
    }

    private void afterS(Part x) {
        totDone++;
        log("  " + x + ": вторинну обробку завершено");
    }

    /** Верстат P2 бере деталь із черги повторної обробки QR (пріоритет над новими деталями). */
    private boolean startP2FromQueue() {
        if (!p2.isBusy() && !qR.isEmpty()) {
            Part x = qR.poll();
            log("  " + x + ": QR -> P2 (повторна обробка)");
            p2.start(x);
            return true;
        }
        return false;
    }

    /** Усі переходи нульової тривалості, поки стан системи змінюється. */
    private void settle() {
        boolean changed;
        do {
            changed = startP2FromQueue();
            changed |= d0.step();
            changed |= dS.step();
        } while (changed);
    }

    // ------------------------------------------------------------------
    // Імітація
    // ------------------------------------------------------------------

    /** Вибір найближчої події; з одночасних - випадкова (пункт методички). */
    private Element chooseNext() {
        double min = Double.POSITIVE_INFINITY;
        for (Element e : eventElements) {
            min = Math.min(min, e.tNext());
        }
        List<Element> cand = new ArrayList<>();
        for (Element e : eventElements) {
            if (e.tNext() == min) {
                cand.add(e);
            }
        }
        Element chosen = cand.get(smp.pick(cand.size()));
        if (cand.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (Element e : cand) {
                sb.append(e.name()).append(' ');
            }
            log(String.format(Locale.ROOT, "t=%.3f: одночасні події { %s} -> спершу %s", min, sb, chosen.name()));
        }
        return chosen;
    }

    /** Просування часу на dt зі статистикою за станом, що діяв на цьому інтервалі (нерівні інтервали). */
    private void advance(double dt) {
        if (dt > 0) {
            for (Element e : eventElements) {
                e.doStatistics(dt);
            }
            q0.doStatistics(dt);
            qR.doStatistics(dt);
            qS.doStatistics(dt);
            areaLs += lS() * dt;
            if (sampleStep > 0) {
                sampleWindows(dt);
            }
            t += dt;
        }
    }

    /** Вмикає запис U(S2) та L_S окремо для кожного вікна тривалістю step (хв), починаючи з t = 0. */
    public void setSampleStep(double step) {
        this.sampleStep = step;
        this.winNext = step;
    }

    /** U(S2) за завершеними вікнами: частка часу, коли S2 був зайнятий. */
    public List<Double> windowUS2() {
        return winUS2;
    }

    /** L_S (середнє за час) за завершеними вікнами. */
    public List<Double> windowLs() {
        return winLs;
    }

    /** Розподіляє інтервал [t, t+dt] за вікнами; стан системи на інтервалі не змінюється. */
    private void sampleWindows(double dt) {
        double from = t;
        double to = t + dt;
        double busy = s2.isBusy() ? 1 : 0;
        double ls = lS();
        while (winNext <= to) {
            double seg = winNext - from;
            if (seg > 0) {
                winBusyS2 += busy * seg;
                winAreaLs += ls * seg;
            }
            winUS2.add(winBusyS2 / sampleStep);
            winLs.add(winAreaLs / sampleStep);
            winBusyS2 = 0;
            winAreaLs = 0;
            from = winNext;
            winNext += sampleStep;
        }
        double seg = to - from;
        if (seg > 0) {
            winBusyS2 += busy * seg;
            winAreaLs += ls * seg;
        }
    }

    private void resetStatistics() {
        for (Element e : eventElements) {
            e.resetStatistics();
        }
        q0.resetStatistics();
        qR.resetStatistics();
        qS.resetStatistics();
        areaLs = 0;
        wArrived = wWaste = wOk = wFirstP1 = wFirstP2 = 0;
        windowStart = t;
    }

    /**
     * Один прогін моделі.
     *
     * @param tMod   загальний час моделювання, хв
     * @param warmup тривалість перехідного періоду, хв (статистика до цього моменту відкидається)
     */
    public Results run(double tMod, double warmup) {
        source.start(0);
        boolean warmDone = warmup <= 0;
        windowStart = 0;
        log(String.format(Locale.ROOT, "Початок імітації: T=%.0f, перехідний період=%.0f, H=%d", tMod, warmup, prm.threshold()));
        while (true) {
            Element next = chooseNext();
            double tn = next.tNext();
            if (!warmDone && tn > warmup) {
                advance(warmup - t);
                resetStatistics();
                warmDone = true;
            }
            if (tn > tMod) {
                advance(tMod - t);
                break;
            }
            advance(tn - t);
            log(String.format(Locale.ROOT, "t=%.3f: %s", t, next.name()));
            next.outAct();
            settle();
            logState();
        }
        return collect(tMod - windowStart);
    }

    private String part(Part p) {
        return p == null ? "-" : p.toString();
    }

    private void logState() {
        log(String.format(Locale.ROOT,
                "     стан: Q0=%d D0=%s P1=%s P2=%s QR=%d | QS=%d DS=%s S1=%s S2=%s | L_S=%d",
                q0.size(), part(d0.held()), part(p1.current()), part(p2.current()), qR.size(),
                qS.size(), part(dS.held()), part(s1.current()), part(s2.current()), lS()));
    }

    private int inSystem() {
        return q0.size() + (d0.holds() ? 1 : 0) + (p1.isBusy() ? 1 : 0) + (p2.isBusy() ? 1 : 0) + qR.size()
                + qS.size() + (dS.holds() ? 1 : 0) + (s1.isBusy() ? 1 : 0) + (s2.isBusy() ? 1 : 0);
    }

    /** Баланс деталей за весь час: надійшло = відходи + оброблено + у системі. */
    public boolean balanceOk() {
        return totArrived == totWaste + totDone + inSystem();
    }

    private Results collect(double tObs) {
        double pw = (wWaste + wOk) > 0 ? (double) wWaste / (wWaste + wOk) : Double.NaN;
        double share = (wFirstP1 + wFirstP2) > 0 ? (double) wFirstP1 / (wFirstP1 + wFirstP2) : Double.NaN;
        return new Results(tObs, wArrived, wWaste, wOk,
                p1.utilization(tObs), p2.utilization(tObs), s1.utilization(tObs), s2.utilization(tObs),
                pw, q0.meanLength(tObs), qR.meanLength(tObs), qS.meanLength(tObs), areaLs / tObs,
                share, balanceOk());
    }
}
