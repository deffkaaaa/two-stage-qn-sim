package ua.kpi.modsim;

/**
 * Усі дані одного запуску, що вводяться у вікні програми, з перевіркою коректності.
 *
 * @param params        вхідні змінні та параметри моделі
 * @param tMod          час моделювання, хв
 * @param warmup        перехідний період, хв (статистика до цього моменту відкидається)
 * @param runs          кількість прогонів (у детермінованому режимі - 1)
 * @param seed          початкове значення генератора випадкових чисел першого прогону
 * @param deterministic true - детермінований режим для верифікації
 */
public record Config(Params params, double tMod, double warmup, int runs, long seed, boolean deterministic) {

    /** Число з крапкою або комою ("0,04" і "0.04" однаково коректні). */
    static double number(String text, String field) {
        try {
            double v = Double.parseDouble(text.trim().replace(',', '.').replace(" ", ""));
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Поле «" + field + "»: введіть число, наприклад 0,04.");
        }
    }

    public static Config parse(String meanArrival, String meanP1, String meanP2, String meanS,
                               String p1, String p2, int threshold,
                               String tMod, String warmup, int runs, String seed, boolean deterministic) {
        double a = number(meanArrival, "Середній інтервал надходження");
        double m1 = number(meanP1, "Середній час обробки на P1");
        double m2 = number(meanP2, "Середній час обробки на P2");
        double ms = number(meanS, "Середній час вторинної обробки");
        double q1 = number(p1, "Імовірність браку p1");
        double q2 = number(p2, "Імовірність браку p2");
        double t = number(tMod, "Час моделювання T");
        double w = number(warmup, "Перехідний період");
        long sd;
        try {
            sd = Long.parseLong(seed.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Поле «Seed»: введіть ціле число.");
        }
        if (a <= 0 || m1 <= 0 || m2 <= 0 || ms <= 0) {
            throw new IllegalArgumentException("Середні часи (хв) мають бути додатними.");
        }
        if (q1 < 0 || q1 > 1 || q2 < 0 || q2 > 1) {
            throw new IllegalArgumentException("Імовірності браку мають лежати в межах від 0 до 1.");
        }
        if (threshold < 0) {
            throw new IllegalArgumentException("Поріг H не може бути від’ємним.");
        }
        if (t <= 0) {
            throw new IllegalArgumentException("Час моделювання T має бути додатним.");
        }
        if (w < 0 || w >= t) {
            throw new IllegalArgumentException("Перехідний період має бути не менший за 0 і менший за T.");
        }
        if (runs < 1) {
            throw new IllegalArgumentException("Кількість прогонів має бути не менша за 1.");
        }
        return new Config(new Params(a, m1, m2, ms, q1, q2, threshold), t, w, deterministic ? 1 : runs, sd, deterministic);
    }
}
