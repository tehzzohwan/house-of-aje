package com.houseofaje.payment;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaystackPaymentProviderTest {

    private static final String BASE = "https://api.paystack.co";

    @Test
    void initiateDepositPostsAssignFlowAndParsesAccount() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaystackPaymentProvider provider = new PaystackPaymentProvider(builder, BASE, "sk_test_x", "wema-bank");

        server.expect(requestTo(BASE + "/dedicated_account"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk_test_x"))
                .andRespond(withSuccess("""
                        {"status":true,"data":{"account_name":"JOHN DOE","account_number":"9930000123",
                        "bank":{"name":"Wema Bank","slug":"wema-bank"},"customer":{"customer_code":"CUS_abc"}}}
                        """, MediaType.APPLICATION_JSON));

        DepositInstructions result = provider.initiateDeposit(
                new DepositRequest("ref", new BigDecimal("4000.00"), "NGN", "a@b.com", "0800"));

        assertThat(result.providerReference()).isEqualTo("CUS_abc");
        assertThat(result.accountNumber()).isEqualTo("9930000123");
        assertThat(result.accountName()).isEqualTo("JOHN DOE");
        assertThat(result.bankName()).isEqualTo("Wema Bank");
        server.verify();
    }

    @Test
    void verifyTransactionConvertsKoboToNaira() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaystackPaymentProvider provider = new PaystackPaymentProvider(builder, BASE, "sk_test_x", "wema-bank");

        server.expect(requestTo(BASE + "/transaction/verify/txn_ref_123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":true,"data":{"status":"success","reference":"txn_ref_123","amount":400000,
                        "currency":"NGN","paid_at":"2026-01-12T09:00:00.000Z","customer":{"customer_code":"CUS_abc"}}}
                        """, MediaType.APPLICATION_JSON));

        TransactionVerification result = provider.verifyTransaction("txn_ref_123");

        assertThat(result.paid()).isTrue();
        assertThat(result.reference()).isEqualTo("txn_ref_123");
        assertThat(result.amount()).isEqualByComparingTo("4000.00"); // 400000 kobo
        assertThat(result.customerCode()).isEqualTo("CUS_abc");
        server.verify();
    }

    @Test
    void verifyTransactionReportsNotPaidWhenProviderStatusIsNotSuccess() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaystackPaymentProvider provider = new PaystackPaymentProvider(builder, BASE, "sk_test_x", "wema-bank");

        server.expect(requestTo(BASE + "/transaction/verify/txn_failed"))
                .andRespond(withSuccess("""
                        {"status":true,"data":{"status":"failed","reference":"txn_failed","amount":400000,"currency":"NGN"}}
                        """, MediaType.APPLICATION_JSON));

        TransactionVerification result = provider.verifyTransaction("txn_failed");

        assertThat(result.paid()).isFalse();
        server.verify();
    }

    @Test
    void refundPostsTransactionWithKoboAmount() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaystackPaymentProvider provider = new PaystackPaymentProvider(builder, BASE, "sk_test_x", "wema-bank");

        server.expect(requestTo(BASE + "/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"transaction\":\"txn_ref_123\",\"amount\":300000}"))
                .andRespond(withSuccess("""
                        {"status":true,"data":{"status":"processing","reference":"ref_1"}}
                        """, MediaType.APPLICATION_JSON));

        RefundResult result = provider.refund(new RefundRequest("txn_ref_123", new BigDecimal("3000.00"), "NGN"));

        assertThat(result.success()).isTrue();
        assertThat(result.providerReference()).isEqualTo("ref_1");
        server.verify();
    }
}
