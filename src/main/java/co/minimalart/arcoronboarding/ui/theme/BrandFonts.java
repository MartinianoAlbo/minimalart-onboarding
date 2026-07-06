package co.minimalart.arcoronboarding.ui.theme;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;

/** Loads the bundled Minimalart TTFs once and derives sized variants.
 * Body = Zalando Sans (UI/text); display = Fraunces SemiBold (titles). */
public final class BrandFonts {

    private final Font bodyBase;
    private final Font displayBase;

    private BrandFonts(Font bodyBase, Font displayBase) {
        this.bodyBase = bodyBase;
        this.displayBase = displayBase;
    }

    public static BrandFonts load() {
        Font body = register("/brand/fonts/ZalandoSans-Regular.ttf");
        register("/brand/fonts/ZalandoSans-Medium.ttf");
        register("/brand/fonts/ZalandoSans-SemiBold.ttf");
        Font display = register("/brand/fonts/Fraunces_72pt-SemiBold.ttf");
        return new BrandFonts(body, display);
    }

    public Font body(float size, int style) {
        return bodyBase.deriveFont(style, size);
    }

    public Font display(float size) {
        return displayBase.deriveFont(size);
    }

    private static Font register(String resource) {
        try (InputStream in = BrandFonts.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled font: " + resource);
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("Failed to load font " + resource, e);
        }
    }
}
