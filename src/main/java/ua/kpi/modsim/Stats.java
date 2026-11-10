package ua.kpi.modsim;

/** Статистична обробка серії прогонів: середнє, відхилення та довірчий інтервал із β = 0,95. */
public final class Stats {
    private Stats() {
    }

    /**
     * @param n    кількість врахованих значень
     * @param mean вибіркове середнє
     * @param sd   вибіркове середньоквадратичне відхилення
     * @param half півширина довірчого інтервалу (NaN, якщо n < 2)
     */
    public record Summary(int n, double mean, double sd, double half) {
        public boolean hasInterval() {
            return n >= 2 && !Double.isNaN(half);
        }
    }

    // квантилі t-розподілу Стьюдента для двостороннього інтервалу 0,95, df = 1..30
    private static final double[] T975 = {
            12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
            2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
            2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042};

    public static double t975(int df) {
        if (df < 1) {
            return Double.NaN;
        }
        return df <= 30 ? T975[df - 1] : 1.96 + 2.37 / df;
    }

    /** Порожні (NaN) значення, напр. імовірність відходів у прогоні без жодної деталі, пропускаються. */
    public static Summary summarize(double[] x) {
        int n = 0;
        double sum = 0;
        for (double v : x) {
            if (!Double.isNaN(v)) {
                n++;
                sum += v;
            }
        }
        if (n == 0) {
            return new Summary(0, Double.NaN, Double.NaN, Double.NaN);
        }
        double mean = sum / n;
        if (n < 2) {
            return new Summary(n, mean, Double.NaN, Double.NaN);
        }
        double ss = 0;
        for (double v : x) {
            if (!Double.isNaN(v)) {
                ss += (v - mean) * (v - mean);
            }
        }
        double sd = Math.sqrt(ss / (n - 1));
        return new Summary(n, mean, sd, t975(n - 1) * sd / Math.sqrt(n));
    }
}
