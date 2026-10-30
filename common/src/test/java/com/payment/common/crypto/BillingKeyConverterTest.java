package com.payment.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class BillingKeyConverterTest {

    // 테스트 전용 32바이트(AES-256) 키. 운영 비밀값과는 무관하다.
    private static final String TEST_SECRET =
        Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final BillingKeyConverter converter = new BillingKeyConverter(TEST_SECRET);

    @Test
    void encryptsAndDecryptsBackToTheOriginalValue() {
        String billingKey = "billing_key_abcdef1234567890";

        String encrypted = converter.convertToDatabaseColumn(billingKey);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(encrypted).isNotEqualTo(billingKey);
        assertThat(decrypted).isEqualTo(billingKey);
    }

    @Test
    void sameValueProducesDifferentCiphertextEachTime() {
        String billingKey = "same_billing_key";

        String first = converter.convertToDatabaseColumn(billingKey);
        String second = converter.convertToDatabaseColumn(billingKey);

        // 매번 새 랜덤 IV를 쓰므로 평문이 같아도 암호문은 달라야 한다(패턴 분석 방지).
        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo(billingKey);
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo(billingKey);
    }

    @Test
    void nullsPassThroughUnchanged() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
