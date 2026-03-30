package kr.yongpyo.bsskill.model;

import java.util.*;

/**
 * 무기별 스킬 데이터 모델
 * <p>
 * MMOITEMS_ITEM_ID에 대응하며, 6개 액티브 슬롯 + N개 패시브를 보유한다.
 * 파일명({MMOITEMS_ITEM_ID}.yml)이 곧 무기 식별자.
 * </p>
 */
public class WeaponSkill {

    public static final int MAX_SLOTS = 6;

    private final String weaponId;
    private String displayName;

    /** 액티브 스킬 슬롯 (1~6) */
    private final Map<Integer, SkillSlot> skills = new LinkedHashMap<>();

    /** 패시브 스킬 목록 (TIMER 모드) */
    private final List<PassiveSlot> passives = new ArrayList<>();

    public WeaponSkill(String weaponId) {
        this.weaponId = weaponId;
        this.displayName = weaponId;
        for (int i = 1; i <= MAX_SLOTS; i++) {
            skills.put(i, new SkillSlot(i));
        }
    }

    public SkillSlot getSkill(int slotNumber) { return skills.get(slotNumber); }
    public Map<Integer, SkillSlot> getSkills() { return Collections.unmodifiableMap(skills); }
    public int getActiveSkillCount() { return (int) skills.values().stream().filter(SkillSlot::isValid).count(); }

    public List<PassiveSlot> getPassives() { return Collections.unmodifiableList(passives); }
    public void addPassive(PassiveSlot passive) { passives.add(passive); }
    public void clearPassives() { passives.clear(); }

    public String getWeaponId() { return weaponId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
}
