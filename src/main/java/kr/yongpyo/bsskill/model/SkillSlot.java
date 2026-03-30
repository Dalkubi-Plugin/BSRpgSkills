package kr.yongpyo.bsskill.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 액티브 스킬 슬롯 (1~6) 데이터 모델
 * <pre>
 *   슬롯 1 → 좌클릭     슬롯 2 → 우클릭
 *   슬롯 3 → 1키        슬롯 4 → 2키
 *   슬롯 5 → 3키        슬롯 6 → 4키
 * </pre>
 * <p>
 * MythicLib API: {@code ModifiableSkill.registerModifier(key, double)}로
 * damage + extra 파라미터를 등록하며, MythicMobs YML에서 {@code <modifier.key>}로 참조.
 * </p>
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
    private String description = "";
    private boolean enabled = false;

    /** MythicLib registerModifier에 전달할 추가 변수 (heal, burndamage 등) */
    private final Map<String, Object> extraParams = new LinkedHashMap<>();

    public SkillSlot(int slot) { this.slot = slot; }

    public boolean isValid() { return enabled && mythicId != null && !mythicId.isBlank(); }
    public String getKeybindLabel() { return (slot >= 1 && slot <= 6) ? KEYBIND_LABELS[slot] : "?"; }

    // Getters & Setters
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
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public Map<String, Object> getExtraParams() { return Collections.unmodifiableMap(extraParams); }
    public void putExtra(String key, Object value) { extraParams.put(key, value); }
    public void clearExtras() { extraParams.clear(); }
}
