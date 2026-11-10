package ua.kpi.modsim;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Простий лінійний графік (білий фон, щоб однаково виглядав у світлій і темній темі системи). */
final class LineChart extends JPanel {
    private static final Locale UA = Locale.forLanguageTag("uk-UA");

    record Line(String name, double[] y, Color color, float width) {
    }

    private final String title;
    private final String xTitle;
    private final String yTitle;
    private double[] x = new double[0];
    private final List<Line> lines = new ArrayList<>();
    private double yFixedMax = Double.NaN;
    private double vMark = Double.NaN;
    private String vLabel = "";
    private double hMark = Double.NaN;
    private String hLabel = "";

    LineChart(String title, String xTitle, String yTitle) {
        this.title = title;
        this.xTitle = xTitle;
        this.yTitle = yTitle;
        setOpaque(true);
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(600, 260));
    }

    void setData(double[] x, List<Line> lines, double yFixedMax, double vMark, String vLabel,
                 double hMark, String hLabel) {
        this.x = x;
        this.lines.clear();
        this.lines.addAll(lines);
        this.yFixedMax = yFixedMax;
        this.vMark = vMark;
        this.vLabel = vLabel;
        this.hMark = hMark;
        this.hLabel = hLabel;
        repaint();
    }

    private static double niceStep(double range, int target) {
        double raw = range / target;
        double pow = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / pow;
        double nice = f < 1.5 ? 1 : f < 3.5 ? 2 : f < 7.5 ? 5 : 10;
        return nice * pow;
    }

    private static String fmt(double v, double step) {
        int d = step >= 1 ? 0 : (int) Math.ceil(-Math.log10(step));
        return String.format(UA, "%." + d + "f", v);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int left = 64;
        int right = 36;
        int bottom = 48;
        int pw = w - left - right;
        int ph = h - 90 - bottom;
        Font base = getFont().deriveFont(13f);
        g.setFont(base.deriveFont(Font.BOLD, 15f));
        g.setColor(Color.BLACK);
        g.drawString(title, left, 20);
        g.setFont(base);
        FontMetrics fm = g.getFontMetrics();

        // легенда над графіком в один-два рядки
        List<String> names = new ArrayList<>();
        List<Color> cols = new ArrayList<>();
        List<Boolean> dashedEntry = new ArrayList<>();
        for (Line l : lines) {
            names.add(l.name());
            cols.add(l.color());
            dashedEntry.add(false);
        }
        if (!Double.isNaN(vMark)) {
            names.add(vLabel);
            cols.add(new Color(0xC62828));
            dashedEntry.add(true);
        }
        if (!Double.isNaN(hMark)) {
            names.add(hLabel);
            cols.add(new Color(0x2E7D32));
            dashedEntry.add(true);
        }
        int legendRows = 1;
        int cx = left;
        int rowH = 18;
        int legendY = 40;
        int[] px0 = new int[names.size()];
        int[] py0 = new int[names.size()];
        for (int i = 0; i < names.size(); i++) {
            int wItem = 30 + fm.stringWidth(names.get(i)) + 18;
            if (cx + wItem > w - right && cx > left) {
                cx = left;
                legendRows++;
            }
            px0[i] = cx;
            py0[i] = legendY + (legendRows - 1) * rowH;
            cx += wItem;
        }
        int top = legendY + legendRows * rowH + 6;
        ph = h - top - bottom;
        if (x.length < 2 || pw < 50 || ph < 50) {
            g.dispose();
            return;
        }
        for (int i = 0; i < names.size(); i++) {
            g.setColor(cols.get(i));
            if (dashedEntry.get(i)) {
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                        new float[]{5f, 4f}, 0f));
            } else {
                g.setStroke(new BasicStroke(i == 1 ? 3f : 1.6f));
            }
            g.drawLine(px0[i], py0[i] - 4, px0[i] + 24, py0[i] - 4);
            g.setColor(Color.BLACK);
            g.drawString(names.get(i), px0[i] + 30, py0[i]);
        }
        g.setStroke(new BasicStroke(1f));

        double step0 = x[1] - x[0];
        double xMin = 0;
        double xMax = x[x.length - 1] + step0 / 2;
        double yMax = yFixedMax;
        if (Double.isNaN(yMax)) {
            double m = 0;
            for (Line l : lines) {
                for (double v : l.y()) {
                    m = Math.max(m, v);
                }
            }
            yMax = m * 1.08;
        }
        double yStep = niceStep(yMax, Math.max(2, ph / 45));
        yMax = Math.ceil(yMax / yStep - 1e-9) * yStep;
        double xStep = niceStep(xMax - xMin, 8);

        // сітка та підписи осей
        g.setStroke(new BasicStroke(1f));
        for (double v = 0; v <= yMax + 1e-9; v += yStep) {
            int py = top + (int) Math.round(ph - v / yMax * ph);
            g.setColor(new Color(0xE3E3E3));
            g.drawLine(left, py, left + pw, py);
            g.setColor(Color.DARK_GRAY);
            String s = fmt(v, yStep);
            g.drawString(s, left - 8 - fm.stringWidth(s), py + fm.getAscent() / 2 - 1);
        }
        for (double v = 0; v <= xMax + 1e-9; v += xStep) {
            int px = left + (int) Math.round((v - xMin) / (xMax - xMin) * pw);
            g.setColor(new Color(0xEFEFEF));
            g.drawLine(px, top, px, top + ph);
            g.setColor(Color.DARK_GRAY);
            String s = fmt(v, xStep);
            g.drawString(s, px - fm.stringWidth(s) / 2, top + ph + 16);
        }
        g.setColor(Color.BLACK);
        g.drawRect(left, top, pw, ph);
        g.drawString(xTitle, left + (pw - fm.stringWidth(xTitle)) / 2, h - 8);
        g.rotate(-Math.PI / 2);
        g.drawString(yTitle, -(top + (ph + fm.stringWidth(yTitle)) / 2), 14);
        g.rotate(Math.PI / 2);

        // лінії
        g.setClip(left, top, pw + 1, ph + 1);
        for (Line l : lines) {
            Path2D path = new Path2D.Double();
            for (int i = 0; i < x.length && i < l.y().length; i++) {
                double px = left + (x[i] - xMin) / (xMax - xMin) * pw;
                double py = top + ph - Math.min(l.y()[i], yMax) / yMax * ph;
                if (i == 0) {
                    path.moveTo(px, py);
                } else {
                    path.lineTo(px, py);
                }
            }
            g.setColor(l.color());
            g.setStroke(new BasicStroke(l.width(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
        }
        float[] dash = {6f, 5f};
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
        if (!Double.isNaN(hMark)) {
            int py = top + (int) Math.round(ph - hMark / yMax * ph);
            g.setColor(new Color(0x2E7D32));
            g.drawLine(left, py, left + pw, py);
        }
        if (!Double.isNaN(vMark)) {
            int px = left + (int) Math.round((vMark - xMin) / (xMax - xMin) * pw);
            g.setColor(new Color(0xC62828));
            g.drawLine(px, top, px, top + ph);
        }
        g.setClip(null);

        g.dispose();
    }
}
