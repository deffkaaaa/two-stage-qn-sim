package ua.kpi.modsim;

/**
 * Необхідна кількість прогонів для заданої точності ε за довірчої імовірності β = 0,95.
 *
 * <p>Основна оцінка - за формулою (3.5) методички (наслідок нерівності Чебишева): n = σ² / (ε² (1 - β)) = 20 σ² / ε².
 * Для порівняння наводиться менш обережна оцінка за розподілом Стьюдента: найменше n, для якого t·σ/√n ≤ ε.
 */
public final class Precision {
    public static final double BETA = 0.95;

    private Precision() {
    }

    /**
     * @param metric  вихідна змінна
     * @param pilot   статистика пілотної серії
     * @param eps     бажана абсолютна точність оцінювання
     * @param needed  необхідна кількість прогонів за формулою (3.5)
     * @param neededT необхідна кількість прогонів за розподілом Стьюдента
     */
    public record Row(Metric metric, Stats.Summary pilot, double eps, int needed, int neededT) {
        public boolean enough() {
            return pilot.n() >= needed;
        }
    }

    public static Row row(Metric m, Experiment.Series pilot, double eps) {
        Stats.Summary s = pilot.summary(m);
        return new Row(m, s, eps, neededChebyshev(s.sd(), eps), neededStudent(s.sd(), eps));
    }

    /** Формула (3.5): n = σ² / (ε² (1 - β)). */
    public static int neededChebyshev(double sd, double eps) {
        if (Double.isNaN(sd) || sd <= 0) {
            return 2;
        }
        return Math.max(2, (int) Math.ceil(sd * sd / (eps * eps * (1 - BETA)) - 1e-9));
    }

    public static int neededStudent(double sd, double eps) {
        if (Double.isNaN(sd) || sd <= 0) {
            return 2;
        }
        int lo = 2;
        int hi = 100_000_000;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (Stats.t975(mid - 1) * sd / Math.sqrt(mid) <= eps) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }
}
