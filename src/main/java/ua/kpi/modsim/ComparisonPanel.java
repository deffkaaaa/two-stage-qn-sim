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
import java.util.function.Supplier;

/** Вкладка «Порівняння H»: однакові умови для кількох значень порогу та оцінка впливу на U(S2). */
final class ComparisonPanel extends JPanel {
    private static final Locale UA = Locale.forLanguageTag("uk-UA");

    private final Supplier<Config> configSource;
    private final JTextField hField = new JTextField("3, 2, 1", 10);
    private final JButton runButton = new JButton("Порівняти значення H");
    private final JLabel info = new JLabel(" ");
    private final JLabel conclusion = new JLabel(" ");
    private final JLabel anovaLabel = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(new String[]{
            "H", "U(S2)", "95 % ДІ для U(S2)", "Δ U(S2) відносно базового H", "95 % ДІ різниці",
            "Різниця значуща?", "Pw", "U(S1)", "L_S"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };

    ComparisonPanel(Supplier<Config> configSource) {
        this.configSource = configSource;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 8, 8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.add(new JLabel("Значення H через кому (перше – базове):"));
        hField.setHorizontalAlignment(SwingConstants.CENTER);
        top.add(hField);
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
        int[] widths = {50, 90, 170, 200, 170, 140, 90, 90, 80};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                setHorizontalAlignment(SwingConstants.CENTER);
                comp.setFont(t.getFont().deriveFont(c == 1 || c == 3 ? Font.BOLD : Font.PLAIN));
                return comp;
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JLabel note = new JLabel("<html><i>Усі значення H рахуються з однаковими T, перехідним періодом, кількістю прогонів "
                + "і seed (взяті з полів угорі). Прогони з однаковим номером використовують однакові випадкові числа "
                + "(надходження, часи обробки, брак), тому різниця U(S2) зумовлена лише зміною H; "
                + "її довірчий інтервал рахується за парами прогонів. Якщо інтервал не містить нуля, "
                + "вплив зміни H статистично значущий. Pw від H не залежить (верстат S2 не впливає на брак), "
                + "тому в усіх рядках воно однакове.</i></html>");
        note.setForeground(Color.GRAY);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        conclusion.setFont(conclusion.getFont().deriveFont(Font.BOLD, 14f));
        JPanel texts = new JPanel(new BorderLayout(0, 6));
        texts.add(conclusion, BorderLayout.NORTH);
        texts.add(anovaLabel, BorderLayout.SOUTH);
        south.add(texts, BorderLayout.NORTH);
        south.add(note, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        runButton.addActionListener(e -> onRun());
    }

    private static String num(double v, int d) {
        return Double.isNaN(v) ? "–" : String.format(UA, "%." + d + "f", v);
    }

    private static String signed(double v, int d) {
        return Double.isNaN(v) ? "–" : String.format(UA, "%+." + d + "f", v);
    }

    private int[] parseH() {
        String[] parts = hField.getText().split("[,;\\s]+");
        int[] hs = new int[parts.length];
        int n = 0;
        for (String p : parts) {
            if (p.isBlank()) {
                continue;
            }
            try {
                int h = Integer.parseInt(p.trim());
                if (h < 0) {
                    throw new NumberFormatException();
                }
                hs[n++] = h;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Поле H: введіть цілі невід’ємні числа через кому, наприклад 3, 2, 1.");
            }
        }
        if (n < 2) {
            throw new IllegalArgumentException("Введіть щонайменше два значення H, наприклад 3, 2, 1.");
        }
        return java.util.Arrays.copyOf(hs, n);
    }

    private void onRun() {
        final Config cfg = configSource.get();
        if (cfg == null) {
            return;
        }
        final int[] hs;
        try {
            hs = parseH();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Некоректні дані", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runButton.setEnabled(false);
        info.setText("Виконується порівняння…");
        new SwingWorker<List<Comparison.Row>, Void>() {
            private long ms;

            @Override
            protected List<Comparison.Row> doInBackground() {
                long t0 = System.nanoTime();
                List<Comparison.Row> r = Comparison.run(cfg, hs);
                ms = (System.nanoTime() - t0) / 1_000_000;
                return r;
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                try {
                    show(cfg, get(), ms);
                } catch (Exception ex) {
                    info.setText("Помилка: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void show(Config cfg, List<Comparison.Row> rows, long ms) {
        model.setRowCount(0);
        int base = rows.get(0).h();
        StringBuilder concl = new StringBuilder("<html>");
        for (Comparison.Row r : rows) {
            Stats.Summary u = r.series().summary(Metric.U_S2);
            String ci = u.hasInterval()
                    ? "[" + num(u.mean() - u.half(), 4) + "; " + num(u.mean() + u.half(), 4) + "]" : "–";
            String d = "–";
            String dci = "–";
            String sig = "базове значення";
            if (r.diff() != null) {
                d = signed(r.diff().mean(), 4);
                if (r.diff().hasInterval()) {
                    dci = "[" + signed(r.diff().mean() - r.diff().half(), 4) + "; "
                            + signed(r.diff().mean() + r.diff().half(), 4) + "]";
                    sig = r.significant() ? "так" : "ні";
                } else {
                    sig = "–";
                }
                if (r.diff().hasInterval()) {
                    concl.append(String.format(UA, "H = %d проти H = %d: U(S2) %s на %s (%s)<br>",
                            r.h(), base, r.diff().mean() >= 0 ? "зростає" : "зменшується",
                            num(Math.abs(r.diff().mean()), 4),
                            r.significant() ? "статистично значуще" : "різниця в межах випадкового розкиду"));
                }
            }
            model.addRow(new Object[]{r.h(), num(u.mean(), 4), ci, d, dci, sig,
                    num(r.series().summary(Metric.P_W).mean(), 5),
                    num(r.series().summary(Metric.U_S1).mean(), 4),
                    num(r.series().summary(Metric.L_S).mean(), 2)});
        }
        concl.append("</html>");
        if (rows.size() >= 2 && rows.get(0).series().runs().size() >= 2 && !cfg.deterministic()) {
            Anova.Result an = Comparison.anova(rows);
            anovaLabel.setText(String.format(UA, "<html>Дисперсійний аналіз (фактор – H, блоки – прогони): "
                            + "SS<sub>H</sub> = %.5f, SS<sub>прогони</sub> = %.5f, SS<sub>залишок</sub> = %.5f; "
                            + "ступені вільності %d / %d / %d; <b>F = %s, p = %s</b> – вплив H %s</html>",
                    an.ssFactor(), an.ssBlocks(), an.ssError(), an.dfFactor(), an.dfBlocks(), an.dfError(),
                    num(an.f(), 2), an.p() < 1e-4 ? "&lt; 0,0001" : num(an.p(), 4),
                    an.significant() ? "статистично значущий (p &lt; 0,05)" : "не є статистично значущим"));
        } else {
            anovaLabel.setText(" ");
        }
        conclusion.setText(concl.length() > 13 ? concl.toString() : " ");
        boolean ok = rows.stream().allMatch(r -> r.series().allBalanced());
        info.setText(String.format(UA, "T = %s хв, перехідний період = %s хв, прогонів: %d, seed = %d · "
                        + "виконано за %.2f с · баланс деталей: %s",
                num(cfg.tMod(), 0), num(cfg.warmup(), 0), cfg.deterministic() ? 1 : cfg.runs(), cfg.seed(),
                ms / 1000.0, ok ? "ОК" : "ПОМИЛКА"));
    }
}
