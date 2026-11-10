package ua.kpi.modsim;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Пристрій розподілу (D0, DS) з нульовим часом обслуговування та блокуванням маршрутів.
 * Бере деталь із черги, і якщо жоден маршрут не відкритий, залишається заблокованим із деталлю.
 */
public class Dispatcher {
    private record Route(String targetName, Consumer<Part> target, BooleanSupplier blocked) {
    }

    private final String name;
    private final PartQueue in;
    private final Model model;
    private final List<Route> routes = new ArrayList<>();
    private Part held;

    public Dispatcher(String name, PartQueue in, Model model) {
        this.name = name;
        this.in = in;
        this.model = model;
    }

    public void addRoute(String targetName, Consumer<Part> target, BooleanSupplier blocked) {
        routes.add(new Route(targetName, target, blocked));
    }

    public boolean holds() {
        return held != null;
    }

    public Part held() {
        return held;
    }

    /** Один крок нульової тривалості. Повертає true, якщо стан змінився. */
    public boolean step() {
        boolean changed = false;
        if (held == null && !in.isEmpty()) {
            held = in.poll();
            changed = true;
        }
        if (held != null) {
            Route open = null;
            for (Route r : routes) {
                if (!r.blocked.getAsBoolean()) {
                    if (open != null) {
                        throw new IllegalStateException("Умови блокування " + name + " не взаємовиключні");
                    }
                    open = r;
                }
            }
            if (open != null) {
                Part p = held;
                held = null;
                model.log("  " + p + ": " + name + " -> " + open.targetName);
                open.target.accept(p);
                changed = true;
            }
        }
        return changed;
    }
}
