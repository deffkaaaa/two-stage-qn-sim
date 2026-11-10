package ua.kpi.modsim;

import java.util.List;

/**
 * Дослідження перехідного періоду (метод Велча): за кілька прогонів від порожнього стану
 * для кожного часового вікна обчислюється середнє значення U(S2) та L_S, після чого крива згладжується
 * ковзним середнім. Момент, з якого крива перестає зростати, і є кінцем перехідного періоду.
 */
public final class Transient {
    private Transient() {
    }

    /**
     * @param step      тривалість вікна, хв
     * @param time      середина кожного вікна, хв
     * @param uS2       середнє за прогонами U(S2) у вікні
     * @param ls        середнє за прогонами L_S у вікні
     * @param uS2Smooth згладжена крива U(S2)
     * @param lsSmooth  згладжена крива L_S
     * @param runs      кількість прогонів
     * @param suggested рекомендований перехідний період, хв (за згладженою кривою U(S2))
     * @param plateau   рівень, до якого виходить згладжена крива U(S2) наприкінці прогону
     * @param drift     збільшення U(S2) в останній чверті прогону порівняно з попередньою чвертю
     * @param lsDrift   те саме для L_S, у частках від попереднього значення
     */
    public record Curve(double step, double[] time, double[] uS2, double[] ls,
                        double[] uS2Smooth, double[] lsSmooth, int runs, double suggested, double plateau,
                        double drift, double lsDrift, long millis) {

        /** Чи крива ще помітно зростає наприкінці прогону (система не вийшла на сталий режим). */
        public boolean stillGrowing() {
            return drift > DRIFT_U || lsDrift > DRIFT_L;
        }
    }

    /** Крива вважається такою, що вийшла на рівень, якщо вона не нижча за (1 - REL) від рівня сталого режиму. */
    public static final double REL = 0.05;
    /** Пороги, за якими робиться висновок, що крива ще зростає в кінці прогону. */
    public static final double DRIFT_U = 0.01;
    public static final double DRIFT_L = 0.10;

    /**
     * @param cfg  дані запуску (використовуються T, кількість прогонів, seed; перехідний період ігнорується)
     * @param step тривалість вікна, хв
     * @param half напівширина ковзного середнього у вікнах (0 - без згладжування)
     */
    public static Curve run(Config cfg, double step, int half) {
        long start = System.nanoTime();
        int runs = cfg.deterministic() ? 1 : cfg.runs();
        int n = (int) Math.floor(cfg.tMod() / step);
        if (n < 3) {
            throw new IllegalArgumentException("Вікно занадто велике: має вміститися щонайменше 3 вікна в T.");
        }
        double[] u = new double[n];
        double[] l = new double[n];
        for (int r = 0; r < runs; r++) {
            Sampler smp = cfg.deterministic() ? new DeterministicSampler() : new RandomSampler(cfg.seed() + r);
            Model m = new Model(cfg.params(), smp, null);
            m.setSampleStep(step);
            m.run(cfg.tMod(), 0);
            List<Double> wu = m.windowUS2();
            List<Double> wl = m.windowLs();
            for (int i = 0; i < n && i < wu.size(); i++) {
                u[i] += wu.get(i) / runs;
                l[i] += wl.get(i) / runs;
            }
        }
        double[] t = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = (i + 0.5) * step;
        }
        double[] us = smooth(u, half);
        double[] ls = smooth(l, half);

        // рівень сталого режиму - середнє згладженої кривої в останній чверті прогону
        int from = (int) Math.floor(n * 0.75);
        int prev = (int) Math.floor(n * 0.5);
        double plateau = mean(us, from, n);
        double prevU = mean(us, prev, from);
        double lastL = mean(ls, from, n);
        double prevL = mean(ls, prev, from);
        // перехідний період - початок останнього проміжку, на якому крива не нижча за (1 - REL)·plateau
        int k = n;
        while (k > 0 && us[k - 1] >= (1 - REL) * plateau) {
            k--;
        }
        double suggested = k * step;
        double lsDrift = prevL > 0 ? (lastL - prevL) / prevL : 0;
        return new Curve(step, t, u, l, us, ls, runs, suggested, plateau, plateau - prevU, lsDrift,
                (System.nanoTime() - start) / 1_000_000);
    }

    private static double mean(double[] x, int from, int to) {
        double s = 0;
        for (int i = from; i < to; i++) {
            s += x[i];
        }
        return s / Math.max(1, to - from);
    }

    /** Центроване ковзне середнє (на краях вікно зменшується симетрично, як у методі Велча). */
    static double[] smooth(double[] x, int half) {
        int n = x.length;
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            int w = Math.min(half, Math.min(i, n - 1 - i));
            double s = 0;
            for (int j = i - w; j <= i + w; j++) {
                s += x[j];
            }
            y[i] = s / (2 * w + 1);
        }
        return y;
    }
}
