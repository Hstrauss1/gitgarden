package garden.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A very small JSON reader, enough for the garden's config file (the project has no
 * external libraries). Objects become {@code Map<String, Object>}, arrays {@code List<Object>},
 * numbers {@code Double}, plus {@code String}, {@code Boolean} and {@code null}.
 */
final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    /** @throws IllegalArgumentException with the position of the problem if the text isn't valid JSON */
    static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("no JSON text");
        Json json = new Json(text);
        json.skipSpace();
        Object value = json.value();
        json.skipSpace();
        if (json.pos != text.length()) throw json.error("unexpected text after the end");
        return value;
    }

    private Object value() {
        if (pos >= text.length()) throw error("unexpected end of text");
        char c = text.charAt(pos);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': return word("true", Boolean.TRUE);
            case 'f': return word("false", Boolean.FALSE);
            case 'n': return word("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return number();
                throw error("unexpected character '" + c + "'");
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        pos++;   // {
        skipSpace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipSpace();
            if (peek() != '"') throw error("expected a quoted key");
            String key = string();
            skipSpace();
            expect(':');
            skipSpace();
            map.put(key, value());
            skipSpace();
            char c = next();
            if (c == '}') return map;
            if (c != ',') throw error("expected ',' or '}'");
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<Object>();
        pos++;   // [
        skipSpace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipSpace();
            list.add(value());
            skipSpace();
            char c = next();
            if (c == ']') return list;
            if (c != ',') throw error("expected ',' or ']'");
        }
    }

    private String string() {
        pos++;   // opening quote
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return out.toString();
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = next();
            switch (e) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    if (pos + 4 > text.length()) throw error("bad \\u escape");
                    try {
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException bad) {
                        throw error("bad \\u escape");
                    }
                    pos += 4;
                    break;
                default:
                    throw error("bad escape '\\" + e + "'");
            }
        }
    }

    private Double number() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < text.length() && "0123456789.eE+-".indexOf(text.charAt(pos)) >= 0) pos++;
        try {
            return Double.valueOf(text.substring(start, pos));
        } catch (NumberFormatException bad) {
            pos = start;
            throw error("bad number");
        }
    }

    private Object word(String word, Object value) {
        if (!text.startsWith(word, pos)) throw error("unexpected text");
        pos += word.length();
        return value;
    }

    private void skipSpace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private char next() {
        if (pos >= text.length()) throw error("unexpected end of text");
        return text.charAt(pos++);
    }

    private void expect(char c) {
        if (next() != c) {
            pos--;
            throw error("expected '" + c + "'");
        }
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("Invalid JSON at character " + (pos + 1) + ": " + what);
    }
}
