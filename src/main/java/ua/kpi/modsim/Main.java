package ua.kpi.modsim;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Точка входу.
 *
 * <pre>
 * без аргументів                   графічний інтерфейс (поля введення та таблиці результатів)
 * trace [T] [p1] [p2] [H] [файл]   детермінований прогін із протоколом подій у консолі (верифікація)
 * run   [T] [warmup] [H] [seed]    один стохастичний прогін у консолі
 * </pre>
 */
public class Main {
    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0] : "gui";
        switch (mode) {
            case "trace" -> trace(args);
            case "run" -> run(args);
            default -> gui();
        }
    }

    private static void gui() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // залишається стандартний вигляд
            }
            JFrame frame = new JFrame("Імітаційна модель двостадійної обробки деталей з браком");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new AppPanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static double d(String[] a, int i, double def) {
        return a.length > i ? Double.parseDouble(a[i]) : def;
    }

    private static void trace(String[] a) throws IOException {
        double t = d(a, 1, 600);
        Params prm = Params.base().withDefects(d(a, 2, 0.04), d(a, 3, 0.08)).withThreshold((int) d(a, 4, 3));
        PrintStream console = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream out = a.length > 5 ? new PrintStream(a[5], "UTF-8") : console;
        Model m = new Model(prm, new DeterministicSampler(), new EventLog(out));
        Results r = m.run(t, 0);
        out.println(r);
        if (out != console) {
            out.close();
            console.println(r);
        }
    }

    private static void run(String[] a) {
        double t = d(a, 1, 100_000);
        double warmup = d(a, 2, 0);
        int h = (int) d(a, 3, 3);
        long seed = (long) d(a, 4, 1);
        Model m = new Model(Params.base().withThreshold(h), new RandomSampler(seed), null);
        new PrintStream(System.out, true, StandardCharsets.UTF_8).println(m.run(t, warmup));
    }
}