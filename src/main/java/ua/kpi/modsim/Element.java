package ua.kpi.modsim;

/**
 * Елемент мережі масового обслуговування (формалізм ММО): будь-який вузол мережі - джерело заявок,
 * СМО або вихід. Кожен елемент має момент найближчої події tNext та дві дії: надходження заявки
 * ({@link #inAct}) і подію самого елемента ({@link #outAct}). Подія мережі - це завжди outAct
 * одного елемента, однаково реалізована для всіх.
 */
public abstract class Element {
    protected final String name;
    protected final Model model;

    protected Element(String name, Model model) {
        this.name = name;
        this.model = model;
    }

    public String name() {
        return name;
    }

    /** Момент найближчої події елемента; нескінченність, якщо подій немає. */
    public abstract double tNext();

    /** Надходження заявки в елемент (з іншого елемента мережі). */
    public abstract void inAct(Part p);

    /** Подія елемента в момент tNext. */
    public abstract void outAct();

    /** Накопичення статистики за інтервал dt зі сталим станом елемента. */
    public void doStatistics(double dt) {
    }

    public void resetStatistics() {
    }
}
