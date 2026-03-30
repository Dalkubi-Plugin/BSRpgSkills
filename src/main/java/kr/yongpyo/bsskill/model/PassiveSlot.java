package kr.yongpyo.bsskill.model;

import java.util.*;

/**
 * 패시브 스킬 데이터 모델 (TIMER 자동 발동)
 * 전투 모드 + 무기 보유 시 timer 간격으로 시전
 * type/timer/cooldown/display-name/icon/custom-model-data/description/enabled 외 키 → modifiers
 */
public class PassiveSlot {

    private static final Set<String> STANDARD_KEYS = Set.of(
            "type", "timer", "cooldown", "display-name",
            "icon", "custom-model-data", "description", "enabled"
    );

    private final int index;
    private String type = "";
    private String displayName = "<gray>비어있음</gray>";
    private double timer = 10.0;
    private double cooldown = 0.0;
    private String icon = "ENDER_EYE";
    private int customModelData = 0;
    private String description = "";
    private boolean enabled = true;
    private final Map<String, Double> modifiers = new LinkedHashMap<>();

    public PassiveSlot(int index) { this.index = index; }

    public boolean isValid() { return enabled && type != null && !type.isBlank() && timer >= 0.1; }
    public static boolean isStandardKey(String k) { return STANDARD_KEYS.contains(k); }

    public int getIndex() { return index; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
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
