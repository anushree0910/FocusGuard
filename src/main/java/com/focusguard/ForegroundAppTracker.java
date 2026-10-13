
package com.focusguard;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;

public class ForegroundAppTracker {

    public static HWND getActiveWindowHandle() {
        return User32.INSTANCE.GetForegroundWindow();
    }

    public static String getActiveWindowTitle() {
        return getWindowTitle(getActiveWindowHandle());
    }

    public static String getWindowTitle(HWND hwnd) {
        if (hwnd == null) {
            return "";
        }

        char[] buffer = new char[1024];
        int length = User32.INSTANCE.GetWindowText(
                hwnd, buffer, buffer.length
        );

        if (length <= 0) {
            return "";
        }

        return Native.toString(buffer);
    }

    public static String getActiveProcessName() {
        return getProcessName(getActiveWindowHandle());
    }

    public static String getProcessName(HWND hwnd) {
        if (hwnd == null) {
            return "";
        }

        IntByReference processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, processId);

        int pid = processId.getValue();

        if (pid <= 0) {
            return "";
        }

        try {
            Process process = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "(Get-Process -Id " + pid + ").ProcessName"
            ).redirectErrorStream(true).start();

            byte[] output = process.getInputStream().readAllBytes();
            process.waitFor();

            return new String(output).trim();

        } catch (Exception e) {
            return "";
        }
    }

    public static boolean restoreWindow(HWND hwnd) {
        if (hwnd == null || !User32.INSTANCE.IsWindow(hwnd)) {
            return false;
        }

        User32.INSTANCE.ShowWindow(hwnd, 9);
        User32.INSTANCE.BringWindowToTop(hwnd);

        return User32.INSTANCE.SetForegroundWindow(hwnd);
    }
}
