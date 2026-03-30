package kr.yongpyo.bsskill.manager;

import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.model.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 무기 스킬 데이터 매니저 — YAML 파싱, 캐싱, 저장, 검증 로그
 */
public class WeaponSkillManager {

    private final BSSkill plugin;
    private final File weaponsFolder;
    private final Map<String, WeaponSkill> cache = new ConcurrentHashMap<>();

    private static final String[] DEFAULTS = {
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
        for (String n : DEFAULTS) {
            if (!new File(weaponsFolder, n).exists()) plugin.saveResource("weapons/" + n, false);
        }

        File[] files = weaponsFolder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) { plugin.getLogger().info("[WeaponSkillManager] 파일 없음"); return; }

        int loaded = 0;
        for (File f : files) {
            try {
                WeaponSkill w = parseFile(f);
                if (w != null) { cache.put(w.getWeaponId(), w); loaded++; logValidation(w); }
            } catch (Exception e) { plugin.getLogger().log(Level.WARNING, "로드 실패: " + f.getName(), e); }
        }
        plugin.getLogger().info("[WeaponSkillManager] " + loaded + "개 무기 로드 완료");
    }

    private WeaponSkill parseFile(File file) {
        String id = file.getName().replace(".yml", "");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        WeaponSkill w = new WeaponSkill(id);
        w.setMmoType(cfg.getString("mmo-type", "SWORD"));
        w.setDisplayName(cfg.getString("display-name", id));

        // -- 액티브 --
        ConfigurationSection skills = cfg.getConfigurationSection("skills");
        if (skills != null) {
            for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
                ConfigurationSection sec = skills.getConfigurationSection("slot-" + i);
                if (sec == null) continue;
                SkillSlot s = w.getSkill(i);
                s.setMythicId(sec.getString("mythic-id", ""));
                s.setDisplayName(sec.getString("display-name", "<gray>비어있음</gray>"));
                s.setCooldown(sec.getDouble("cooldown", 0));
                s.setDamage(sec.getDouble("damage", 0));
                s.setIcon(sec.getString("icon", "BARRIER"));
                s.setCustomModelData(sec.getInt("custom-model-data", 0));
                s.setDescription(sec.getString("description", ""));
                s.setEnabled(sec.getBoolean("enabled", false));
                ConfigurationSection extra = sec.getConfigurationSection("extra");
                if (extra != null) {
                    s.clearExtras();
                    for (String k : extra.getKeys(false)) s.putExtra(k, extra.get(k));
                }
            }
        }

        // -- 패시브 --
        ConfigurationSection passives = cfg.getConfigurationSection("passives");
        if (passives != null) {
            w.clearPassives();
            int idx = 0;
            for (String key : passives.getKeys(false)) {
                ConfigurationSection sec = passives.getConfigurationSection(key);
                if (sec == null) continue;
                idx++;
                PassiveSlot p = new PassiveSlot(idx);
                p.setType(sec.getString("type", ""));
                p.setDisplayName(sec.getString("display-name", "<gray>비어있음</gray>"));
                p.setTimer(sec.getDouble("timer", 10.0));
                p.setCooldown(sec.getDouble("cooldown", 0));
                p.setIcon(sec.getString("icon", "ENDER_EYE"));
                p.setCustomModelData(sec.getInt("custom-model-data", 0));
                p.setDescription(sec.getString("description", ""));
                p.setEnabled(sec.getBoolean("enabled", true));
                p.clearModifiers();
                for (String mk : sec.getKeys(false)) {
                    if (!PassiveSlot.isStandardKey(mk)) p.putModifier(mk, toDouble(sec.get(mk)));
                }
                if (p.getType().isBlank()) {
                    plugin.getLogger().warning("[" + id + "] " + key + ": type 누락");
                    continue;
                }
                if (p.getTimer() < 0.1) {
                    plugin.getLogger().warning("[" + id + "] " + key + ": timer < 0.1 -> 보정");
                }
                w.addPassive(p);
            }
        }
        return w;
    }

    /** 콘솔 검증 로그 */
    private void logValidation(WeaponSkill w) {
        plugin.getLogger().info("--- " + w.getWeaponId() + " (" + w.getMmoType() + ") ---");
        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            SkillSlot s = w.getSkill(i);
            if (!s.getMythicId().isBlank()) {
                plugin.getLogger().info("  [슬롯" + i + "] " + s.getMythicId()
                        + " | DMG:" + s.getDamage() + " | CD:" + s.getCooldown() + "s"
                        + " | CMD:" + s.getCustomModelData()
                        + (s.isEnabled() ? "" : " | DISABLED")
                        + (!s.getExtraParams().isEmpty() ? " | extra:" + s.getExtraParams().keySet() : ""));
            }
        }
        for (PassiveSlot p : w.getPassives()) {
            plugin.getLogger().info("  [패시브] " + p.getType()
                    + " | T:" + p.getTimer() + "s | CD:" + p.getCooldown() + "s"
                    + " | CMD:" + p.getCustomModelData()
                    + (p.isEnabled() ? "" : " | DISABLED")
                    + (!p.getModifiers().isEmpty() ? " | mods:" + p.getModifiers() : ""));
        }
    }

    // ===================================================================
    // 저장
    // ===================================================================

    public void save(WeaponSkill w) {
        File file = new File(weaponsFolder, w.getWeaponId() + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("mmo-type", w.getMmoType());
        cfg.set("display-name", w.getDisplayName());

        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            SkillSlot s = w.getSkill(i);
            String p = "skills.slot-" + i;
            cfg.set(p + ".mythic-id", s.getMythicId());
            cfg.set(p + ".display-name", s.getDisplayName());
            cfg.set(p + ".cooldown", s.getCooldown());
            cfg.set(p + ".damage", s.getDamage());
            cfg.set(p + ".icon", s.getIcon());
            cfg.set(p + ".custom-model-data", s.getCustomModelData());
            cfg.set(p + ".description", s.getDescription());
            cfg.set(p + ".enabled", s.isEnabled());
            if (!s.getExtraParams().isEmpty())
                for (var e : s.getExtraParams().entrySet()) cfg.set(p + ".extra." + e.getKey(), e.getValue());
        }

        int pidx = 0;
        for (PassiveSlot ps : w.getPassives()) {
            pidx++;
            String p = "passives.passive" + pidx;
            cfg.set(p + ".type", ps.getType());
            cfg.set(p + ".display-name", ps.getDisplayName());
            cfg.set(p + ".timer", ps.getTimer());
            cfg.set(p + ".cooldown", ps.getCooldown());
            cfg.set(p + ".icon", ps.getIcon());
            cfg.set(p + ".custom-model-data", ps.getCustomModelData());
            cfg.set(p + ".description", ps.getDescription());
            cfg.set(p + ".enabled", ps.isEnabled());
            for (var e : ps.getModifiers().entrySet()) cfg.set(p + "." + e.getKey(), e.getValue());
        }

        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "저장 실패: " + file.getName(), e); }
    }

    public void saveAll() { cache.values().forEach(this::save); plugin.getLogger().info("[WeaponSkillManager] 전체 저장 완료"); }

    // -- 조회 --
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
