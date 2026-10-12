package com.focusguard;

import javax.swing.*;
import java.awt.*;

/** JPanel with antialiased rounded corners and an optional vertical gradient. */
public class RoundedPanel extends JPanel {
    private final int arc;
    private Color gradientTop;
    private Color gradientBottom;

    public RoundedPanel(Color background, int arc) {
        this.arc = arc;
        setOpaque(false);
        setBackground(background);
    }

    public void setGradient(Color top, Color bottom) {
        gradientTop = top;
        gradientBottom = bottom;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (gradientTop != null && gradientBottom != null) {
            g2.setPaint(new GradientPaint(0, 0, gradientTop, 0, Math.max(1, getHeight()), gradientBottom));
        } else {
            g2.setColor(getBackground());
        }
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        g2.dispose();
    }
}
