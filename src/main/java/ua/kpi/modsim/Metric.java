package ua.kpi.modsim;

import java.util.function.ToDoubleFunction;

/** Вихідні змінні моделі, що виводяться у таблиці результатів (таблиця 1.4 розділу 1). */
public enum Metric {
    U_S2("Завантаження верстата S2 вторинної обробки", "U(S2)", "частка", Results::uS2, 4, true, false),
    P_W("Імовірність появи відходів", "Pw", "частка", Results::pWaste, 5, true, false),
    U_S1("Завантаження верстата S1 вторинної обробки", "U(S1)", "частка", Results::uS1, 4, false, false),
    U_P1("Завантаження верстата P1 первинної обробки", "U(P1)", "частка", Results::uP1, 4, false, false),
    U_P2("Завантаження верстата P2 первинної обробки", "U(P2)", "частка", Results::uP2, 4, false, false),
    L_S("Середня кількість деталей, що чекають на S1/S2", "L_S", "деталей", Results::meanLs, 2, false, false),
    Q_0("Середня довжина черги Q0 (нові деталі)", "Q0", "деталей", Results::meanQ0, 3, false, false),
    Q_R("Середня довжина черги QR (повторна обробка)", "QR", "деталей", Results::meanQR, 3, false, false),
    Q_FIRST_P1("Частка деталей, вперше оброблених на P1", "q", "частка", Results::shareFirstP1, 3, false, false),
    N_IN("Надійшло деталей", "N_in", "деталей", r -> (double) r.nIn(), 1, false, true),
    N_W("Кількість відходів", "N_w", "деталей", r -> (double) r.nWaste(), 1, false, true),
    N_OK("Придатних після первинної обробки", "N_ok", "деталей", r -> (double) r.nOk(), 1, false, true);

    private final String title;
    private final String symbol;
    private final String unit;
    private final ToDoubleFunction<Results> getter;
    private final int decimals;
    private final boolean main;
    private final boolean count;

    Metric(String title, String symbol, String unit, ToDoubleFunction<Results> getter,
           int decimals, boolean main, boolean count) {
        this.title = title;
        this.symbol = symbol;
        this.unit = unit;
        this.getter = getter;
        this.decimals = decimals;
        this.main = main;
        this.count = count;
    }

    public String title() {
        return title;
    }

    public String symbol() {
        return symbol;
    }

    public String unit() {
        return unit;
    }

    public double of(Results r) {
        return getter.applyAsDouble(r);
    }

    /** Кількість знаків після коми; лічильники в одному прогоні показуються цілими. */
    public int decimals(int runs) {
        return count && runs == 1 ? 0 : decimals;
    }

    /** Основні вихідні змінні за метою моделювання (виділяються жирним). */
    public boolean isMain() {
        return main;
    }
}
