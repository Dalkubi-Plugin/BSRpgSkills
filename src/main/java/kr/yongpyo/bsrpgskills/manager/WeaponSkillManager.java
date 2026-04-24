package kr.yongpyo.bsrpgskills.manager;

import kr.yongpyo.bsrpgskills.BSRpgSkills;
import kr.yongpyo.bsrpgskills.model.PassiveSlot;
import kr.yongpyo.bsrpgskills.model.PassiveTrigger;
import kr.yongpyo.bsrpgskills.model.SkillSlot;
import kr.yongpyo.bsrpgskills.model.WeaponSkill;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 무기 스킬 YAML을 로드/저장하는 매니저입니다.
 * 권장 구조는 display / timing / modifiers 섹션 기반이며, 구버전 평탄 구조도 계속 읽을 수 있습니다.
 */
public class WeaponSkillManager {

    private static final String[] DEFAULTS = {
            "AWAKENED_ARCHER_GALEBOW.yml",
            "AWAKENED_MAGE_RUNESTAFF.yml",
            "AWAKENED_SHAMAN_LIFESCEPTER.yml",
            "AWAKENED_MARTIAL_ARTIST_TIGERFIST.yml"
    };

    private final BSRpgSkills plugin;
    private final File weaponsFolder;
    private final Map<String, WeaponSkill> cache = new ConcurrentHashMap<>();

    public WeaponSkillManager(BSRpgSkills plugin) {
        this.plugin = plugin;
        this.weaponsFolder = new File(plugin.getDataFolder(), "weapons");
    }

    public void loadAll() {
        cache.clear();

        if (!weaponsFolder.exists() && !weaponsFolder.mkdirs()) {
            plugin.getLogger().warning("무기 설정 폴더를 생성하지 못했습니다: " + weaponsFolder.getAbsolutePath());
            return;
        }

        for (String resourceName : DEFAULTS) {
            if (!new File(weaponsFolder, resourceName).exists()) {
                plugin.saveResource("weapons/" + resourceName, false);
            }
        }

        File[] files = weaponsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("로드할 무기 설정 파일이 없습니다.");
            return;
        }

        int loaded = 0;
        for (File file : files) {
            try {
                WeaponSkill weapon = parseFile(file);
                if (weapon != null) {
                    cache.put(weapon.getWeaponId(), weapon);
                    loaded++;
                    logValidation(weapon);
                }
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "무기 설정 로드 실패: " + file.getName(), exception);
            }
        }

        plugin.getLogger().info("무기 설정 " + loaded + "개를 로드했습니다.");
    }

    private WeaponSkill parseFile(File file) {
        String weaponId = file.getName().replace(".yml", "");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        WeaponSkill weapon = new WeaponSkill(weaponId);
        weapon.setMmoType(config.getString("mmo-type", "SWORD"));
        weapon.setDisplayName(config.getString("display-name", weaponId));

        readSkills(config.getConfigurationSection("skills"), weapon);
        readPassives(config.getConfigurationSection("passives"), weapon, weaponId);
        return weapon;
    }

    private void readSkills(ConfigurationSection skillsSection, WeaponSkill weapon) {
        if (skillsSection == null) {
            return;
        }

        for (int slot = 1; slot <= WeaponSkill.MAX_SLOTS; slot++) {
            ConfigurationSection section = skillsSection.getConfigurationSection("slot-" + slot);
            if (section == null) {
                continue;
            }

            SkillSlot skill = weapon.getSkill(slot);
            skill.setMythicId(readString(section, "mythic-id", ""));
            skill.setDisplayName(readString(section, "display.name", "display-name", "<gray>비어 있음</gray>"));
            skill.setCooldown(readDouble(section, "timing.cooldown", "cooldown", 0));
            skill.setIcon(readString(section, "display.icon", "icon", "BARRIER"));
            skill.setCustomModelData(readInt(section, "display.custom-model-data", "custom-model-data", 0));
            skill.setDescription(readString(section, "display.description", "description", ""));
            skill.setEnabled(readBoolean(section, "enabled", false));
            skill.clearModifiers();

            ConfigurationSection modifiers = section.getConfigurationSection("modifiers");
            if (modifiers != null) {
                for (String key : modifiers.getKeys(false)) {
                    skill.putModifier(key, toDouble(modifiers.get(key)));
                }
            }

            if (!skill.getModifiers().containsKey("damage") && section.contains("damage")) {
                skill.setDamage(section.getDouble("damage", 0));
            }

            if (!skill.getModifiers().containsKey("ratio")) {
                skill.putModifier("ratio", 1.0);
            }

            ConfigurationSection extra = section.getConfigurationSection("extra");
            if (extra != null) {
                for (String key : extra.getKeys(false)) {
                    skill.putExtra(key, extra.get(key));
                }
            }
        }
    }

    private void readPassives(ConfigurationSection passivesSection, WeaponSkill weapon, String weaponId) {
        if (passivesSection == null) {
            return;
        }

        weapon.clearPassives();
        int index = 0;
        for (String key : passivesSection.getKeys(false)) {
            ConfigurationSection section = passivesSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            index++;
            PassiveSlot passive = new PassiveSlot(index);
            passive.setType(readString(section, "type", ""));
            passive.setTriggerType(PassiveTrigger.parse(readString(section, "trigger", "TIMER")));
            passive.setChance(readDouble(section, "timing.chance", "chance", 1.0));
            passive.setDisplayName(readString(section, "display.name", "display-name", "<gray>비어 있음</gray>"));
            passive.setTimer(readDouble(section, "timing.interval", "timer", 10.0));
            passive.setCooldown(readDouble(section, "timing.cooldown", "cooldown", 0));
            passive.setIcon(readString(section, "display.icon", "icon", "ENDER_EYE"));
            passive.setCustomModelData(readInt(section, "display.custom-model-data", "custom-model-data", 0));
            passive.setDescription(readString(section, "display.description", "description", ""));
            passive.setEnabled(readBoolean(section, "enabled", true));
            passive.clearModifiers();

            ConfigurationSection modifiers = section.getConfigurationSection("modifiers");
            if (modifiers != null) {
                for (String modifierKey : modifiers.getKeys(false)) {
                    passive.putModifier(modifierKey, toDouble(modifiers.get(modifierKey)));
                }
            } else {
                for (String field : section.getKeys(false)) {
                    if (!PassiveSlot.isStandardKey(field)) {
                        passive.putModifier(field, toDouble(section.get(field)));
                    }
                }
            }

            if (passive.getType().isBlank()) {
                plugin.getLogger().warning("[" + weaponId + "] " + key + ": type 값이 비어 있습니다.");
                continue;
            }

            if (passive.getTriggerType() == PassiveTrigger.TIMER && passive.getTimer() < 0.1) {
                plugin.getLogger().warning("[" + weaponId + "] " + key + ": interval이 너무 작아 0.1로 보정됩니다.");
            }

            weapon.addPassive(passive);
        }
    }

    private void logValidation(WeaponSkill weapon) {
        if (!plugin.isDebugMode()) {
            return;
        }

        plugin.logDebug("--- " + weapon.getWeaponId() + " (" + weapon.getMmoType() + ") ---");

        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            SkillSlot skill = weapon.getSkill(i);
            if (!skill.getMythicId().isBlank()) {
                plugin.logDebug("슬롯 " + i + " | " + skill.getMythicId()
                        + " | damage:" + skill.getDamage()
                        + " | cooldown:" + skill.getCooldown() + "s"
                        + " | modifiers:" + skill.getModifiers().keySet());
            }
        }

        for (PassiveSlot passive : weapon.getPassives()) {
            plugin.logDebug("패시브 | " + passive.getType()
                    + " | trigger:" + passive.getTriggerType().name()
                    + " | interval:" + passive.getTimer() + "s"
                    + " | cooldown:" + passive.getCooldown() + "s"
                    + " | chance:" + String.format("%.0f%%", passive.getChance() * 100)
                    + " | modifiers:" + passive.getModifiers().keySet());
        }
    }

    public void save(WeaponSkill weapon) {
        File file = new File(weaponsFolder, weapon.getWeaponId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        config.set("mmo-type", weapon.getMmoType());
        config.set("display-name", weapon.getDisplayName());

        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            SkillSlot skill = weapon.getSkill(i);
            String path = "skills.slot-" + i;

            config.set(path + ".mythic-id", skill.getMythicId());
            config.set(path + ".enabled", skill.isEnabled());
            config.set(path + ".timing.cooldown", skill.getCooldown());
            config.set(path + ".display.name", skill.getDisplayName());
            config.set(path + ".display.description", skill.getDescription());
            config.set(path + ".display.icon", skill.getIcon());
            config.set(path + ".display.custom-model-data", skill.getCustomModelData());

            Map<String, Object> modifierValues = new LinkedHashMap<>();
            for (var entry : skill.getModifiers().entrySet()) {
                modifierValues.put(entry.getKey(), entry.getValue());
            }
            config.set(path + ".modifiers", modifierValues);
        }

        int passiveIndex = 0;
        for (PassiveSlot passive : weapon.getPassives()) {
            passiveIndex++;
            String path = "passives.passive-" + passiveIndex;

            config.set(path + ".type", passive.getType());
            config.set(path + ".trigger", passive.getTriggerType().name());
            config.set(path + ".enabled", passive.isEnabled());
            config.set(path + ".timing.interval", passive.getTimer());
            config.set(path + ".timing.cooldown", passive.getCooldown());
            config.set(path + ".timing.chance", passive.getChance());
            config.set(path + ".display.name", passive.getDisplayName());
            config.set(path + ".display.description", passive.getDescription());
            config.set(path + ".display.icon", passive.getIcon());
            config.set(path + ".display.custom-model-data", passive.getCustomModelData());

            Map<String, Object> modifierValues = new LinkedHashMap<>();
            for (var entry : passive.getModifiers().entrySet()) {
                modifierValues.put(entry.getKey(), entry.getValue());
            }
            config.set(path + ".modifiers", modifierValues);
        }

        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "무기 설정 저장 실패: " + file.getName(), exception);
        }
    }

    public void saveAll() {
        cache.values().forEach(this::save);
        plugin.logDebug("전체 무기 설정 저장을 마쳤습니다.");
    }

    public WeaponSkill getWeapon(String id) {
        return cache.get(id);
    }

    public boolean hasWeapon(String id) {
        return cache.containsKey(id);
    }

    public Collection<WeaponSkill> getAllWeapons() {
        return Collections.unmodifiableCollection(cache.values());
    }

    public int size() {
        return cache.size();
    }

    private String readString(ConfigurationSection section, String path, String fallback) {
        if (section.contains(path)) {
            return section.getString(path, fallback);
        }
        return fallback;
    }

    private String readString(ConfigurationSection section, String primaryPath, String legacyPath, String fallback) {
        if (section.contains(primaryPath)) {
            return section.getString(primaryPath, fallback);
        }
        if (section.contains(legacyPath)) {
            return section.getString(legacyPath, fallback);
        }
        return fallback;
    }

    private double readDouble(ConfigurationSection section, String primaryPath, String legacyPath, double fallback) {
        if (section.contains(primaryPath)) {
            return section.getDouble(primaryPath, fallback);
        }
        if (section.contains(legacyPath)) {
            return section.getDouble(legacyPath, fallback);
        }
        return fallback;
    }

    private int readInt(ConfigurationSection section, String primaryPath, String legacyPath, int fallback) {
        if (section.contains(primaryPath)) {
            return section.getInt(primaryPath, fallback);
        }
        if (section.contains(legacyPath)) {
            return section.getInt(legacyPath, fallback);
        }
        return fallback;
    }

    private boolean readBoolean(ConfigurationSection section, String path, boolean fallback) {
        if (section.contains(path)) {
            return section.getBoolean(path, fallback);
        }
        return fallback;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0;
    }
}
