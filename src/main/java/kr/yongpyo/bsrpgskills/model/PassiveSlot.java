package kr.yongpyo.bsrpgskills.model;

import java.util.*;

/**
 * 패시브 스킬 데이터 모델
 *
 * 트리거 종류
 * - TIMER: 일정 주기(interval)마다 자동 발동
 * - ON_DAMAGE_TAKEN: 플레이어가 피격당했을 때 발동 (chance/cooldown으로 빈도 제어)
 * - ON_DAMAGE_DEALT: 플레이어가 무기 평타로 가격했을 때 발동 (chance/cooldown으로 빈도 제어)
 *
 * STANDARD_KEYS는 구버전 평탄 구조에서 modifiers 섹션이 누락된 경우의
 * 폴백 파싱이 nested 섹션(timing/display/modifiers)이나 트리거 메타데이터를
 * 잘못된 modifier로 등록하지 않도록 보호합니다.
 */
public class PassiveSlot {

    private static final Set<String> STANDARD_KEYS = Set.of(
            "type", "trigger", "chance",
            "timer", "cooldown",
            "timing", "display", "modifiers",
            "display-name", "icon", "custom-model-data", "description", "enabled"
    );

    private final int index;
    private String type = "";
    private String displayName = "<gray>비어있음</gray>";
    private PassiveTrigger triggerType = PassiveTrigger.TIMER;
    private double chance = 1.0;
    private double timer = 10.0;
    private double cooldown = 0.0;
    private String icon = "ENDER_EYE";
    private int customModelData = 0;
    private String description = "";
    private boolean enabled = true;
    private final Map<String, Double> modifiers = new LinkedHashMap<>();

    public PassiveSlot(int index) { this.index = index; }

    /**
     * 패시브가 실제로 동작 가능한 상태인지 검사합니다.
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

    public static boolean isStandardKey(String k) { return STANDARD_KEYS.contains(k); }

    public int getIndex() { return index; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public PassiveTrigger getTriggerType() { return triggerType; }
    public void setTriggerType(PassiveTrigger v) { this.triggerType = v == null ? PassiveTrigger.TIMER : v; }
    public double getChance() { return chance; }
    public void setChance(double v) { this.chance = Math.max(0.0, Math.min(1.0, v)); }
    public double getTimer() { return timer; }
    public void setTimer(double v) { this.timer = Math.max(0.1, v); }
    public double getCooldown() { return cooldown; }
    public void setCooldown(double v) { this.cooldown = Math.max(0, v); }
    public String getIcon() { return icon; }
    public void setIcon(String v) { this.icon = v; }
    public int getCustomModelData() { return customModelData; }
    public void setCustomModelData(int v) { this.customModelData = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public Map<String, Double> getModifiers() { return Collections.unmodifiableMap(modifiers); }
    public void putModifier(String k, double v) { modifiers.put(k, v); }
    public void clearModifiers() { modifiers.clear(); }
}
