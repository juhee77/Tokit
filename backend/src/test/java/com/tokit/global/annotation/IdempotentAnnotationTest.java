package com.tokit.global.annotation;

import com.tokit.domain.order.controller.OrderController;
import com.tokit.domain.wallet.controller.WalletController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotentAnnotationTest {

    @Test
    @DisplayName("@Idempotent 어노테이션 검증: RetentionPolicy가 RUNTIME 이며 Target이 METHOD 로 정의되어 있다.")
    void idempotentAnnotation_IsRetentionRuntimeAndMethodTarget() {
        // Given & When
        Idempotent annotation = DummyClass.class.getMethods()[0].getAnnotation(Idempotent.class);

        // Then
        assertThat(Idempotent.class.getAnnotation(java.lang.annotation.Retention.class).value())
                .isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Idempotent.class.getAnnotation(java.lang.annotation.Target.class).value())
                .contains(ElementType.METHOD);
    }

    @Test
    @DisplayName("@Idempotent 반영 검증: OrderController 및 WalletController CUD 메소드에 @Idempotent가 부착되어 있다.")
    void idempotentAnnotation_PresentOnControllers() throws Exception {
        // Given & When
        Method placeOrderMethod = OrderController.class.getMethod("placeOrder", String.class, OrderController.PlaceOrderRequest.class);
        Method depositKrwMethod = WalletController.class.getMethod("depositKrw", String.class, WalletController.WalletAmountRequest.class);

        // Then
        assertThat(placeOrderMethod.isAnnotationPresent(Idempotent.class)).isTrue();
        assertThat(depositKrwMethod.isAnnotationPresent(Idempotent.class)).isTrue();
    }

    static class DummyClass {
        @Idempotent
        public void dummyMethod() {}
    }
}
