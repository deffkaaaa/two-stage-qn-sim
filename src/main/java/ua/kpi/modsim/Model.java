package ua.kpi.modsim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Імітаційна модель двофазної обробки деталей з браком як мережа масового обслуговування (розділ 2).
 *
 * <p>Мережа складається з джерела I0, шести СМО (D0 з чергою Q0, P1, P2 з чергою QR, DS з чергою QS, S1, S2)
 * і двох виходів (W, F). Маршрути задано між СМО з імовірностями та умовами блокування.
 *
 * <p>Алгоритм: модельний час просувається до найближчої події tNext серед елементів; подія - це
 * {@link Element#outAct()} одного елемента, однаково реалізована для всіх елементів. З одночасних подій
 * обирається випадкова (у детермінованому режимі - перша за порядком).
 */
public class Model {
    private final Params prm;
    private final Sampler smp;
    private final EventLog log;

    // стан і елементи мережі (рисунок 2.1)
    private double t = 0;
    private int idSeq = 0;
    private final Create source;
    private final Process d0, p1, p2, dS, s1, s2;
    private final Dispose waste, finished;
    /** Порядок важливий для детермінованого режиму: спершу СМО з нульовим часом, далі решта. */
    private final List<Element> elements = new ArrayList<>();

    // лічильники за період спостереження та облік середньої L_S
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

        source = new Create("I0", this, smp, prm.meanArrival());
        d0 = new Process("D0", "D0", this, smp, 0, Integer.MAX_VALUE);   // Q0 + D0, t = 0
        p1 = new Process("P1", "P1", this, smp, prm.meanP1(), 0);         // без черги
        p2 = new Process("P2", "P2", this, smp, prm.meanP2(), Integer.MAX_VALUE); // з чергою QR
        dS = new Process("DS", "DS", this, smp, 0, Integer.MAX_VALUE);   // QS + DS, t = 0
        s1 = new Process("S1", "S", this, smp, prm.meanS(), 0);
        s2 = new Process("S2", "S", this, smp, prm.meanS(), 0);
        waste = new Dispose("W", this);
        finished = new Dispose("F", this);
        elements.addAll(List.of(d0, dS, source, p1, p2, s1, s2, waste, finished));

        source.setNext(d0);
        d0.addRoute(Route.to(p1, 1).blockedIf(this::bP1));
        d0.addRoute(Route.to(p2, 1).blockedIf(this::bP2));
        p1.addRoute(Route.to(dS, 1 - prm.p1()));
        p1.addRoute(Route.to(p2, prm.p1()).doing(x -> x.setTheta(2)));
        p2.addRoute(Route.to(dS, 1 - prm.p2()));
        p2.addRoute(Route.to(p2, prm.p2()).blockedIf(this::bRet).doing(x -> x.setTheta(2)));
        p2.addRoute(Route.to(waste, prm.p2()).blockedIf(this::bW));
        dS.addRoute(Route.to(s1, 1).blockedIf(this::bS1));
        dS.addRoute(Route.to(s2, 1).blockedIf(this::bS2));
        s1.addRoute(Route.to(finished, 1));
        s2.addRoute(Route.to(finished, 1));
    }

    // ------------------------------------------------------------------
    // Умови блокування маршрутів, формули (2.1)-(2.7)
    // ------------------------------------------------------------------

    /** (2.1) L_S = q_S + g_S: заявки, що очікують на вторинну обробку (разом із заявкою в каналі DS). */
    int lS() {
        return dS.queueSize() + (dS.isBusy() ? 1 : 0);
    }

    /** (2.2) маршрут D0 -> P1 заблоковано, якщо P1 зайнятий. */
    boolean bP1(Part x) {
        return p1.isBusy();
    }

    /** (2.3) маршрут D0 -> P2 заблоковано, якщо P1 вільний, або P2 зайнятий, або в QR є деталі. */
    boolean bP2(Part x) {
        return !p1.isBusy() || p2.isBusy() || p2.queueSize() > 0;
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
    boolean bS1(Part x) {
        return s1.isBusy();
    }

    /** (2.7) маршрут DS -> S2 заблоковано, якщо S1 вільний, або S2 зайнятий, або L_S <= H. */
    boolean bS2(Part x) {
        return !s1.isBusy() || s2.isBusy() || lS() <= prm.threshold();
    }

    double now() {
        return t;
    }

    Part newPart() {
        return new Part(++idSeq);
    }

    void log(String s) {
        if (log != null) {
            log.println(s);
        }
    }

    // ------------------------------------------------------------------
    // Імітація
    // ------------------------------------------------------------------

    /** Вибір найближчої події; з одночасних - випадкова (пункт методички). */
    private Element chooseNext() {
        double min = Double.POSITIVE_INFINITY;
        for (Element e : elements) {
            min = Math.min(min, e.tNext());
        }
        List<Element> cand = new ArrayList<>();
        for (Element e : elements) {
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

    /** Просування часу до моменту to зі статистикою за станом, що діяв на інтервалі (нерівні інтервали). */
    private void advance(double to) {
        double dt = to - t;
        if (dt > 0) {
            for (Element e : elements) {
                e.doStatistics(dt);
            }
            areaLs += lS() * dt;
            if (sampleStep > 0) {
                sampleWindows(dt);
            }
            t = to;
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
        for (Element e : elements) {
            e.resetStatistics();
        }
        areaLs = 0;
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
                advance(warmup);
                resetStatistics();
                warmDone = true;
            }
            if (tn > tMod) {
                advance(tMod);
                break;
            }
            advance(tn);
            log(String.format(Locale.ROOT, "t=%.3f: %s", t, next.name()));
            next.outAct();
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
                d0.queueSize(), part(d0.current()), part(p1.current()), part(p2.current()), p2.queueSize(),
                dS.queueSize(), part(dS.current()), part(s1.current()), part(s2.current()), lS()));
    }

    private int inSystem() {
        int n = 0;
        for (Process p : List.of(d0, p1, p2, dS, s1, s2)) {
            n += p.queueSize() + (p.isBusy() ? 1 : 0);
        }
        return n;
    }

    /** Баланс деталей за весь час: надійшло = відходи + оброблено + у системі. */
    public boolean balanceOk() {
        return source.total() == waste.total() + finished.total() + inSystem();
    }

    private Results collect(double tObs) {
        long nOk = dS.inWindow();
        long nW = waste.window();
        double pw = (nW + nOk) > 0 ? (double) nW / (nW + nOk) : Double.NaN;
        long first1 = p1.inWindow(1);
        long first2 = p2.inWindow(1);
        double share = (first1 + first2) > 0 ? (double) first1 / (first1 + first2) : Double.NaN;
        return new Results(tObs, source.window(), nW, nOk,
                p1.utilization(tObs), p2.utilization(tObs), s1.utilization(tObs), s2.utilization(tObs),
                pw, d0.meanQueue(tObs), p2.meanQueue(tObs), dS.meanQueue(tObs), areaLs / tObs,
                share, balanceOk());
    }
}
