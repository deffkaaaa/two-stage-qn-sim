package ua.kpi.modsim;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Маршрут мережі між двома елементами (СМО): куди може піти заявка після обслуговування.
 *
 * @param target  елемент, до якого веде маршрут
 * @param prob    імовірність вибору маршруту (вага; серед відкритих маршрутів нормується до 1)
 * @param blocked умова блокування маршруту для заявки: якщо вона виконується, маршрут закритий
 * @param action  зміна стану заявки при проходженні маршрутом (може бути null)
 */
public record Route(Element target, double prob, Predicate<Part> blocked, Consumer<Part> action) {

    /** Маршрут без умови блокування. */
    public static Route to(Element target, double prob) {
        return new Route(target, prob, x -> false, null);
    }

    /** Той самий маршрут з умовою блокування. */
    public Route blockedIf(Predicate<Part> condition) {
        return new Route(target, prob, condition, action);
    }

    /** Той самий маршрут із зміною стану заявки (наприклад, типу θ). */
    public Route doing(Consumer<Part> change) {
        return new Route(target, prob, blocked, change);
    }
}
