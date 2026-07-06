package co.minimalart.arcoronboarding.ui.theme;

import org.junit.jupiter.api.Test;
import java.awt.Font;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BrandFontsTest {

    @Test
    void loadsBundledFonts() {
        BrandFonts fonts = BrandFonts.load();
        Font body = fonts.body(14f, Font.PLAIN);
        Font display = fonts.display(24f);
        assertNotNull(body);
        assertNotNull(display);
        assertEquals(14, body.getSize());
        assertEquals(24, display.getSize());
    }
}
