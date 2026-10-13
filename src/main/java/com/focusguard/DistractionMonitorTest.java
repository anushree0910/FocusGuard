
package com.focusguard;

import javax.swing.*;

public class DistractionMonitorTest {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DistractionMonitor monitor = new DistractionMonitor();
            monitor.startMonitoring();

            JOptionPane.showMessageDialog(
                    null,
                    "Monitoring started. Open Notepad to test the reminder."
            );
        });
    }
}
