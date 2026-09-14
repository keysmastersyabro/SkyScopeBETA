package dev.skyscope.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;

/** Shared visual language for the SkyScope client screens. */
final class SkyScopeUi {
    static final int BG = 0xFF080D13;
    static final int TOP = 0xFF101B27;
    static final int CARD = 0xFF121C27;
    static final int CARD_ALT = 0xFF172331;
    static final int BORDER = 0xFF263849;
    static final int ACCENT = 0xFF56E6C1;
    static final int ACCENT_SOFT = 0xFF1D5C5A;
    static final int WHITE = 0xFFF4F7FA;
    static final int MUTED = 0xFFA5B4C3;
    static final int DIM = 0xFF718294;
    static final int GREEN = 0xFF6FE3A9;
    static final int AMBER = 0xFFF4CA72;
    static final int RED = 0xFFFF7185;
    private static final int PANEL_RADIUS = 10;

    private SkyScopeUi() {}

    static void background(GuiGraphicsExtractor g, int width, int height) {
        g.fillGradient(0, 0, width, Math.min(58, height), TOP, BG);
        g.fill(0, Math.min(57, height - 1), width, Math.min(59, height), ACCENT_SOFT);
        g.fill(0, Math.min(59, height - 1), width, height, BG);
    }

    static void header(GuiGraphicsExtractor g, Font font, int width, String title, String subtitle, String status, int statusColor) {
        int badgeWidth = Math.max(58, status.length() * 6 + 18);
        int margin = width < 520 ? 12 : 22;
        int badgeX = width - badgeWidth - margin;
        g.text(font, "SKYSCOPE", margin, 14, ACCENT, true);
        if (width < 520) g.text(font, title, margin, 34, WHITE, true);
        else {
            g.text(font, title, 92, 14, WHITE, true);
            g.text(font, subtitle, 22, 34, MUTED, false);
        }
        pill(g, badgeX, 12, badgeWidth, 20, statusColor);
        g.centeredText(font, status, badgeX + badgeWidth / 2, 18, statusColor);
    }

    static void card(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        card(g, x, y, width, height, ACCENT);
    }

    static void card(GuiGraphicsExtractor g, int x, int y, int width, int height, int accent) {
        if (width <= 0 || height <= 0) return;
        rounded(g, x + 3, y + 4, width - 6, height - 4, 0xFF05090D);
        rounded(g, x, y, width, height, BORDER);
        rounded(g, x + 1, y + 1, width - 2, height - 2, CARD);
        rounded(g, x + 10, y + 1, width - 20, 3, accent);
    }

    static void inset(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean selected) {
        if (selected) {
            rounded(g, x + 2, y + 3, width - 4, height - 3, 0xFF071014);
            rounded(g, x, y, width, height, ACCENT_SOFT);
            rounded(g, x + 1, y + 1, width - 2, height - 2, 0xFF17343B);
            rounded(g, x + 8, y + 1, width - 16, 3, ACCENT);
        } else {
            rounded(g, x, y, width, height, 0xFF1A2734);
            rounded(g, x + 1, y + 1, width - 2, height - 2, 0xFF101923);
        }
    }

    static void pill(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        rounded(g, x, y, width, height, (color & 0x00FFFFFF) | 0x26000000);
    }

    static void rounded(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        int radius = Math.min(PANEL_RADIUS, Math.min(width, height) / 2);
        g.fill(x + radius, y, x + width - radius, y + height, color);
        g.fill(x, y + radius, x + width, y + height - radius, color);
    }

    static void divider(GuiGraphicsExtractor g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, 0xFF20303D);
    }

    static void stat(GuiGraphicsExtractor g, Font font, int x, int y, int width, String label, String value, int color) {
        card(g, x, y, width, 52, color);
        g.text(font, label, x + 12, y + 10, DIM, false);
        g.text(font, value, x + 12, y + 27, color, true);
    }

    static void section(GuiGraphicsExtractor g, Font font, int x, int y, String label, String detail) {
        g.text(font, label, x, y, ACCENT, true);
        if (!detail.isBlank()) g.text(font, detail, x, y + 17, DIM, false);
    }

    static int pulse(int base) {
        double wave = (Math.sin(System.currentTimeMillis() / 650.0) + 1.0) * 0.5;
        int boost = (int) (wave * 18);
        return Math.min(255, ((base >> 16) & 0xFF) + boost) << 16
                | Math.min(255, ((base >> 8) & 0xFF) + boost) << 8
                | Math.min(255, (base & 0xFF) + boost) | 0xFF000000;
    }
}
