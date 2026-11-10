package ua.kpi.modsim;

/** Деталь (запит у мережі). Тип theta: 1 - перше проходження первинної обробки, 2 - повторне. */
public class Part {
    private final int id;
    private int theta = 1;

    public Part(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public int theta() {
        return theta;
    }

    public void setTheta(int theta) {
        this.theta = theta;
    }

    @Override
    public String toString() {
        return "#" + id + "/θ" + theta;
    }
}
