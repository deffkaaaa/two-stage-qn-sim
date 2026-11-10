package ua.kpi.modsim;

import java.util.HashMap;
import java.util.Map;

/**
 * Детермінований режим для верифікації: усі тривалості дорівнюють середнім,
 * брак розподіляється рівномірно (кожна 1/p-та деталь), а з одночасних подій
 * завжди обирається перша за порядком. Результат прогону можна порахувати вручну.
 */
public class DeterministicSampler implements Sampler {
    private final Map<String, Double> acc = new HashMap<>();

    @Override
    public double expo(String stream, double mean) {
        return mean;
    }

    @Override
    public boolean defect(String key, double p) {
        double a = acc.getOrDefault(key, 0.0) + p;
        if (a >= 1.0 - 1e-9) {
            acc.put(key, a - 1.0);
            return true;
        }
        acc.put(key, a);
        return false;
    }

    @Override
    public int pick(int n) {
        return 0;
    }
}
