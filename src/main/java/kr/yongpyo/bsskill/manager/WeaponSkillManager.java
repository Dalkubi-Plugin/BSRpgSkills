package kr.yongpyo.bsskill.manager;

import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.model.PassiveSlot;
import kr.yongpyo.bsskill.model.SkillSlot;
import kr.yongpyo.bsskill.model.WeaponSkill;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 무기 스킬 데이터 매니저
 * <p>
 * {@code plugins/BSSkill/weapons/{MMOITEMS_ITEM_ID}.yml} 파일을 파싱하여
 * {@link WeaponSkill} 객체로 캐싱한다. 로드 시 각 스킬/패시브의 유효성을 검증하고
 * 상세 로그를 콘솔에 출력한다.
 * </p>
 */
public class WeaponSkillManager {

    private final BSSkill plugin;
    private final File weaponsFolder;
    private final Map<String, WeaponSkill> cache = new ConcurrentHashMap<>();

    /** jar 내장 기본 무기 파일 */
    private static final String[] DEFAULT_WEAPONS = {
            "AWAKENED_ARCHER_GALEBOW.yml",
            "AWAKENED_SHAMAN_LIFESCEPTER.yml"
    };

    public WeaponSkillManager(BSSkill plugin) {
        this.plugin = plugin;
        this.weaponsFolder = new File(plugin.getDataFolder(), "weapons");
    }

    // ===================================================================
    // 로드
    // ===================================================================

    public void loadAll() {
        cache.clear();
        if (!weaponsFolder.exists()) weaponsFolder.mkdirs();
        extractDefaults();

        File[] files = weaponsFolder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("[WeaponSkillManager] weapons 폴더에 파일 없음");
            return;
        }

        int loaded = 0;
        for (File file : files) {
            try {
                WeaponSkill weapon = parseFile(file);
                if (weapon != null) {
                    cache.put(weapon.getWeaponId(), weapon);
                    loaded++;
                    logValidation(weapon); // 검증 로그 출력
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[WeaponSkillManager] 로드 실패: " + file.getName(), e);
            }
        }
        plugin.getLogger().info("[WeaponSkillManager] " + loaded + "개 무기 로드 완료");
    }

    private void extractDefaults() {
        for (String name : DEFAULT_WEAPONS) {
            if (!new File(weaponsFolder, name).exists()) {
                plugin.saveResource("weapons/" + name, false);
            }
        }
    }

    // ===================================================================
    // 파싱
    // ===================================================================

    private WeaponSkill parseFile(File file) {
        String weaponId = file.getName().replace(".yml", "");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        WeaponSkill weapon = new WeaponSkill(weaponId);
        weapon.setDisplayName(config.getString("display-name", weaponId));

        // ── 액티브 스킬 파싱 ──
        ConfigurationSection skillsSec = config.getConfigurationSection("skills");
        if (skillsSec != null) {
            for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
                ConfigurationSection slotSec = skillsSec.getConfigurationSection("slot-" + i);
                if (slotSec == null) continue;

                SkillSlot slot = weapon.getSkill(i);
                slot.setMythicId(slotSec.getString("mythic-id", ""));
                slot.setDisplayName(slotSec.getString("display-name", "<gray>비어있음</gray>"));
                slot.setCooldown(slotSec.getDouble("cooldown", 0));
                slot.setDamage(slotSec.getDouble("damage", 0));
                slot.setIcon(slotSec.getString("icon", "BARRIER"));
                slot.setDescription(slotSec.getString("description", ""));
                slot.setEnabled(slotSec.getBoolean("enabled", false));

                // extra 섹션 → 커스텀 모디파이어
                ConfigurationSection extraSec = slotSec.getConfigurationSection("extra");
                if (extraSec != null) {
                    slot.clearExtras();
                    for (String key : extraSec.getKeys(false)) {
                        slot.putExtra(key, extraSec.get(key));
                    }
                }
            }
        }

        // ── 패시브 파싱 ──
        ConfigurationSection passivesSec = config.getConfigurationSection("passives");
        if (passivesSec != null) {
            weapon.clearPassives();
            int idx = 0;
            for (String key : passivesSec.getKeys(false)) {
                ConfigurationSection pSec = passivesSec.getConfigurationSection(key);
                if (pSec == null) continue;

                idx++;
                PassiveSlot passive = new PassiveSlot(idx);
                passive.setType(pSec.getString("type", ""));
                passive.setTimer(pSec.getDouble("timer", 10.0));
                passive.setCooldown(pSec.getDouble("cooldown", 0));

                // type, timer, cooldown 외 모든 키 → 모디파이어
                passive.clearModifiers();
                for (String pKey : pSec.getKeys(false)) {
                    if (!PassiveSlot.isStandardKey(pKey)) {
                        passive.putModifier(pKey, toDouble(pSec.get(pKey)));
                    }
                }

                // 유효성 경고
                if (passive.getType().isBlank()) {
                    plugin.getLogger().warning("[" + weaponId + "] " + key + ": type이 비어있음 — 스킵");
                    continue;
                }
                if (passive.getTimer() < 0.1) {
                    plugin.getLogger().warning("[" + weaponId + "] " + key + ": timer가 0.1초 미만 → 0.1로 보정");
                }

                weapon.addPassive(passive);
            }
        }

        return weapon;
    }

    // ===================================================================
    // 검증 로그 (서버 시작 시 콘솔 출력)
    // ===================================================================

    /**
     * 무기 데이터의 유효성을 콘솔에 출력
     * - 각 스킬/패시브의 type, modifiers 목록 표시
     * - 누락된 필수 값 경고
     */
    private void logValidation(WeaponSkill weapon) {
        plugin.getLogger().info("─── " + weapon.getWeaponId() + " (" + weapon.getDisplayName() + ") ───");

        // 액티브 스킬 검증
        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            SkillSlot s = weapon.getSkill(i);
            if (s.isValid()) {
                StringBuilder sb = new StringBuilder();
                sb.append("  [슬롯 ").append(i).append("] ").append(s.getMythicId());
                sb.append(" | DMG:").append(s.getDamage());
                sb.append(" | CD:").append(s.getCooldown()).append("s");
                if (!s.getExtraParams().isEmpty()) {
                    sb.append(" | extra:").append(s.getExtraParams().keySet());
                }
                plugin.getLogger().info(sb.toString());
            } else if (!s.getMythicId().isBlank()) {
                plugin.getLogger().warning("  [슬롯 " + i + "] " + s.getMythicId() + " — enabled=false");
            }
        }

        // 패시브 검증
        for (PassiveSlot p : weapon.getPassives()) {
            StringBuilder sb = new StringBuilder();
            sb.append("  [패시브] ").append(p.getType());
            sb.append(" | timer:").append(p.getTimer()).append("s");
            if (p.getCooldown() > 0) sb.append(" | CD:").append(p.getCooldown()).append("s");
            if (!p.getModifiers().isEmpty()) {
                sb.append(" | modifiers:").append(p.getModifiers());
            }
            plugin.getLogger().info(sb.toString());
        }
    }

    // ===================================================================
    // 저장
    // ===================================================================

    public void save(WeaponSkill weapon) {
        File file = new File(weaponsFolder, weapon.getWeaponId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        config.set("display-name", weapon.getDisplayName());

        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            SkillSlot s = weapon.getSkill(i);
            String p = "skills.slot-" + i;
            config.set(p + ".mythic-id", s.getMythicId());
            config.set(p + ".display-name", s.getDisplayName());
            config.set(p + ".cooldown", s.getCooldown());
            config.set(p + ".damage", s.getDamage());
            config.set(p + ".icon", s.getIcon());
            config.set(p + ".description", s.getDescription());
            config.set(p + ".enabled", s.isEnabled());
            if (!s.getExtraParams().isEmpty()) {
                for (var e : s.getExtraParams().entrySet()) {
                    config.set(p + ".extra." + e.getKey(), e.getValue());
                }
            }
        }

        int pidx = 0;
        for (PassiveSlot ps : weapon.getPassives()) {
            pidx++;
            String p = "passives.passive" + pidx;
            config.set(p + ".type", ps.getType());
            config.set(p + ".timer", ps.getTimer());
            config.set(p + ".cooldown", ps.getCooldown());
            for (var e : ps.getModifiers().entrySet()) {
                config.set(p + "." + e.getKey(), e.getValue());
            }
        }

        try { config.save(file); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "저장 실패: " + file.getName(), e); }
    }

    public void saveAll() {
        cache.values().forEach(this::save);
        plugin.getLogger().info("[WeaponSkillManager] 전체 저장 완료 (" + cache.size() + "개)");
    }

    // ===================================================================
    // 조회
    // ===================================================================

    public WeaponSkill getWeapon(String id) { return cache.get(id); }
    public boolean hasWeapon(String id) { return cache.containsKey(id); }
    public Collection<WeaponSkill> getAllWeapons() { return Collections.unmodifiableCollection(cache.values()); }
    public int size() { return cache.size(); }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) { try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {} }
        return 0.0;
    }
}
