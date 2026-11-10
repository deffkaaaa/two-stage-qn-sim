package ua.kpi.modsim;

import java.util.ArrayList;
import java.util.List;

/**
 * Порівняння значень порогу H за однакових умов: однакові T, перехідний період, кількість прогонів
 * і початкові значення генератора. Різниця U(S2) відносно базового H оцінюється за парними різницями прогонів.
 */
public final class Comparison {
    private Comparison() {
    }

    /**
     * @param h      значення порогу
     * @param series серія прогонів для цього H
     * @param diff   різниця U(S2) відносно базового H за парами прогонів (для базового H - null)
     */
    public record Row(int h, Experiment.Series series, Stats.Summary diff) {
        /** Різниця значуща, якщо довірчий інтервал різниці не містить нуля. */
        public boolean significant() {
            return diff != null && diff.hasInterval() && Math.abs(diff.mean()) > diff.half();
        }
    }

    /** Дисперсійний аналіз U(S2): фактор - H, блоки - прогони з однаковим seed. */
    public static Anova.Result anova(List<Row> rows) {
        int k = rows.size();
        int n = rows.get(0).series().runs().size();
        for (Row r : rows) {
            n = Math.min(n, r.series().runs().size());
        }
        double[][] d = new double[k][n];
        for (int a = 0; a < k; a++) {
            for (int b = 0; b < n; b++) {
                d[a][b] = rows.get(a).series().runs().get(b).uS2();
            }
        }
        return Anova.run(d);
    }

    /** Перше значення в списку є базовим. */
    public static List<Row> run(Config base, int[] thresholds) {
        List<Row> rows = new ArrayList<>();
        Experiment.Series baseSeries = null;
        for (int h : thresholds) {
            Config c = new Config(base.params().withThreshold(h), base.tMod(), base.warmup(),
                    base.runs(), base.seed(), base.deterministic());
            Experiment.Series s = Experiment.run(c, null);
            Stats.Summary diff = null;
            if (baseSeries == null) {
                baseSeries = s;
            } else {
                int n = Math.min(s.runs().size(), baseSeries.runs().size());
                double[] d = new double[n];
                for (int i = 0; i < n; i++) {
                    d[i] = s.runs().get(i).uS2() - baseSeries.runs().get(i).uS2();
                }
                diff = Stats.summarize(d);
            }
            rows.add(new Row(h, s, diff));
        }
        return rows;
    }
}
