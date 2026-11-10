package ua.kpi.modsim;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Scrollable;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Вкладка «Перехідний період»: графіки U(S2) і L_S у часі та рекомендований перехідний період. */
final class TransientPanel extends JPanel {
    private static final Locale UA = Locale.forLanguageTag("uk-UA");
    private static final Color RAW = new Color(0x9DB8D9);
    private static final Color SMOOTH = new Color(0x0D47A1);

    private final Supplier<Config> configSource;
    private final Consumer<Double> warmupSetter;
    private final JTextField stepField = new JTextField("авто", 6);
    private final JSpinner halfSpin = new JSpinner(new SpinnerNumberModel(5, 0, 50, 1));
    private final JButton runButton = new JButton("Побудувати графіки");
    private final JButton saveButton = new JButton("Зберегти як PNG");
    private final JButton applyButton = new JButton("Встановити перехідний період");
    private final JLabel info = new JLabel(" ");
    private final JLabel result = new JLabel(" ");
    private final LineChart chartU = new LineChart("Завантаження верстата S2 за часом (середнє за прогонами)",
            "час моделювання, хв", "U(S2)");
    private final LineChart chartL = new LineChart("Кількість деталей, що чекають вторинної обробки, L_S",
            "час моделювання, хв", "L_S, деталей");
    private final JPanel charts = new ScrollableCharts();
    private double suggested = -1;

    TransientPanel(Supplier<Config> configSource, Consumer<Double> warmupSetter) {
        this.configSource = configSource;
        this.warmupSetter = warmupSetter;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 8, 8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.add(new JLabel("Вікно, хв:"));
        stepField.setHorizontalAlignment(SwingConstants.CENTER);
        top.add(stepField);
        top.add(new JLabel("Згладжування (± вікон):"));
        top.add(halfSpin);
        runButton.setFont(runButton.getFont().deriveFont(Font.BOLD));
        top.add(runButton);
        top.add(applyButton);
        top.add(saveButton);
        applyButton.setEnabled(false);
        saveButton.setEnabled(false);
        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.add(top, BorderLayout.NORTH);
        info.setForeground(Color.DARK_GRAY);
        north.add(info, BorderLayout.CENTER);
        result.setFont(result.getFont().deriveFont(Font.BOLD, 14f));
        north.add(result, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        charts.setBackground(Color.WHITE);
        charts.add(chartU);
        charts.add(chartL);
        JScrollPane scroll = new JScrollPane(charts, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xC8C8C8)));
        add(scroll, BorderLayout.CENTER);

        JLabel note = new JLabel("<html><i>Прогони від порожнього стану. Товста лінія – ковзне середнє (метод Велча). "
                + "Перехідний період – там, де U(S2) виходить на 95 % свого рівня наприкінці прогону.</i></html>");
        note.setForeground(Color.GRAY);
        add(note, BorderLayout.SOUTH);

        runButton.addActionListener(e -> onRun());
        saveButton.addActionListener(e -> onSave());
        applyButton.addActionListener(e -> {
            if (suggested >= 0) {
                warmupSetter.accept(suggested);
            }
        });
    }

    /** Два графіки один під одним: висота не менша за 2 x 300, ширина підлаштовується під вікно. */
    private static final class ScrollableCharts extends JPanel implements Scrollable {
        ScrollableCharts() {
            super(new GridLayout(2, 1, 0, 10));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(900, 560);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(900, 640);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return 200;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() > 640;
        }
    }

    private static String num(double v, int d) {
        return Double.isNaN(v) ? "–" : String.format(UA, "%." + d + "f", v);
    }

    private void onRun() {
        final Config cfg = configSource.get();
        if (cfg == null) {
            return;
        }
        final double step;
        String text = stepField.getText().trim();
        try {
            step = text.isEmpty() || text.equalsIgnoreCase("авто") ? cfg.tMod() / 100.0 : Config.number(text, "Вікно");
            if (step <= 0) {
                throw new IllegalArgumentException("Вікно має бути додатним.");
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Некоректні дані", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final int half = (Integer) halfSpin.getValue();
        runButton.setEnabled(false);
        info.setText("Виконується моделювання…");
        new SwingWorker<Transient.Curve, Void>() {
            @Override
            protected Transient.Curve doInBackground() {
                return Transient.run(cfg, step, half);
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                try {
                    show(cfg, get());
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    info.setText("Помилка: " + c.getMessage());
                }
            }
        }.execute();
    }

    private void show(Config cfg, Transient.Curve c) {
        suggested = c.suggested();
        chartU.setData(c.time(), List.of(
                new LineChart.Line("середнє за прогонами у вікні", c.uS2(), RAW, 1.2f),
                new LineChart.Line("ковзне середнє", c.uS2Smooth(), SMOOTH, 2.6f)),
                1.0, c.suggested(), "рекомендований перехідний період ≈ " + num(c.suggested(), 0) + " хв",
                c.plateau(), "рівень в кінці прогону " + num(c.plateau(), 3));
        chartL.setData(c.time(), List.of(
                new LineChart.Line("середнє за прогонами у вікні", c.ls(), RAW, 1.2f),
                new LineChart.Line("ковзне середнє", c.lsSmooth(), SMOOTH, 2.6f)),
                Double.NaN, c.suggested(), "перехідний період", Double.NaN, "");
        info.setText(String.format(UA, "T = %s хв, вікно = %s хв, прогонів: %d, seed = %d · %.2f с",
                num(c.time()[c.time().length - 1] + c.step() / 2, 0), num(c.step(), 0), c.runs(),
                cfg.seed(), c.millis() / 1000.0));
        StringBuilder sb = new StringBuilder("<html>Рекомендований перехідний період: <b>≈ ")
                .append(num(c.suggested(), 0)).append(" хв</b>.");
        if (c.stillGrowing()) {
            sb.append("<br><font color='#B71C1C'>Увага: наприкінці прогону U(S2) і/або L_S ще зростають "
                    + "(стадія майже критична) – результати подаються для конкретного T.</font>");
        }
        result.setText(sb.append("</html>").toString());
        applyButton.setText("Встановити перехідний період = " + num(c.suggested(), 0) + " хв");
        applyButton.setEnabled(true);
        saveButton.setEnabled(true);
    }

    private void onSave() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("perekhidnyi_period.png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        int w = charts.getWidth();
        int h = charts.getHeight();
        BufferedImage img = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.scale(2, 2);
        charts.paint(g);
        g.dispose();
        try {
            File f = fc.getSelectedFile();
            if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                f = new File(f.getParentFile(), f.getName() + ".png");
            }
            ImageIO.write(img, "png", f);
            info.setText("Збережено: " + f.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Не вдалося зберегти файл: " + ex.getMessage(),
                    "Помилка", JOptionPane.ERROR_MESSAGE);
        }
    }
}
