package p2p.net;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Protocol {
    public static final String PREFIX = "P2P471";
    public static final int FLOOD_TTL = 2;

    private Protocol() {
    }

    public static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
