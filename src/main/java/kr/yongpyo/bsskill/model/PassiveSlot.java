package kr.yongpyo.bsskill.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 패시브 스킬 데이터 모델 (TIMER 모드)
 * <p>
 * 전투 모드 + 무기를 들고 있을 때 {@code timer}초 간격으로 자동 시전된다.
 * 무기를 내리거나 전투 모드 해제 시 스케줄러가 즉시 취소된다.
 * </p>
 *
 * <h3>YAML 구조</h3>
 * <pre>
 * passives:
 *   passive1:
 *     type: SOUL_LINK       # MythicMobs 스킬 ID
 *     timer: 2              # 실행 주기 (초)
 *     cooldown: 0           # 쿨타임 (초, 0이면 없음)
 *     heal: 2               # 커스텀 모디파이어
 *     healinterval: 1       # 커스텀 모디파이어
 * </pre>
 *
 * <p>
 * {@code type}, {@code timer}, {@code cooldown}을 제외한 모든 키값은
 * {@link #modifiers}에 저장되어 {@code registerModifier(key, value)}로 전달됨.
 * MythicMobs YML에서 {@code <modifier.heal>}, {@code <modifier.healinterval>} 등으로 참조.
 * </p>
 */
public class PassiveSlot {

    /** YAML에서 표준 키로 인식하는 필드 (modifiers 맵에 포함하지 않을 키) */
    private static final java.util.Set<String> STANDARD_KEYS =
            java.util.Set.of("type", "timer", "cooldown");

    private final int index;

    /** MythicMobs 스킬 ID */
    private String type = "";

    /** 자동 발동 주기 (초) — 0.1 미만 불허 */
    private double timer = 10.0;

    /** 쿨타임 (초) — 0이면 쿨타임 없음 */
    private double cooldown = 0.0;

    /** type, timer, cooldown 외의 모든 키값 → registerModifier로 전달 */
    private final Map<String, Double> modifiers = new LinkedHashMap<>();

    public PassiveSlot(int index) { this.index = index; }

    /**
     * 유효한 패시브인지 확인
     * @return type이 비어있지 않고 timer가 유효하면 true
     */
    public boolean isValid() {
        return type != null && !type.isBlank() && timer >= 0.1;
    }

    /**
     * 주어진 키가 표준 키인지 (modifier가 아닌지) 확인
     */
    public static boolean isStandardKey(String key) {
        return STANDARD_KEYS.contains(key);
    }

    // Getters & Setters
    public int getIndex() { return index; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public double getTimer() { return timer; }
    public void setTimer(double v) { this.timer = Math.max(0.1, v); }
    public double getCooldown() { return cooldown; }
    public void setCooldown(double v) { this.cooldown = Math.max(0, v); }
    public Map<String, Double> getModifiers() { return Collections.unmodifiableMap(modifiers); }
    public void putModifier(String key, double value) { modifiers.put(key, value); }
    public void clearModifiers() { modifiers.clear(); }
}
