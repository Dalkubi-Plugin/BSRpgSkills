package kr.yongpyo.bsrpgskills.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
    private int maxLevel = 1;

    /**
     * 무기 설정 파일에서 읽어온 modifier 원본입니다.
     * 예: damage, radius, amount, duration, heal 등
     */
    private final Map<String, Double> modifiers = new LinkedHashMap<>();

    /**
     * 레벨별 오버라이드 값입니다. cooldown / damage 만 레벨에 따라 달라지며,
     * 그 외 modifier(ratio, duration 등)는 base modifiers 값을 그대로 사용합니다.
     */
    private final TreeMap<Integer, LevelOverride> levels = new TreeMap<>();

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

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = Math.max(1, maxLevel);
    }

    public boolean isLevelable() {
        return maxLevel > 1;
    }

    public Map<Integer, LevelOverride> getLevels() {
        return Collections.unmodifiableMap(levels);
    }

    public void putLevel(int level, LevelOverride override) {
        if (level < 2 || override == null) {
            return;
        }
        levels.put(level, override);
    }

    public void clearLevels() {
        levels.clear();
    }

    /**
     * 요청한 레벨에서 적용할 쿨타임을 반환합니다.
     * level-N 정의가 없으면 가장 가까운 하위 레벨 값을 사용하며,
     * 어떤 정의도 없으면 base cooldown을 반환합니다.
     */
    public double getCooldownForLevel(int level) {
        if (level <= 1 || levels.isEmpty()) {
            return cooldown;
        }
        int clamped = Math.min(level, maxLevel);
        Map.Entry<Integer, LevelOverride> entry = levels.floorEntry(clamped);
        while (entry != null) {
            Double override = entry.getValue().getCooldown();
            if (override != null) {
                return override;
            }
            entry = levels.lowerEntry(entry.getKey());
        }
        return cooldown;
    }

    /**
     * 요청한 레벨에서 적용할 데미지를 반환합니다.
     * level-N 정의가 없으면 가장 가까운 하위 레벨 값을 사용하며,
     * 어떤 정의도 없으면 base damage(modifiers.damage)를 반환합니다.
     */
    public double getDamageForLevel(int level) {
        if (level <= 1 || levels.isEmpty()) {
            return getDamage();
        }
        int clamped = Math.min(level, maxLevel);
        Map.Entry<Integer, LevelOverride> entry = levels.floorEntry(clamped);
        while (entry != null) {
            Double override = entry.getValue().getDamage();
            if (override != null) {
                return override;
            }
            entry = levels.lowerEntry(entry.getKey());
        }
        return getDamage();
    }

    /**
     * 추가 lore 라인을 포함해 해당 레벨에서 적용할 새 설명을 만듭니다.
     * 레벨별 lore가 하나도 없으면 base description을 그대로 반환합니다.
     * lore 문자열 내 {damage}, {cooldown} 토큰은 해당 레벨의 실제 값으로 치환됩니다.
     * {cooldown} 토큰이 포함된 줄은 해당 레벨의 쿨다운이 base와 동일하면 전체 줄이 제거됩니다.
     */
    public List<String> getDescriptionLinesForLevel(int level) {
        List<String> lines = new ArrayList<>();
        if (description != null && !description.isBlank()) {
            lines.add(description);
        }
        if (!levels.isEmpty()) {
            int clamped = Math.min(Math.max(level, 1), maxLevel);
            for (var entry : levels.entrySet()) {
                if (entry.getKey() > clamped) {
                    break;
                }
                List<String> extra = entry.getValue().getLore();
                if (extra != null) {
                    for (String line : extra) {
                        String resolved = resolveLoreLine(line, entry.getKey());
                        if (resolved != null) {
                            lines.add(resolved);
                        }
                    }
                }
            }
        }
        return lines;
    }

    /**
     * 특정 레벨의 lore 줄만 (누적 아님) 토큰 치환 후 반환합니다.
     * GUI에서 다음 레벨 미리보기에 사용합니다.
     */
    public List<String> getResolvedLoreForSingleLevel(int level) {
        LevelOverride override = levels.get(level);
        if (override == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String line : override.getLore()) {
            String resolved = resolveLoreLine(line, level);
            if (resolved != null) {
                result.add(resolved);
            }
        }
        return result;
    }

    /**
     * lore 줄 내 {damage} / {cooldown} 토큰을 해당 레벨의 실제 값으로 치환합니다.
     * {cooldown} 포함 줄에서 해당 레벨 쿨다운이 base와 동일하면 null 반환(줄 제거).
     */
    private String resolveLoreLine(String line, int level) {
        if (line == null || line.isBlank()) {
            return null;
        }
        if (line.contains("{cooldown}")) {
            double levelCd = getCooldownForLevel(level);
            if (Double.compare(levelCd, cooldown) == 0) {
                return null;
            }
            line = line.replace("{cooldown}", formatLoreValue(levelCd));
        }
        if (line.contains("{damage}")) {
            line = line.replace("{damage}", formatLoreValue(getDamageForLevel(level)));
        }
        return line;
    }

    private String formatLoreValue(double value) {
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) < 1e-9) {
            return String.valueOf(rounded);
        }
        String s = String.format("%.2f", value);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    /**
     * 특정 레벨의 cooldown override 값을 설정합니다. null 전달 시 해당 필드만 비웁니다.
     * 다른 필드들은 보존됩니다. 모든 필드가 비면 해당 레벨 항목을 자동 제거합니다.
     */
    public void setLevelCooldown(int level, Double value) {
        if (level < 2) {
            return;
        }
        LevelOverride existing = levels.get(level);
        LevelOverride next = new LevelOverride(
                value,
                existing != null ? existing.getDamage() : null,
                existing != null ? existing.getLore() : List.of()
        );
        applyOrRemove(level, next);
    }

    public void setLevelDamage(int level, Double value) {
        if (level < 2) {
            return;
        }
        LevelOverride existing = levels.get(level);
        LevelOverride next = new LevelOverride(
                existing != null ? existing.getCooldown() : null,
                value,
                existing != null ? existing.getLore() : List.of()
        );
        applyOrRemove(level, next);
    }

    public void setLevelLore(int level, List<String> lore) {
        if (level < 2) {
            return;
        }
        LevelOverride existing = levels.get(level);
        LevelOverride next = new LevelOverride(
                existing != null ? existing.getCooldown() : null,
                existing != null ? existing.getDamage() : null,
                lore == null ? List.of() : lore
        );
        applyOrRemove(level, next);
    }

    public void removeLevel(int level) {
        levels.remove(level);
    }

    private void applyOrRemove(int level, LevelOverride override) {
        if (override.isEmpty()) {
            levels.remove(level);
        } else {
            levels.put(level, override);
        }
    }

    /**
     * 레벨별 오버라이드 값. null 필드는 base 값을 그대로 사용한다는 의미입니다.
     */
    public static class LevelOverride {
        private final Double cooldown;
        private final Double damage;
        private final List<String> lore;

        public LevelOverride(Double cooldown, Double damage, List<String> lore) {
            this.cooldown = cooldown;
            this.damage = damage;
            this.lore = lore == null ? List.of() : List.copyOf(lore);
        }

        public Double getCooldown() {
            return cooldown;
        }

        public Double getDamage() {
            return damage;
        }

        public List<String> getLore() {
            return lore;
        }

        public boolean isEmpty() {
            return cooldown == null && damage == null && lore.isEmpty();
        }
    }
}
