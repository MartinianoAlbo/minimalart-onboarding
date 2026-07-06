package co.minimalart.arcoronboarding.infra;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpSerializerTest {

    @Test
    void serializesString() {
        assertEquals("s:13:\"administrator\";", PhpSerializer.serialize("administrator"));
    }

    @Test
    void serializesBooleanAndInt() {
        assertEquals("b:1;", PhpSerializer.serialize(true));
        assertEquals("b:0;", PhpSerializer.serialize(false));
        assertEquals("i:5;", PhpSerializer.serialize(5));
    }

    @Test
    void serializesActivePluginsList() {
        assertEquals(
            "a:1:{i:0;s:27:\"arcorencasa/arcorencasa.php\";}",
            PhpSerializer.serialize(List.of("arcorencasa/arcorencasa.php")));
    }

    @Test
    void serializesCapabilitiesMap() {
        Map<String, Boolean> caps = new LinkedHashMap<>();
        caps.put("administrator", true);
        assertEquals("a:1:{s:13:\"administrator\";b:1;}", PhpSerializer.serialize(caps));
    }
}
