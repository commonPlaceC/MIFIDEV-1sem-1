package ru.example.url.shortener.service;

import ru.example.url.shortener.storage.UrlStorage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class UrlGenerator {
    private static final String BASE62_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SHORT_CODE_LENGTH = 7;
    private static final int MAX_COLLISION_ATTEMPTS = 10;
    private static final int HEX_TRUNCATION_LENGTH = 15;

    private final UrlStorage urlStorage;

    public UrlGenerator(UrlStorage urlStorage) {
        this.urlStorage = urlStorage;
    }

    public String generateShortCode(String originalUrl, UUID userId) {
        String uniqueInput = createUniqueInput(originalUrl, userId);
        String hash = computeMD5Hash(uniqueInput);
        String shortCode = extractBase62Code(hash);

        shortCode = resolveCollisions(shortCode, uniqueInput);

        return shortCode;
    }

    private String createUniqueInput(String originalUrl, UUID userId) {
        return userId.toString() + originalUrl + System.currentTimeMillis();
    }

    private String extractBase62Code(String hash) {
        return encodeToBase62(hash).substring(0, SHORT_CODE_LENGTH);
    }

    private String resolveCollisions(String initialCode, String baseInput) {
        String currentCode = initialCode;
        String currentInput = baseInput;

        for (int attempt = 0; attempt < MAX_COLLISION_ATTEMPTS; attempt++) {
            if (!urlStorage.isShortCodeExists(currentCode)) {
                return currentCode;
            }

            currentInput = baseInput + attempt;
            String hash = computeMD5Hash(currentInput);
            currentCode = extractBase62Code(hash);
        }

        return createFallbackCode();
    }

    private String createFallbackCode() {
        return generateRandomCode();
    }

    private static String computeMD5Hash(String input) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] hashBytes = messageDigest.digest(input.getBytes(StandardCharsets.UTF_8));

            return convertToHexString(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException("MD5 algorithm not available", exception);
        }
    }

    private static String convertToHexString(byte[] bytes) {
        StringBuilder hexBuilder = new StringBuilder();

        for (byte currentByte : bytes) {
            String hex = Integer.toHexString(0xff & currentByte);
            if (hex.length() == 1) {
                hexBuilder.append('0');
            }
            hexBuilder.append(hex);
        }

        return hexBuilder.toString();
    }

    private static String encodeToBase62(String hexString) {
        long numericValue = convertHexToLong(hexString);

        if (numericValue == 0) {
            return String.valueOf(BASE62_CHARACTERS.charAt(0));
        }

        return buildBase62String(numericValue);
    }

    private static long convertHexToLong(String hexString) {
        String truncatedHex = hexString.length() > HEX_TRUNCATION_LENGTH ?
                hexString.substring(0, HEX_TRUNCATION_LENGTH) : hexString;
        return Long.parseUnsignedLong(truncatedHex, 16);
    }

    private static String buildBase62String(long number) {
        StringBuilder resultBuilder = new StringBuilder();

        while (number > 0) {
            int index = (int) (number % BASE62_CHARACTERS.length());
            resultBuilder.append(BASE62_CHARACTERS.charAt(index));
            number /= BASE62_CHARACTERS.length();
        }

        return resultBuilder.reverse().toString();
    }

    private static String generateRandomCode() {
        return IntStream.range(0, SHORT_CODE_LENGTH)
                .mapToObj(UrlGenerator::generateRandomCharacter)
                .collect(Collectors.joining());
    }

    private static String generateRandomCharacter(int index) {
        int randomIndex = (int) (Math.random() * BASE62_CHARACTERS.length());
        return String.valueOf(BASE62_CHARACTERS.charAt(randomIndex));
    }

    public boolean isValidShortCode(String shortCode) {
        if (shortCode == null || shortCode.isEmpty()) {
            return false;
        }

        return shortCode.chars()
                .allMatch(UrlGenerator::isValidBase62Character);
    }

    private static boolean isValidBase62Character(int character) {
        return BASE62_CHARACTERS.indexOf(character) != -1;
    }
}