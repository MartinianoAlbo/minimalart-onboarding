package co.minimalart.arcoronboarding.ui.theme;

import java.awt.Color;

/** Minimalart brand colors (from the brand tokens). No hex is hardcoded elsewhere. */
public final class BrandColors {

    private BrandColors() {}

    public static final Color CELESTE      = Color.decode("#4B8EEF"); // accent
    public static final Color CELESTE_PALE  = Color.decode("#B9D4FB");
    public static final Color NEUTRAL_1     = Color.decode("#0F172A"); // text (darkest)
    public static final Color NEUTRAL_5     = Color.decode("#64748B"); // muted text
    public static final Color NEUTRAL_8     = Color.decode("#E2E8F0"); // border
    public static final Color NEUTRAL_9     = Color.decode("#F1F5F9"); // sunken surface
    public static final Color NEUTRAL_10    = Color.decode("#F8FAFC"); // surface (lightest)
    public static final Color TEXT_ON_ACCENT = NEUTRAL_10;
}
