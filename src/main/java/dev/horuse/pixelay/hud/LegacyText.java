package dev.horuse.pixelay.hud;

/**
 * Converts legacy section-sign formatting codes ({@code §a}, {@code §l}, {@code §x§R§R§G§G§B§B})
 * into MiniMessage tags.
 *
 * <p>Placeholders often return legacy codes. The client draws them with zero width, but
 * MiniMessage keeps them as plain characters, so the measured line width would be too large
 * and the HUD would shift. Converting them first keeps the measured width equal to the drawn width.
 */
public final class LegacyText {

    private static final char SECTION = '§';
    private static final String COLORS = "0123456789abcdef";
    private static final String[] COLOR_TAGS = {
        "black",
        "dark_blue",
        "dark_green",
        "dark_aqua",
        "dark_red",
        "dark_purple",
        "gold",
        "gray",
        "dark_gray",
        "blue",
        "green",
        "aqua",
        "red",
        "light_purple",
        "yellow",
        "white",
    };

    private LegacyText() {}

    /**
     * Replaces legacy formatting codes with MiniMessage tags.
     *
     * @param text text that can contain legacy codes and MiniMessage tags
     * @return text with MiniMessage tags only
     */
    public static String toMiniMessage(String text) {
        if (text == null || text.indexOf(SECTION) < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c != SECTION || i + 1 >= text.length()) {
                out.append(c);
                i++;
                continue;
            }
            char code = Character.toLowerCase(text.charAt(i + 1));
            if (code == 'x' && i + 14 <= text.length()) {
                String hex = hexColor(text, i + 2);
                if (hex != null) {
                    out.append("<reset><#").append(hex).append('>');
                    i += 14;
                    continue;
                }
            }
            int color = COLORS.indexOf(code);
            if (color >= 0) {
                // A legacy color resets every active decoration.
                out.append("<reset><").append(COLOR_TAGS[color]).append('>');
            } else {
                String tag = switch (code) {
                    case 'k' -> "<obfuscated>";
                    case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>";
                    case 'n' -> "<underlined>";
                    case 'o' -> "<italic>";
                    case 'r' -> "<reset>";
                    default -> "";
                };
                out.append(tag);
            }
            i += 2;
        }
        return out.toString();
    }

    /** Reads the six hex digits of a {@code §x} color that starts at {@code start}. */
    private static String hexColor(String text, int start) {
        StringBuilder hex = new StringBuilder(6);
        for (int k = 0; k < 6; k++) {
            int pos = start + k * 2;
            if (text.charAt(pos) != SECTION) {
                return null;
            }
            char digit = text.charAt(pos + 1);
            if (Character.digit(digit, 16) < 0) {
                return null;
            }
            hex.append(digit);
        }
        return hex.toString();
    }
}
