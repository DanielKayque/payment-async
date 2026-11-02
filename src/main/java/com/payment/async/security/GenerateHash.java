package com.payment.async.security;

import com.payment.async.features.payment.CreatePaymentRequestDto;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class GenerateHash {

    public static String generateHash(CreatePaymentRequestDto dto){
        String content = dto.amountCents().toString();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    content.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não está disponível", e);
        }
    }
}
