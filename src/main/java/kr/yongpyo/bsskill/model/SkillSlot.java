package kr.yongpyo.bsskill.model;

import java.util.*;

/**
 * 액티브 스킬 슬롯 (1~6) 데이터 모델
 * 슬롯 1: 좌클릭, 슬롯 2: 우클릭, 슬롯 3~6: 핫바 1~4키
 */
public class SkillSlot {

    public static final String[] KEYBIND_LABELS = {
            "", "좌클릭", "우클릭", "1키", "2키", "3키", "4키"
    };

    private final int slot;
    private String mythicId = "";
    private String displayName = "<gray>비어있음</gray>";
    private double cooldown = 0.0;
    private double damage = 0.0;
    private String icon = "BARRIER";
    private int customModelData = 0;
    private String description = "";
    private boolean enabled = false;
    private final Map<String, Object> extraParams = new LinkedHashMap<>();

    public SkillSlot(int slot) { this.slot = slot; }

    public boolean isValid() { return enabled && mythicId != null && !mythicId.isBlank(); }
    public String getKeybindLabel() { return (slot >= 1 && slot <= 6) ? KEYBIND_LABELS[slot] : "?"; }

    public int getSlot() { return slot; }
    public String getMythicId() { return mythicId; }
    public void setMythicId(String v) { this.mythicId = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public double getCooldown() { return cooldown; }
    public void setCooldown(double v) { this.cooldown = Math.max(0, v); }
    public double getDamage() { return damage; }
    public void setDamage(double v) { this.damage = Math.max(0, v); }
    public String getIcon() { return icon; }
    public void setIcon(String v) { this.icon = v; }
    public int getCustomModelData() { return customModelData; }
    public void setCustomModelData(int v) { this.customModelData = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public Map<String, Object> getExtraParams() { return Collections.unmodifiableMap(extraParams); }
    public void putExtra(String k, Object v) { extraParams.put(k, v); }
    public void clearExtras() { extraParams.clear(); }
}
