package ua.kpi.modsim;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.PrintStream;
import java.util.Locale;

/**
 * Головна панель програми: поля введення вхідних змінних, кнопка запуску
 * та таблиці результатів (підсумки, прогони, протокол подій).
 */
public class AppPanel extends JPanel {
    private static final Locale UA = Locale.forLanguageTag("uk-UA");
    private static final int MAX_LOG_LINES = 20_000;
    private static final String[] MODES = {"Стохастичний", "Детермінований"};

    // --- поля введення (початкові значення з умови завдання B1)
    private final JSpinner thresholdSpin = new JSpinner(new SpinnerNumberModel(3, 0, 1000, 1));
    private final JTextField arrivalField = field("50");
    private final JTextField p1TimeField = field("40");
    private final JTextField p2TimeField = field("60");
    private final JTextField sTimeField = field("100");
    private final JTextField p1DefectField = field("0,04");
    private final JTextField p2DefectField = field("0,08");
    private final JTextField tModField = field("50000");
    private final JTextField warmupField = field("0");
    private final JSpinner runsSpin = new JSpinner(new SpinnerNumberModel(20, 1, 10000, 1));
    private final JTextField seedField = field("1");
    private final JComboBox<String> modeBox = new JComboBox<>(MODES);

    private final JButton runButton = new JButton("Запустити моделювання");
    private final JButton resetButton = new JButton("Значення з умови завдання");

    // --- виведення
    private final ReadOnlyModel summaryModel = new ReadOnlyModel(
            "Вихідна змінна", "Позначення", "Одиниці", "Середнє", "σ", "95 % довірчий інтервал");
    private final ReadOnlyModel runsModel = new ReadOnlyModel(
            "№", "Seed", "N_in", "N_w", "N_ok", "U(S2)", "U(S1)", "Pw", "L_S", "Баланс");
    private final JTable summaryTable = new JTable(summaryModel);
    private final JTable runsTable = new JTable(runsModel);
    private final JTextArea logArea = new JTextArea();
    private final JLabel infoLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel("Введіть дані та натисніть «Запустити моделювання».");
    private final JTabbedPane tabs = new JTabbedPane();

    public AppPanel() {
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildInputs(), BorderLayout.NORTH);
        add(buildOutputs(), BorderLayout.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));
        add(statusLabel, BorderLayout.SOUTH);

        modeBox.addActionListener(e -> updateModeControls());
        runButton.addActionListener(e -> onRun());
        resetButton.addActionListener(e -> resetDefaults());
        thresholdSpin.setMinimumSize(thresholdSpin.getPreferredSize());
        runsSpin.setMinimumSize(runsSpin.getPreferredSize());
        modeBox.setMinimumSize(modeBox.getPreferredSize());
        updateModeControls();
    }

    // ------------------------------------------------------------------ побудова вікна

    private static JTextField field(String text) {
        JTextField f = new JTextField(text, 8);
        f.setHorizontalAlignment(SwingConstants.RIGHT);
        f.setMinimumSize(f.getPreferredSize());
        return f;
    }

    private JPanel buildInputs() {
        JPanel cols = new JPanel(new GridBagLayout());

        JPanel control = form("Керована змінна");
        addRow(control, 0, "Поріг H, деталей", thresholdSpin);
        JLabel hint = new JLabel("<html><i>Верстат S2 підключається, коли кількість деталей, "
                + "що очікують вторинної обробки, більша за H.</i></html>");
        hint.setForeground(Color.GRAY);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 1;
        gc.gridwidth = 2;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(8, 4, 4, 4);
        gc.weightx = 1;
        gc.anchor = GridBagConstraints.NORTHWEST;
        control.add(hint, gc);
        gc.gridy = 2;
        gc.weighty = 1;
        gc.fill = GridBagConstraints.BOTH;
        control.add(new JLabel(), gc);

        JPanel stoch = form("Стохастичні змінні та параметри");
        addRow(stoch, 0, "Інтервал надходження, хв (середнє)", arrivalField);
        addRow(stoch, 1, "Час обробки на P1, хв (середнє)", p1TimeField);
        addRow(stoch, 2, "Час обробки на P2, хв (середнє)", p2TimeField);
        addRow(stoch, 3, "Вторинна обробка S1, S2, хв (середнє)", sTimeField);
        addRow(stoch, 4, "Імовірність браку p1 (верстат P1)", p1DefectField);
        addRow(stoch, 5, "Імовірність браку p2 (верстат P2)", p2DefectField);

        JPanel exp = form("Параметри експерименту");
        addRow(exp, 0, "Режим", modeBox);
        addRow(exp, 1, "Час моделювання T, хв", tModField);
        addRow(exp, 2, "Перехідний період, хв", warmupField);
        addRow(exp, 3, "Кількість прогонів", runsSpin);
        addRow(exp, 4, "Seed (початкове значення)", seedField);

        GridBagConstraints cc = new GridBagConstraints();
        cc.gridy = 0;
        cc.fill = GridBagConstraints.BOTH;
        cc.weighty = 1;
        cc.insets = new Insets(0, 0, 0, 12);
        cc.gridx = 0;
        cc.weightx = 0.7;
        cols.add(control, cc);
        cc.gridx = 1;
        cc.weightx = 1.2;
        cols.add(stoch, cc);
        cc.gridx = 2;
        cc.weightx = 1.0;
        cc.insets = new Insets(0, 0, 0, 0);
        cols.add(exp, cc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(resetButton);
        runButton.setFont(runButton.getFont().deriveFont(Font.BOLD));
        buttons.add(runButton);

        JPanel box = new JPanel(new BorderLayout(0, 8));
        box.add(cols, BorderLayout.CENTER);
        box.add(buttons, BorderLayout.SOUTH);
        return box;
    }

    private static JPanel form(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        return p;
    }

    private static void addRow(JPanel p, int row, String label, Component c) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = row;
        g.insets = new Insets(3, 6, 3, 6);
        g.gridx = 0;
        g.weightx = 1;
        g.anchor = GridBagConstraints.WEST;
        p.add(new JLabel(label), g);
        g.gridx = 1;
        g.weightx = 0;
        g.anchor = GridBagConstraints.EAST;
        g.fill = GridBagConstraints.HORIZONTAL;
        p.add(c, g);
    }

    private JTabbedPane buildOutputs() {
        styleTable(summaryTable);
        summaryTable.getColumnModel().getColumn(0).setPreferredWidth(380);
        summaryTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        summaryTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        summaryTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        summaryTable.getColumnModel().getColumn(4).setPreferredWidth(110);
        summaryTable.getColumnModel().getColumn(5).setPreferredWidth(210);
        summaryTable.setDefaultRenderer(Object.class, new CellRenderer(true));

        styleTable(runsTable);
        runsTable.setDefaultRenderer(Object.class, new CellRenderer(false));

        infoLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));
        JPanel results = new JPanel(new BorderLayout());
        results.add(infoLabel, BorderLayout.NORTH);
        results.add(new JScrollPane(summaryTable), BorderLayout.CENTER);
        JLabel note = new JLabel("<html><i>Основні вихідні змінні за метою завдання виділено жирним. "
                + "Довірчий інтервал – за формулою середнє ± t·σ/√n, β = 0,95.</i></html>");
        note.setForeground(Color.GRAY);
        note.setBorder(BorderFactory.createEmptyBorder(6, 4, 0, 4));
        results.add(note, BorderLayout.SOUTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logArea.setText(logHint());

        tabs.addTab("Результати", results);
        tabs.addTab("За прогонами", new JScrollPane(runsTable));
        tabs.addTab("Протокол подій", new JScrollPane(logArea));
        tabs.addTab("Порівняння H", new ComparisonPanel(this::readConfigOrWarn));
        tabs.addTab("Перехідний період", new TransientPanel(this::readConfigOrWarn,
                w -> warmupField.setText(String.format(Locale.ROOT, "%.0f", w))));
        tabs.addTab("Кількість прогонів", new PrecisionPanel(this::readConfigOrWarn,
                n -> runsSpin.setValue(Math.min(n, 10000))));
        return tabs;
    }

    private static String logHint() {
        return "Протокол подій формується в детермінованому режимі або при одному прогоні "
                + "(кількість прогонів = 1).";
    }

    private static void styleTable(JTable t) {
        t.setRowHeight(26);
        t.setFont(t.getFont().deriveFont(14f));
        t.getTableHeader().setFont(t.getTableHeader().getFont().deriveFont(Font.BOLD, 14f));
        t.setFillsViewportHeight(true);
        t.setGridColor(new Color(0xD0D0D0));
        t.setShowGrid(true);
        t.getTableHeader().setReorderingAllowed(false);
    }

    // ------------------------------------------------------------------ дії

    private void updateModeControls() {
        boolean det = modeBox.getSelectedIndex() == 1;
        runsSpin.setEnabled(!det);
        seedField.setEnabled(!det);
    }

    private void resetDefaults() {
        thresholdSpin.setValue(3);
        arrivalField.setText("50");
        p1TimeField.setText("40");
        p2TimeField.setText("60");
        sTimeField.setText("100");
        p1DefectField.setText("0,04");
        p2DefectField.setText("0,08");
        tModField.setText("50000");
        warmupField.setText("0");
        runsSpin.setValue(20);
        seedField.setText("1");
        modeBox.setSelectedIndex(0);
    }

    private Config readConfig() {
        return Config.parse(arrivalField.getText(), p1TimeField.getText(), p2TimeField.getText(),
                sTimeField.getText(), p1DefectField.getText(), p2DefectField.getText(),
                (Integer) thresholdSpin.getValue(), tModField.getText(), warmupField.getText(),
                (Integer) runsSpin.getValue(), seedField.getText(), modeBox.getSelectedIndex() == 1);
    }

    /** Читає поля; при помилці показує повідомлення й повертає null (для додаткових вкладок). */
    private Config readConfigOrWarn() {
        try {
            return readConfig();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Некоректні дані", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    /** Протокол подій з обмеженням кількості рядків, щоб не переповнити пам'ять при довгих прогонах. */
    private static final class CappedLog extends EventLog {
        private final StringBuilder sb = new StringBuilder();
        private int lines;
        private boolean truncated;

        CappedLog() {
            super(new PrintStream(PrintStream.nullOutputStream()));
        }

        @Override
        public void println(String s) {
            if (lines < MAX_LOG_LINES) {
                sb.append(s).append('\n');
                lines++;
            } else {
                truncated = true;
            }
        }

        String text() {
            return truncated ? sb + "... (протокол обмежено першими " + MAX_LOG_LINES + " рядками)\n" : sb.toString();
        }
    }

    private void onRun() {
        final Config cfg;
        try {
            cfg = readConfig();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Некоректні дані", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runButton.setEnabled(false);
        statusLabel.setForeground(Color.DARK_GRAY);
        statusLabel.setText("Виконується моделювання…");
        final CappedLog log = (cfg.deterministic() || cfg.runs() == 1) ? new CappedLog() : null;

        new SwingWorker<Experiment.Series, Void>() {
            @Override
            protected Experiment.Series doInBackground() {
                return Experiment.run(cfg, log);
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                try {
                    show(get(), log);
                } catch (Exception ex) {
                    statusLabel.setForeground(Color.RED.darker());
                    statusLabel.setText("Помилка моделювання: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------------ виведення

    private static String num(double v, int decimals) {
        return Double.isNaN(v) ? "–" : String.format(UA, "%." + decimals + "f", v);
    }

    private void show(Experiment.Series s, CappedLog log) {
        Config c = s.config();
        int runs = c.runs();

        infoLabel.setText(String.format(UA,
                "<html><b>H = %d</b> &nbsp;|&nbsp; T = %s хв &nbsp;|&nbsp; перехідний період = %s хв &nbsp;|&nbsp; "
                        + "режим: %s &nbsp;|&nbsp; прогонів: %d</html>",
                c.params().threshold(), num(c.tMod(), 0), num(c.warmup(), 0),
                c.deterministic() ? "детермінований" : "стохастичний", runs));

        summaryModel.setRowCount(0);
        for (Metric m : Metric.values()) {
            Stats.Summary sm = s.summary(m);
            int d = m.decimals(runs);
            String sd = runs > 1 ? num(sm.sd(), d + 1) : "–";
            String ci = sm.hasInterval()
                    ? "[" + num(sm.mean() - sm.half(), d) + "; " + num(sm.mean() + sm.half(), d) + "]" : "–";
            summaryModel.addRow(new Object[]{m.title(), m.symbol(), m.unit(), num(sm.mean(), d), sd, ci});
        }

        runsModel.setRowCount(0);
        for (int i = 0; i < s.runs().size(); i++) {
            Results r = s.runs().get(i);
            runsModel.addRow(new Object[]{i + 1, c.deterministic() ? "–" : String.valueOf(c.seed() + i),
                    r.nIn(), r.nWaste(), r.nOk(), num(r.uS2(), 4), num(r.uS1(), 4), num(r.pWaste(), 5),
                    num(r.meanLs(), 2), r.balanceOk() ? "ОК" : "ПОМИЛКА"});
        }

        logArea.setText(log != null ? log.text() : logHint());
        logArea.setCaretPosition(0);

        statusLabel.setForeground(s.allBalanced() ? new Color(0x1B5E20) : Color.RED.darker());
        statusLabel.setText(String.format(UA, "Готово за %.2f с · прогонів: %d · баланс деталей: %s",
                s.millis() / 1000.0, runs, s.allBalanced() ? "ОК" : "ПОМИЛКА"));
        tabs.setSelectedIndex(0);
    }

    // ------------------------------------------------------------------ допоміжні класи

    private static final class ReadOnlyModel extends DefaultTableModel {
        ReadOnlyModel(String... columns) {
            super(columns, 0);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    /** Вирівнювання: перша колонка зліва, решта по центру; основні рядки підсумкової таблиці - жирним. */
    private static final class CellRenderer extends DefaultTableCellRenderer {
        private final boolean summary;

        CellRenderer(boolean summary) {
            this.summary = summary;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            setHorizontalAlignment(summary && col == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);
            boolean bold = summary && row < Metric.values().length && Metric.values()[row].isMain();
            c.setFont(t.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN));
            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            return c;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1280, 800);
    }
}
