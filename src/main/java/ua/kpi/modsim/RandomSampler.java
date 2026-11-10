package ua.kpi.modsim;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Стохастичний режим: експоненціальні величини, брак за Бернуллі, випадковий порядок подій.
 *
 * <p>Кожне джерело випадковості (надходження, час на P1, час на P2, вторинна обробка, брак на P1, брак на P2,
 * вибір серед одночасних подій) має власний потік. Завдяки цьому прогони з однаковим seed і різними H мають
 * однакові надходження, часи обробки та брак - це "загальні випадкові числа": різниця результатів
 * для різних H зумовлена лише зміною H, а не випадковим розкидом.
 */
public class RandomSampler implements Sampler {
    private final long seed;
    private final Map<String, Random> streams = new HashMap<>();

    public RandomSampler(long seed) {
        this.seed = seed;
    }

    /** Початкове значення окремого потоку: змішування seed і назви потоку (splitmix64). */
    private static long mix(long seed, String name) {
        long z = seed + 0x9E3779B97F4A7C15L * (1L + name.hashCode());
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private Random rng(String name) {
        return streams.computeIfAbsent(name, k -> new Random(mix(seed, k)));
    }

    /** Формула (2.8): g = -d * ln(r); використано 1 - r, щоб r належало (0; 1]. */
    @Override
    public double expo(String stream, double mean) {
        return -mean * Math.log(1.0 - rng("time:" + stream).nextDouble());
    }

    /** Формула (2.9): брак, якщо r < p. */
    @Override
    public boolean defect(String key, double p) {
        return rng("defect:" + key).nextDouble() < p;
    }

    @Override
    public int pick(int n) {
        return n == 1 ? 0 : rng("pick").nextInt(n);
    }
}
