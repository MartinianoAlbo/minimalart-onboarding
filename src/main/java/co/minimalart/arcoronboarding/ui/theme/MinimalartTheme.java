package co.minimalart.arcoronboarding.ui.theme;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import java.awt.Font;

/** Installs FlatLaf and maps the Minimalart brand tokens onto Swing UI defaults. */
public final class MinimalartTheme {

    private MinimalartTheme() {}

    public static BrandFonts install() {
        FlatLightLaf.setup();
        BrandFonts fonts = BrandFonts.load();

        UIManager.put("defaultFont", fonts.body(14f, Font.PLAIN));

        // Accent + surfaces from the brand palette.
        UIManager.put("Component.focusColor", BrandColors.CELESTE);
        UIManager.put("Component.focusedBorderColor", BrandColors.CELESTE);
        UIManager.put("Button.default.background", BrandColors.CELESTE);
        UIManager.put("Button.default.foreground", BrandColors.TEXT_ON_ACCENT);
        UIManager.put("ProgressBar.foreground", BrandColors.CELESTE);
        UIManager.put("Panel.background", BrandColors.NEUTRAL_10);
        UIManager.put("Component.borderColor", BrandColors.NEUTRAL_8);

        // Rounded look.
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        return fonts;
    }
}
