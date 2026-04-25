package io.github.ZeroFishKoZGuard.benchmark;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TestDataGenerator {
    private static final String[] VALID_ADDRESSES = {
            "0x1234567890abcdef1234567890abcdef12345678",
            "0xabcdef1234567890abcdef1234567890abcdef12",
            "0x1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b",
            "0x0000000000000000000000000000000000000000",
            "0xffffffffffffffffffffffffffffffffffffffff"
    };

    private static final String[] VALID_CHAIN_IDS = {"1", "56", "137", "42161", "10"};
    private static final int[] PAYMENT_CODE_LENGTHS = {6};

    /**
     * 生成随机有效的钱包地址
     */
    public static String randomValidAddress() {
        return VALID_ADDRESSES[ThreadLocalRandom.current().nextInt(VALID_ADDRESSES.length)];
    }

    /**
     * 生成随机有效的支付码（指定长度）
     */
    public static String randomValidPaymentCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 生成随机有效的支付码（随机长度）
     */
    public static String randomValidPaymentCode() {
        int len = PAYMENT_CODE_LENGTHS[ThreadLocalRandom.current().nextInt(PAYMENT_CODE_LENGTHS.length)];
        return randomValidPaymentCode(len);
    }

    /**
     * 生成随机有效的链ID
     */
    public static String randomValidChainId() {
        return VALID_CHAIN_IDS[ThreadLocalRandom.current().nextInt(VALID_CHAIN_IDS.length)];
    }

    /**
     * 生成随机有效的金额（正数，精度≤2）
     */
    public static Double randomValidAmount() {
        double val = ThreadLocalRandom.current().nextDouble(1, 10000);
        return Math.round(val * 100) / 100.0;
    }

    /**
     * 生成随机无效地址（各种错误）
     */
    public static String randomInvalidAddress() {
        int type = ThreadLocalRandom.current().nextInt(4);
        switch (type) {
            case 0:
                return "0x" + randomHexString(40);
            case 1:
                return "0x" + randomHexString(41);
            case 2:
                return "0x" + randomHexString(42).toUpperCase();
            case 3:
                return "invalid_addr";
            default:
                return "0x1234";
        }
    }

    /**
     * 生成随机无效链ID
     */
    public static String randomInvalidChainId() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(1000, 10000));
    }

    /**
     * 生成随机无效金额（负数、零、超精度）
     */
    public static Double randomInvalidAmount() {
        int type = ThreadLocalRandom.current().nextInt(3);
        switch (type) {
            case 0:
                return -ThreadLocalRandom.current().nextDouble(1, 100);
            case 1:
                return 0.0;
            case 2:
                return 999999.9999;
            default:
                return null;
        }
    }

    private static String randomHexString(int length) {
        String chars = "0123456789abcdef";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(16)));
        }
        return sb.toString();
    }

    /**
     * 生成一个完整的有效数据Map
     */
    public static Map<String, Object> validData() {
        Map<String, Object> data = new HashMap<>();
        data.put("walletAddr", randomValidAddress());
        data.put("paymentCode", randomValidPaymentCode());
        data.put("chainId", randomValidChainId());
        data.put("transferAmount", randomValidAmount());
        return data;
    }

    /**
     * 生成一个随机数据，可能有效也可能无效（按比例）
     */
    public static Map<String, Object> mixedData(double validProbability) {
        if (ThreadLocalRandom.current().nextDouble() < validProbability) {
            return validData();
        }
        Map<String, Object> data = validData();
        int field = ThreadLocalRandom.current().nextInt(4);
        switch (field) {
            case 0:
                data.put("walletAddr", randomInvalidAddress());
                break;
            case 1:
                data.put("paymentCode", randomInvalidPaymentCode());
                break;
            case 2:
                data.put("chainId", randomInvalidChainId());
                break;
            case 3:
                data.put("transferAmount", randomInvalidAmount());
                break;
        }
        return data;
    }

    private static String randomInvalidPaymentCode() {
        int type = ThreadLocalRandom.current().nextInt(3);
        switch (type) {
            case 0:
                return randomHexString(6);
            case 1:
                return "123";
            case 2:
                return "1234567890";
            default:
                return null;
        }
    }
}