package kr.yongpyo.bsrpgskills.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 패시브 스킬 데이터 모델입니다.
 *
 * 트리거 종류
 * - TIMER: 일정 주기(interval)마다 자동 발동
 * - ON_DAMAGE_TAKEN: 플레이어가 피격당했을 때 발동
 * - ON_DAMAGE_DEALT: 플레이어가 무기 평타로 가격했을 때 발동
 *
 * STANDARD_KEYS는 구버전 평면 구조에서 modifiers 섹션이 생략된 경우,
 * 메타 데이터가 modifier로 잘못 등록되지 않도록 보호합니다.
 */
public class PassiveSlot {

    private static final Set<String> STANDARD_KEYS = Set.of(
            "type", "trigger",
            "timer", "cooldown",
            "timing", "display", "modifiers",
            "display-name", "icon", "custom-model-data", "description", "enabled"
    );

    private final int index;
    private String type = "";
    private String displayName = "<gray>비어 있음</gray>";
    private PassiveTrigger triggerType = PassiveTrigger.TIMER;
    private double timer = 10.0;
    private double cooldown = 0.0;
    private String icon = "ENDER_EYE";
    private int customModelData = 0;
    private String description = "";
    private boolean enabled = true;
    private final Map<String, Double> modifiers = new LinkedHashMap<>();

    public PassiveSlot(int index) {
        this.index = index;
    }

    /**
     * 패시브가 실제로 동작 가능한 상태인지 검증합니다.
     * TIMER 패시브는 timer 간격이 유효해야 하고, 이벤트 패시브는 type만 지정되면 충분합니다.
     */
    public boolean isValid() {
        if (!enabled || type == null || type.isBlank()) {
            return false;
        }
        if (triggerType == PassiveTrigger.TIMER) {
            return timer >= 0.1;
        }
        return true;
    }

    public static boolean isStandardKey(String key) {
        return STANDARD_KEYS.contains(key);
    }

    public int getIndex() {
        return index;
    }

    public String getType() {
        return type;
    }

    public void setType(String value) {
        this.type = value == null ? "" : value;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String value) {
        this.displayName = value == null ? "" : value;
    }

    public PassiveTrigger getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(PassiveTrigger value) {
        this.triggerType = value == null ? PassiveTrigger.TIMER : value;
    }

    public double getTimer() {
        return timer;
    }

    public void setTimer(double value) {
        this.timer = Math.max(0.1, value);
    }

    public double getCooldown() {
        return cooldown;
    }

    public void setCooldown(double value) {
        this.cooldown = Math.max(0, value);
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String value) {
        this.icon = value == null ? "ENDER_EYE" : value;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public void setCustomModelData(int value) {
        this.customModelData = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        this.description = value == null ? "" : value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public Map<String, Double> getModifiers() {
        return Collections.unmodifiableMap(modifiers);
    }

    public void putModifier(String key, double value) {
        modifiers.put(key, value);
    }

    public void clearModifiers() {
        modifiers.clear();
    }
}
