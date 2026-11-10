package ua.kpi.modsim;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Вкладка «Кількість прогонів»: скільки прогонів потрібно для заданої точності (β = 0,95). */
final class PrecisionPanel extends JPanel {
    private static final Locale UA = Locale.forLanguageTag("uk-UA");

    private final Supplier<Config> configSource;
    private final Consumer<Integer> runsSetter;
    private final JTextField epsU = new JTextField("0,02", 6);
    private final JTextField epsP = new JTextField("0,001", 6);
    private final JButton runButton = new JButton("Розрахувати кількість прогонів");
    private final JButton applyButton = new JButton("Встановити цю кількість у полі «Кількість прогонів»");
    private final JLabel info = new JLabel(" ");
    private final JLabel result = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(new String[]{
            "Вихідна змінна", "σ (пілот)", "Похибка ε", "Півширина ДІ",
            "Прогонів", "n за (3.5)", "n за Стьюдентом", "Висновок"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private int recommended = -1;

    PrecisionPanel(Supplier<Config> configSource, Consumer<Integer> runsSetter) {
        this.configSource = configSource;
        this.runsSetter = runsSetter;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 8, 8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.add(new JLabel("Похибка U(S2), ε ="));
        epsU.setHorizontalAlignment(SwingConstants.CENTER);
        top.add(epsU);
        top.add(new JLabel("Похибка Pw, ε ="));
        epsP.setHorizontalAlignment(SwingConstants.CENTER);
        top.add(epsP);
        runButton.setFont(runButton.getFont().deriveFont(Font.BOLD));
        top.add(runButton);
        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.add(top, BorderLayout.NORTH);
        info.setForeground(Color.DARK_GRAY);
        north.add(info, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setFont(table.getFont().deriveFont(14f));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 14f));
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);
        table.setGridColor(new Color(0xD0D0D0));
        int[] widths = {250, 110, 110, 120, 90, 110, 150, 190};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                setHorizontalAlignment(c == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);
                comp.setFont(t.getFont().deriveFont(c == 5 ? Font.BOLD : Font.PLAIN));
                return comp;
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 6));
        result.setFont(result.getFont().deriveFont(Font.BOLD, 14f));
        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        line.add(applyButton);
        applyButton.setEnabled(false);
        JPanel two = new JPanel(new BorderLayout(0, 6));
        two.add(result, BorderLayout.NORTH);
        two.add(line, BorderLayout.SOUTH);
        south.add(two, BorderLayout.NORTH);
        JLabel note = new JLabel("<html><i>Пілотна серія – це прогони з полів угорі (рекомендовано не менше 10). "
                + "Основна оцінка – за формулою (3.5) методички: n = 20·σ²/ε² (β = 0,95, нерівність Чебишева). "
                + "Для порівняння наведено менш обережну оцінку за розподілом Стьюдента. σ береться з пілотної серії.</i></html>");
        note.setForeground(Color.GRAY);
        south.add(note, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        runButton.addActionListener(e -> onRun());
        applyButton.addActionListener(e -> {
            if (recommended > 0) {
                runsSetter.accept(recommended);
            }
        });
    }

    private static String num(double v, int d) {
        return Double.isNaN(v) ? "–" : String.format(UA, "%." + d + "f", v);
    }

    private void onRun() {
        final Config cfg = configSource.get();
        if (cfg == null) {
            return;
        }
        if (cfg.deterministic()) {
            JOptionPane.showMessageDialog(this, "Розрахунок кількості прогонів потрібен для стохастичного режиму: "
                    + "оберіть режим «Стохастичний».", "Режим", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (cfg.runs() < 2) {
            JOptionPane.showMessageDialog(this, "Для оцінки σ потрібно щонайменше 2 прогони.",
                    "Кількість прогонів", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final double eu;
        final double ep;
        try {
            eu = Config.number(epsU.getText(), "Похибка U(S2)");
            ep = Config.number(epsP.getText(), "Похибка Pw");
            if (eu <= 0 || ep <= 0) {
                throw new IllegalArgumentException("Похибки мають бути додатними.");
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Некоректні дані", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runButton.setEnabled(false);
        info.setText("Виконується пілотна серія…");
        new SwingWorker<Experiment.Series, Void>() {
            @Override
            protected Experiment.Series doInBackground() {
                return Experiment.run(cfg, null);
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                try {
                    show(cfg, get(), eu, ep);
                } catch (Exception ex) {
                    info.setText("Помилка: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void show(Config cfg, Experiment.Series s, double eu, double ep) {
        List<Precision.Row> rows = List.of(Precision.row(Metric.U_S2, s, eu), Precision.row(Metric.P_W, s, ep));
        model.setRowCount(0);
        int need = 2;
        for (Precision.Row r : rows) {
            int d = r.metric() == Metric.P_W ? 5 : 4;
            model.addRow(new Object[]{r.metric() == Metric.U_S2 ? "Завантаження верстата S2, U(S2)"
                            : "Імовірність відходів, Pw",
                    num(r.pilot().sd(), d + 1), num(r.eps(), d), num(r.pilot().half(), d),
                    r.pilot().n(), r.needed(), r.neededT(),
                    r.enough() ? "точність досягнута" : "потрібно більше прогонів"});
            need = Math.max(need, r.needed());
        }
        recommended = need;
        boolean enough = need <= cfg.runs();
        result.setText(String.format(UA, "Для обох змінних одночасно потрібно не менше %d прогонів "
                + "(зараз %d) – %s.", need, cfg.runs(), enough ? "точність досягнута" : "кількість прогонів треба збільшити"));
        applyButton.setEnabled(true);
        info.setText(String.format(UA, "Пілотна серія: T = %s хв, перехідний період = %s хв, прогонів: %d, seed = %d · %.2f с",
                num(cfg.tMod(), 0), num(cfg.warmup(), 0), cfg.runs(), cfg.seed(), s.millis() / 1000.0));
    }
}
