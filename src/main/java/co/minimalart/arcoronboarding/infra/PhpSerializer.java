package co.minimalart.arcoronboarding.infra;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Serializes the minimal subset of PHP's serialize() that WordPress options and
 * usermeta need: strings, ints, booleans, integer-indexed lists and string-keyed maps. */
public final class PhpSerializer {

    private PhpSerializer() {}

    public static String serialize(Object value) {
        if (value instanceof String s)   return serializeString(s);
        if (value instanceof Boolean b)  return "b:" + (b ? 1 : 0) + ";";
        if (value instanceof Integer i)  return "i:" + i + ";";
        if (value instanceof Long l)     return "i:" + l + ";";
        if (value instanceof List<?> l)  return serializeList(l);
        if (value instanceof Map<?, ?> m) return serializeMap(m);
        throw new IllegalArgumentException("Unsupported type: " + value.getClass());
    }

    private static String serializeString(String s) {
        int byteLength = s.getBytes(StandardCharsets.UTF_8).length;
        return "s:" + byteLength + ":\"" + s + "\";";
    }

    private static String serializeList(List<?> list) {
        StringBuilder sb = new StringBuilder("a:").append(list.size()).append(":{");
        for (int i = 0; i < list.size(); i++) {
            sb.append(serialize(i)).append(serialize(list.get(i)));
        }
        return sb.append("}").toString();
    }

    private static String serializeMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("a:").append(map.size()).append(":{");
        for (Map.Entry<?, ?> e : map.entrySet()) {
            sb.append(serialize(e.getKey())).append(serialize(e.getValue()));
        }
        return sb.append("}").toString();
    }
}
