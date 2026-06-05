package kr.yongpyo.bsrpgskills.manager;

import kr.yongpyo.bsrpgskills.BSRpgSkills;
import kr.yongpyo.bsrpgskills.model.PlayerSkillData;
import kr.yongpyo.bsrpgskills.model.WeaponSkill;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 플레이어별 스킬 레벨/포인트 데이터를 디스크에 보관하는 매니저입니다.
 * 저장 경로는 plugins/BSRpgSkills/playerdata/&lt;uuid&gt;.yml 입니다.
 *
 * <p><b>Reload 안전 규칙</b>
 * <ul>
 *   <li>이 클래스의 인스턴스는 plugin onEnable에서 단 1회 생성된다. reload 시 절대 재생성하지 않는다.</li>
 *   <li>{@code cache.clear()} 호출은 어디에서도 하지 않는다. 캐시는 플레이어 join/quit 시점 또는
 *       onDisable에만 변동된다.</li>
 *   <li>reload 시 {@link #clampToWeaponDefinitions()}는 기존 엔트리를 in-place로 수정할 뿐,
 *       엔트리 자체를 제거하지 않는다.</li>
 * </ul>
 */
public class PlayerSkillManager {

    private final BSRpgSkills plugin;
    private final File dataFolder;
    private final Map<UUID, PlayerSkillData> cache = new ConcurrentHashMap<>();

    public PlayerSkillManager(BSRpgSkills plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("플레이어 데이터 폴더 생성 실패: " + dataFolder.getAbsolutePath());
        }
    }

    public PlayerSkillData get(Player player) {
        return get(player.getUniqueId());
    }

    public PlayerSkillData get(UUID playerId) {
        return cache.computeIfAbsent(playerId, this::loadFromDisk);
    }

    public void load(UUID playerId) {
        cache.computeIfAbsent(playerId, this::loadFromDisk);
    }

    public void unload(UUID playerId) {
        PlayerSkillData data = cache.remove(playerId);
        if (data != null) {
            writeToDisk(data);
        }
    }

    public void save(UUID playerId) {
        PlayerSkillData data = cache.get(playerId);
        if (data != null) {
            writeToDisk(data);
        }
    }

    public void saveAll() {
        for (PlayerSkillData data : cache.values()) {
            writeToDisk(data);
        }
    }

    /**
     * 무기 yml이 다시 로드된 직후, 캐시된 모든 플레이어 데이터의 레벨이 새 max-level을
     * 초과하지 않도록 클램프합니다. 초과분은 포인트로 환불됩니다.
     */
    public void clampToWeaponDefinitions() {
        var weapons = plugin.getWeaponSkillManager().getAllWeapons();
        for (PlayerSkillData data : cache.values()) {
            boolean changed = false;
            for (var weapon : weapons) {
                for (int slot = 1; slot <= WeaponSkill.MAX_SLOTS; slot++) {
                    var skill = weapon.getSkill(slot);
                    if (skill == null) {
                        continue;
                    }
                    if (data.clampLevel(weapon.getWeaponId(), slot, skill.getMaxLevel()) > 0) {
                        changed = true;
                    }
                }
            }
            if (changed) {
                writeToDisk(data);
            }
        }
    }

    private PlayerSkillData loadFromDisk(UUID playerId) {
        PlayerSkillData data = new PlayerSkillData(playerId);
        File file = new File(dataFolder, playerId + ".yml");
        if (!file.exists()) {
            return data;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        data.setPoints(config.getInt("points", 0));

        ConfigurationSection skills = config.getConfigurationSection("skills");
        if (skills == null) {
            return data;
        }

        int legacyPoints = 0;
        for (String weaponId : skills.getKeys(false)) {
            ConfigurationSection weaponSection = skills.getConfigurationSection(weaponId);
            if (weaponSection == null) {
                continue;
            }
            WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(weaponId);
            for (String slotKey : weaponSection.getKeys(false)) {
                int slot = parseSlot(slotKey);
                if (slot < 1) {
                    continue;
                }
                ConfigurationSection slotSection = weaponSection.getConfigurationSection(slotKey);
                if (slotSection == null) {
                    continue;
                }
                int level = slotSection.getInt("level", 1);
                int points = slotSection.getInt("points", 0);
                if (level > 1) {
                    data.setLevel(weaponId, slot, level);
                }
                if (points > 0) {
                    legacyPoints += points;
                }
                if (weapon != null) {
                    var skill = weapon.getSkill(slot);
                    if (skill != null) {
                        data.clampLevel(weaponId, slot, skill.getMaxLevel());
                    }
                }
            }
        }
        if (legacyPoints > 0) {
            // 구버전 데이터의 weapon/slot별 포인트는 새 공용 포인트로 합산해 마이그레이션합니다.
            data.addPoints(legacyPoints);
        }
        return data;
    }

    private void writeToDisk(PlayerSkillData data) {
        File file = new File(dataFolder, data.getPlayerId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, Integer> entry : data.getAllLevels().entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            config.set("skills." + parts[0] + ".slot-" + parts[1] + ".level", entry.getValue());
        }
        config.set("points", data.getPoints());

        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "플레이어 스킬 데이터 저장 실패: " + file.getName(), ex);
        }
    }

    private int parseSlot(String slotKey) {
        if (slotKey == null) {
            return -1;
        }
        String trimmed = slotKey.startsWith("slot-") ? slotKey.substring("slot-".length()) : slotKey;
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
