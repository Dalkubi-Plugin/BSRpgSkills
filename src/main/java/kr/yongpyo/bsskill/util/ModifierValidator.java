package kr.yongpyo.bsskill.util;

import java.util.Locale;

/**
 * modifier 키와 값을 검증하는 유틸리티입니다.
 * GUI 입력으로 구조를 깨뜨리는 잘못된 키가 저장되지 않도록 막아줍니다.
 */
public final class ModifierValidator {

    private ModifierValidator() {
    }

    public static ValidationResult validateCustomModifierKey(String rawKey) {
        String key = rawKey == null ? "" : rawKey.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return ValidationResult.failure("modifier 이름이 비어 있습니다.");
        }

        if ("damage".equals(key)) {
            return ValidationResult.failure("damage는 기본 데미지 전용 버튼으로 수정해주세요.");
        }

        if (key.contains(":")) {
            return ValidationResult.failure("modifier 이름에는 ':' 문자를 사용할 수 없습니다.");
        }

        return ValidationResult.success(key);
    }

    public static ValueResult validateNumericValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ValueResult.failure("modifier 값이 비어 있습니다.");
        }

        try {
            double value = Double.parseDouble(rawValue.trim());
            if (!Double.isFinite(value)) {
                return ValueResult.failure("modifier 값은 유한한 숫자여야 합니다.");
            }
            if (Math.abs(value) > 1_000_000) {
                return ValueResult.failure("modifier 값이 너무 큽니다.");
            }
            return ValueResult.success(value);
        } catch (NumberFormatException exception) {
            return ValueResult.failure("modifier 값은 숫자여야 합니다.");
        }
    }

    public record ValidationResult(boolean valid, String normalizedKey, String message) {
        public static ValidationResult success(String normalizedKey) {
            return new ValidationResult(true, normalizedKey, "");
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, "", message);
        }
    }

    public record ValueResult(boolean valid, double value, String message) {
        public static ValueResult success(double value) {
            return new ValueResult(true, value, "");
        }

        public static ValueResult failure(String message) {
            return new ValueResult(false, 0.0, message);
        }
    }
}
