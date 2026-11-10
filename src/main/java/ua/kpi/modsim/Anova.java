package ua.kpi.modsim;

/**
 * Дисперсійний аналіз з одним фактором (значення порогу H) і блоками (прогони з однаковим seed).
 * Прогони для різних H використовують однакові випадкові числа, тому вони утворюють пари (блоки), і мінливість
 * між прогонами виключається з похибки: це двофакторний аналіз без повторень (блочний план).
 */
public final class Anova {
    private Anova() {
    }

    /**
     * @param ssFactor сума квадратів відхилень, зумовлена фактором (H)
     * @param ssBlocks сума квадратів між блоками (прогонами)
     * @param ssError  залишкова сума квадратів
     * @param dfFactor кількість ступенів вільності фактора (k - 1)
     * @param dfBlocks кількість ступенів вільності блоків (n - 1)
     * @param dfError  залишкові ступені вільності ((k - 1)(n - 1))
     * @param f        значення F-статистики для фактора
     * @param p        p-значення: імовірність отримати таке або більше F за відсутності впливу фактора
     */
    public record Result(double ssFactor, double ssBlocks, double ssError,
                         int dfFactor, int dfBlocks, int dfError, double f, double p) {
        public boolean significant() {
            return p < 0.05;
        }
    }

    /** @param data data[k][i] - значення для k-го рівня фактора в i-му прогоні (блоці) */
    public static Result run(double[][] data) {
        int k = data.length;
        int n = data[0].length;
        double grand = 0;
        double[] rowMean = new double[k];
        double[] colMean = new double[n];
        for (int a = 0; a < k; a++) {
            for (int b = 0; b < n; b++) {
                grand += data[a][b];
                rowMean[a] += data[a][b] / n;
                colMean[b] += data[a][b] / k;
            }
        }
        grand /= (double) k * n;
        double ssF = 0;
        double ssB = 0;
        double ssT = 0;
        for (int a = 0; a < k; a++) {
            ssF += n * (rowMean[a] - grand) * (rowMean[a] - grand);
        }
        for (int b = 0; b < n; b++) {
            ssB += k * (colMean[b] - grand) * (colMean[b] - grand);
        }
        for (int a = 0; a < k; a++) {
            for (int b = 0; b < n; b++) {
                ssT += (data[a][b] - grand) * (data[a][b] - grand);
            }
        }
        double ssE = Math.max(0, ssT - ssF - ssB);
        int dfF = k - 1;
        int dfB = n - 1;
        int dfE = dfF * dfB;
        double f = ssE > 0 ? (ssF / dfF) / (ssE / dfE) : Double.POSITIVE_INFINITY;
        double p = Double.isInfinite(f) ? 0 : fSurvival(f, dfF, dfE);
        return new Result(ssF, ssB, ssE, dfF, dfB, dfE, f, p);
    }

    /** P(F > f) для розподілу Фішера з d1 і d2 ступенями вільності: I_x(d2/2, d1/2), x = d2/(d2 + d1 f). */
    static double fSurvival(double f, int d1, int d2) {
        double x = d2 / (d2 + d1 * f);
        return incompleteBeta(d2 / 2.0, d1 / 2.0, x);
    }

    /** Регуляризована неповна бета-функція I_x(a, b) (ланцюговий дріб Лентца). */
    static double incompleteBeta(double a, double b, double x) {
        if (x <= 0) {
            return 0;
        }
        if (x >= 1) {
            return 1;
        }
        double lbeta = lgamma(a + b) - lgamma(a) - lgamma(b);
        double front = Math.exp(lbeta + a * Math.log(x) + b * Math.log(1 - x));
        if (x < (a + 1) / (a + b + 2)) {
            return front * betacf(a, b, x) / a;
        }
        return 1 - front * betacf(b, a, 1 - x) / b;
    }

    private static double betacf(double a, double b, double x) {
        final double tiny = 1e-300;
        double qab = a + b;
        double qap = a + 1;
        double qam = a - 1;
        double c = 1;
        double d = 1 - qab * x / qap;
        if (Math.abs(d) < tiny) {
            d = tiny;
        }
        d = 1 / d;
        double h = d;
        for (int m = 1; m <= 500; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < tiny) {
                d = tiny;
            }
            c = 1 + aa / c;
            if (Math.abs(c) < tiny) {
                c = tiny;
            }
            d = 1 / d;
            h *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < tiny) {
                d = tiny;
            }
            c = 1 + aa / c;
            if (Math.abs(c) < tiny) {
                c = tiny;
            }
            d = 1 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < 1e-14) {
                break;
            }
        }
        return h;
    }

    /** ln Γ(x) (наближення Ланцоша). */
    static double lgamma(double x) {
        double[] cof = {76.18009172947146, -86.50532032941677, 24.01409824083091,
                -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (double c : cof) {
            ser += c / ++y;
        }
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }
}
