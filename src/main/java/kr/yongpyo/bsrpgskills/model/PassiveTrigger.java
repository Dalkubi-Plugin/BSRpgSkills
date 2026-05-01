package kr.yongpyo.bsrpgskills.model;

import java.util.Locale;

/**
 * 패시브 스킬의 발동 조건입니다.
 *
 * - TIMER: 일정 주기마다 자동 발동
 * - ON_DAMAGE_TAKEN: 플레이어가 피격되었을 때 발동
 * - ON_DAMAGE_DEALT: 플레이어가 가격했을 때 발동
 */
public enum PassiveTrigger {
    TIMER,
    ON_DAMAGE_TAKEN,
    ON_DAMAGE_DEALT;

    /**
     * 문자열에서 트리거를 안전하게 파싱합니다.
     * 비어 있거나 알 수 없는 값이면 TIMER를 반환합니다.
     */
    public static PassiveTrigger parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return TIMER;
        }
        try {
            return PassiveTrigger.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TIMER;
        }
    }

    /**
     * 이벤트 기반 트리거 여부를 반환합니다.
     */
    public boolean isEventTrigger() {
        return this != TIMER;
    }

    /**
     * GUI 표시용 한글 라벨입니다.
     */
    public String getLabel() {
        return switch (this) {
            case TIMER -> "주기 발동";
            case ON_DAMAGE_TAKEN -> "피격 시";
            case ON_DAMAGE_DEALT -> "가격 시";
        };
    }

    /**
     * 다음 트리거로 순환합니다.
     */
    public PassiveTrigger next() {
        PassiveTrigger[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
