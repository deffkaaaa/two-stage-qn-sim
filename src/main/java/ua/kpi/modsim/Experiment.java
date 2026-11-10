package ua.kpi.modsim;

import java.util.ArrayList;
import java.util.List;

/** Виконання серії прогонів моделі за введеними даними. */
public final class Experiment {
    private Experiment() {
    }

    /** Результати серії: окремі прогони та підсумкова статистика. */
    public record Series(Config config, List<Results> runs, long millis, boolean allBalanced) {
        public Stats.Summary summary(Metric m) {
            double[] x = new double[runs.size()];
            for (int i = 0; i < x.length; i++) {
                x[i] = m.of(runs.get(i));
            }
            return Stats.summarize(x);
        }
    }

    /**
     * @param cfg дані запуску
     * @param log протокол подій (або null); ведеться для детермінованого режиму та для одного прогону
     */
    public static Series run(Config cfg, EventLog log) {
        long start = System.nanoTime();
        List<Results> list = new ArrayList<>();
        boolean balanced = true;
        if (cfg.deterministic()) {
            Results r = new Model(cfg.params(), new DeterministicSampler(), log).run(cfg.tMod(), cfg.warmup());
            list.add(r);
            balanced = r.balanceOk();
        } else {
            for (int i = 0; i < cfg.runs(); i++) {
                EventLog l = cfg.runs() == 1 ? log : null;
                Results r = new Model(cfg.params(), new RandomSampler(cfg.seed() + i), l)
                        .run(cfg.tMod(), cfg.warmup());
                list.add(r);
                balanced &= r.balanceOk();
            }
        }
        return new Series(cfg, list, (System.nanoTime() - start) / 1_000_000, balanced);
    }
}
