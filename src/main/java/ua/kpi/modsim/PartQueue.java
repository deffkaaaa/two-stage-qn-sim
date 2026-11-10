package ua.kpi.modsim;

import java.util.ArrayDeque;

/** Черга (накопичувач) необмеженої місткості з обліком середньої довжини на нерівних інтервалах часу. */
public class PartQueue {
    private final String name;
    private final ArrayDeque<Part> items = new ArrayDeque<>();
    private double area; // інтеграл довжини черги за часом

    public PartQueue(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void add(Part p) {
        items.addLast(p);
    }

    public Part poll() {
        return items.pollFirst();
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Накопичення статистики за інтервал dt, протягом якого довжина черги не змінювалась. */
    public void doStatistics(double dt) {
        area += items.size() * dt;
    }

    public void resetStatistics() {
        area = 0;
    }

    public double meanLength(double observedTime) {
        return observedTime > 0 ? area / observedTime : 0;
    }
}
