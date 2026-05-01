package com.urlshortener;

import com.urlshortener.util.Base62Encoder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class Base62EncoderTest {

    private final Base62Encoder encoder = new Base62Encoder();

    @Test
    void encode_thenDecode_returnsOriginal() {
        long id = 123456789L;
        String encoded = encoder.encode(id);
        assertThat(encoder.decode(encoded)).isEqualTo(id);
    }

    @Test
    void generateRandom_hasCorrectLength() {
        String code = encoder.generateRandom(7);
        assertThat(code).hasSize(7);
        assertThat(code).matches("[a-zA-Z0-9]{7}");
    }

    @Test
    void detectDevice_mobileUserAgent_returnsMobile() {
        String mobile = Base62Encoder.detectDevice("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0)");
        assertThat(mobile).isEqualTo("mobile");
    }

    @Test
    void detectDevice_desktopUserAgent_returnsDesktop() {
        String desktop = Base62Encoder.detectDevice("Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        assertThat(desktop).isEqualTo("desktop");
    }

    @Test
    void encode_zero_returnsFirstChar() {
        assertThat(encoder.encode(0)).isEqualTo("a");
    }
}
