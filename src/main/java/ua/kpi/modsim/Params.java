package ua.kpi.modsim;

/**
 * Вхідні змінні та параметри моделі (таблиці 1.1-1.3 розділу 1). Час - у хвилинах.
 *
 * @param meanArrival середній інтервал надходження деталей, exp(50)
 * @param meanP1      середній час обробки на верстаті P1, exp(40)
 * @param meanP2      середній час обробки на верстаті P2, exp(60)
 * @param meanS       середній час вторинної обробки на S1 і S2, exp(100)
 * @param p1          імовірність браку після P1 (0,04)
 * @param p2          імовірність браку після P2 (0,08)
 * @param threshold   поріг H: верстат S2 підключається, коли L_S > H
 */
public record Params(double meanArrival, double meanP1, double meanP2, double meanS,
                     double p1, double p2, int threshold) {

    /** Базові значення з умови завдання B1. */
    public static Params base() {
        return new Params(50, 40, 60, 100, 0.04, 0.08, 3);
    }

    public Params withThreshold(int h) {
        return new Params(meanArrival, meanP1, meanP2, meanS, p1, p2, h);
    }

    public Params withDefects(double newP1, double newP2) {
        return new Params(meanArrival, meanP1, meanP2, meanS, newP1, newP2, threshold);
    }
}
