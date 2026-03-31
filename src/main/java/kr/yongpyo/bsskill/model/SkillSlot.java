package kr.yongpyo.bsskill.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 액티브 스킬 슬롯(1~6) 데이터 모델입니다.
 * 액티브 스킬의 기본 데미지는 modifiers.damage 하나로 관리하며,
 * 그 외 값들도 모두 modifiers를 통해 MythicLib로 전달할 수 있습니다.
 */
public class SkillSlot {

    public static final String[] KEYBIND_LABELS = {
            "", "좌클릭", "우클릭", "1번", "2번", "3번", "4번"
    };

    private final int slot;
    private String mythicId = "";
    private String displayName = "<gray>비어있음</gray>";
    private double cooldown = 0.0;
    private String icon = "BARRIER";
    private int customModelData = 0;
    private String description = "";
    private boolean enabled = false;

    /**
     * 무기 설정 파일에서 읽어온 modifier 원본입니다.
     * 예: damage, radius, amount, duration, heal 등
     */
    private final Map<String, Double> modifiers = new LinkedHashMap<>();

    public SkillSlot(int slot) {
        this.slot = slot;
        ensureDefaultModifiers();
    }

    public boolean isValid() {
        return enabled && mythicId != null && !mythicId.isBlank();
    }

    public String getKeybindLabel() {
        return (slot >= 1 && slot <= 6) ? KEYBIND_LABELS[slot] : "?";
    }

    public int getSlot() {
        return slot;
    }

    public String getMythicId() {
        return mythicId;
    }

    public void setMythicId(String mythicId) {
        this.mythicId = mythicId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public double getCooldown() {
        return cooldown;
    }

    public void setCooldown(double cooldown) {
        this.cooldown = Math.max(0, cooldown);
    }

    /**
     * legacy 호환용 getter입니다.
     * 내부적으로는 modifiers.damage 값을 기본 데미지로 사용합니다.
     */
    public double getDamage() {
        return modifiers.getOrDefault("damage", 0.0);
    }

    /**
     * legacy 호환용 setter입니다.
     * GUI와 Placeholder가 damage 이름을 계속 사용하므로 modifiers.damage와 동기화합니다.
     */
    public void setDamage(double damage) {
        putModifier("damage", Math.max(0, damage));
    }

    /**
     * ratio는 액티브 스킬 기본 계수입니다.
     * 최종 데미지는 damage * ratio 형태로 계산됩니다.
     */
    public double getRatio() {
        return modifiers.getOrDefault("ratio", 1.0);
    }

    public void setRatio(double ratio) {
        putModifier("ratio", Math.max(0, ratio));
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public void setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Double> getModifiers() {
        return Collections.unmodifiableMap(modifiers);
    }

    public void putModifier(String key, double value) {
        modifiers.put(key, value);
    }

    public void clearModifiers() {
        modifiers.clear();
        ensureDefaultModifiers();
    }

    /**
     * 기존 코드 호환용 API입니다.
     * extra는 이제 modifiers의 별칭으로 동작합니다.
     */
    public Map<String, Object> getExtraParams() {
        Map<String, Object> legacyView = new LinkedHashMap<>();
        modifiers.forEach(legacyView::put);
        legacyView.remove("damage");
        return Collections.unmodifiableMap(legacyView);
    }

    public void putExtra(String key, Object value) {
        if (value instanceof Number number) {
            putModifier(key, number.doubleValue());
            return;
        }

        try {
            putModifier(key, Double.parseDouble(String.valueOf(value)));
        } catch (Exception ignored) {
        }
    }

    public void clearExtras() {
        modifiers.entrySet().removeIf(entry -> !"damage".equals(entry.getKey()) && !"ratio".equals(entry.getKey()));
        ensureDefaultModifiers();
    }

    /**
     * 액티브 스킬은 기본적으로 ratio modifier를 항상 가지도록 유지합니다.
     * 스킬 파일에 값이 없으면 1.0으로 동작하게 만들어 MythicMobs 쪽 수식에서 바로 사용할 수 있습니다.
     */
    private void ensureDefaultModifiers() {
        modifiers.putIfAbsent("ratio", 1.0);
    }
}
