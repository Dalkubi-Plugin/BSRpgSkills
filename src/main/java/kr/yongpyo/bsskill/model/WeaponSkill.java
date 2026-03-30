package kr.yongpyo.bsskill.model;

import java.util.*;

/**
 * 무기별 스킬 데이터 모델
 * MMOITEMS_ITEM_ID에 대응하며, 6 액티브 + N 패시브 보유
 */
public class WeaponSkill {

    public static final int MAX_SLOTS = 6;

    private final String weaponId;
    private String mmoType = "SWORD";
    private String displayName;
    private final Map<Integer, SkillSlot> skills = new LinkedHashMap<>();
    private final List<PassiveSlot> passives = new ArrayList<>();

    public WeaponSkill(String weaponId) {
        this.weaponId = weaponId;
        this.displayName = weaponId;
        for (int i = 1; i <= MAX_SLOTS; i++) skills.put(i, new SkillSlot(i));
    }

    public SkillSlot getSkill(int n) { return skills.get(n); }
    public Map<Integer, SkillSlot> getSkills() { return Collections.unmodifiableMap(skills); }
    public int getActiveSkillCount() { return (int) skills.values().stream().filter(SkillSlot::isValid).count(); }
    public List<PassiveSlot> getPassives() { return Collections.unmodifiableList(passives); }
    public void addPassive(PassiveSlot p) { passives.add(p); }
    public void clearPassives() { passives.clear(); }

    public String getWeaponId() { return weaponId; }
    public String getMmoType() { return mmoType; }
    public void setMmoType(String v) { this.mmoType = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
}
