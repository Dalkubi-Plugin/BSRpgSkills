package kr.yongpyo.bsskill;

import kr.yongpyo.bsskill.command.BSSkillCommand;
import kr.yongpyo.bsskill.gui.GUIManager;
import kr.yongpyo.bsskill.listener.CombatListener;
import kr.yongpyo.bsskill.listener.GUIListener;
import kr.yongpyo.bsskill.manager.CombatManager;
import kr.yongpyo.bsskill.manager.WeaponSkillManager;
import kr.yongpyo.bsskill.placeholder.BSSkillExpansion;
import kr.yongpyo.bsskill.util.CombatHudTask;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BSSkill v4.0.0
 * MMOItems 무기 기반 6슬롯 스킬 + 패시브 시스템
 * MythicLib API 직접 시전 (registerModifier)
 * CommandAPI 11.x 서버 플러그인 의존
 */
public class BSSkill extends JavaPlugin {

    private static BSSkill instance;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private WeaponSkillManager weaponSkillManager;
    private CombatManager combatManager;
    private GUIManager guiManager;
    private CombatHudTask hudTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        weaponSkillManager = new WeaponSkillManager(this);
        combatManager = new CombatManager(this);
        guiManager = new GUIManager(this);

        weaponSkillManager.loadAll();
        combatManager.warmUpHandlerCache();

        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);

        new BSSkillCommand(this).registerAll();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BSSkillExpansion(this).register();
            getLogger().info("[BSSkill] PlaceholderAPI 연동 완료");
        }

        startHudTask();

        Bukkit.getConsoleSender().sendMessage(mm.deserialize(
                "<gradient:red:gold>BSSkill v" + getPluginMeta().getVersion()
                        + " 활성화</gradient> <gray>(" + weaponSkillManager.size() + "개 무기)</gray>"));
    }

    @Override
    public void onDisable() {
        if (weaponSkillManager != null) weaponSkillManager.saveAll();
        stopHudTask();
        instance = null;
    }

    private void startHudTask() { hudTask = new CombatHudTask(this); hudTask.start(); }
    private void stopHudTask() { if (hudTask != null && !hudTask.isCancelled()) { hudTask.cancel(); hudTask = null; } }
    public void restartHudTask() { stopHudTask(); combatManager.reloadMessages(); combatManager.warmUpHandlerCache(); startHudTask(); }

    public static BSSkill getInstance() { return instance; }
    public WeaponSkillManager getWeaponSkillManager() { return weaponSkillManager; }
    public CombatManager getCombatManager() { return combatManager; }
    public GUIManager getGUIManager() { return guiManager; }
}
