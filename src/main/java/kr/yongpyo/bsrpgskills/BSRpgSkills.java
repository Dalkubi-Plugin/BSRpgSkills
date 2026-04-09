package kr.yongpyo.bsrpgskills;

import kr.yongpyo.bsrpgskills.command.BSRpgSkillsCommand;
import kr.yongpyo.bsrpgskills.gui.GUIManager;
import kr.yongpyo.bsrpgskills.listener.CombatListener;
import kr.yongpyo.bsrpgskills.listener.GUIListener;
import kr.yongpyo.bsrpgskills.manager.CombatManager;
import kr.yongpyo.bsrpgskills.manager.WeaponSkillManager;
import kr.yongpyo.bsrpgskills.placeholder.BSRpgSkillsExpansion;
import kr.yongpyo.bsrpgskills.util.CombatHudTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * BSRpgSkills 메인 플러그인 클래스입니다.
 * 설정 로드, 매니저 초기화, 이벤트 등록, 디버그 모드 제어를 한곳에서 관리합니다.
 */
public class BSRpgSkills extends JavaPlugin {

    private static BSRpgSkills instance;

    private WeaponSkillManager weaponSkillManager;
    private CombatManager combatManager;
    private GUIManager guiManager;
    private CombatHudTask hudTask;
    private boolean debugMode;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadRuntimeSettings();

        weaponSkillManager = new WeaponSkillManager(this);
        combatManager = new CombatManager(this);
        guiManager = new GUIManager(this);

        weaponSkillManager.loadAll();
        combatManager.warmUpHandlerCache();

        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);

        new BSRpgSkillsCommand(this).registerAll();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BSRpgSkillsExpansion(this).register();
            logDebug("PlaceholderAPI 연동을 활성화했습니다.");
        }

        startHudTask();
        getLogger().info("BSRpgSkills 활성화 완료 (무기 " + weaponSkillManager.size() + "개, 디버그 "
                + (debugMode ? "ON" : "OFF") + ")");
    }

    @Override
    public void onDisable() {
        if (weaponSkillManager != null) {
            weaponSkillManager.saveAll();
        }
        stopHudTask();
        instance = null;
    }

    /**
     * config.yml 변경 후 즉시 반영되어야 하는 런타임 옵션을 다시 읽어옵니다.
     */
    public void reloadRuntimeSettings() {
        debugMode = getConfig().getBoolean("debug.enabled", false);
    }

    private void startHudTask() {
        hudTask = new CombatHudTask(this);
        hudTask.start();
    }

    private void stopHudTask() {
        if (hudTask != null && !hudTask.isCancelled()) {
            hudTask.cancel();
            hudTask = null;
        }
    }

    public void restartHudTask() {
        stopHudTask();
        reloadRuntimeSettings();
        combatManager.reloadMessages();
        combatManager.warmUpHandlerCache();
        startHudTask();
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void logDebug(String message) {
        if (debugMode) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public void logDebug(String message, Throwable throwable) {
        if (debugMode) {
            getLogger().log(Level.INFO, "[DEBUG] " + message, throwable);
        }
    }

    public static BSRpgSkills getInstance() {
        return instance;
    }

    public WeaponSkillManager getWeaponSkillManager() {
        return weaponSkillManager;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public GUIManager getGUIManager() {
        return guiManager;
    }
}
