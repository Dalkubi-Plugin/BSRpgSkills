package kr.yongpyo.bsrpgskills;

import kr.yongpyo.bsrpgskills.command.BSRpgSkillsCommand;
import kr.yongpyo.bsrpgskills.gui.GUIManager;
import kr.yongpyo.bsrpgskills.hook.WorldGuardHook;
import kr.yongpyo.bsrpgskills.listener.CombatListener;
import kr.yongpyo.bsrpgskills.listener.GUIListener;
import kr.yongpyo.bsrpgskills.manager.CombatManager;
import kr.yongpyo.bsrpgskills.manager.PlayerSkillManager;
import kr.yongpyo.bsrpgskills.manager.ResetItemManager;
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

    private WeaponSkillManager weaponSkillManager;
    private PlayerSkillManager playerSkillManager;
    private CombatManager combatManager;
    private GUIManager guiManager;
    private ResetItemManager resetItemManager;
    private CombatHudTask hudTask;
    private boolean debugMode;
    private boolean worldGuardEnabled;

    @Override
    public void onLoad() {
        // WorldGuard 커스텀 플래그는 반드시 onLoad에서 등록해야 합니다.
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                WorldGuardHook.registerFlag();
                worldGuardEnabled = true;
                getLogger().info("WorldGuard 연동 활성화: bsrpgskills-combat 플래그 등록 완료");
            } catch (Throwable throwable) {
                getLogger().warning("WorldGuard 플래그 등록 실패: " + throwable.getMessage());
            }
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadRuntimeSettings();

        weaponSkillManager = new WeaponSkillManager(this);
        playerSkillManager = new PlayerSkillManager(this);
        combatManager = new CombatManager(this);
        guiManager = new GUIManager(this);
        resetItemManager = new ResetItemManager(this);

        weaponSkillManager.loadAll();
        combatManager.warmUpHandlerCache();

        for (var online : Bukkit.getOnlinePlayers()) {
            playerSkillManager.load(online.getUniqueId());
        }

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
        if (playerSkillManager != null) {
            playerSkillManager.saveAll();
        }
        stopHudTask();
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
        if (hudTask == null) {
            return;
        }

        try {
            hudTask.cancel();
        } catch (IllegalStateException ignored) {
            // 스케줄되지 않았거나 이미 취소된 경우를 안전하게 무시합니다.
        }
        hudTask = null;
    }

    /**
     * Reload 진입점.
     *
     * 안전 규칙(필수 준수):
     * 1. PlayerSkillManager는 절대 재생성하지 않는다 (캐시 보존).
     * 2. PlayerSkillManager 내부 캐시는 절대 clear()하지 않는다.
     * 3. 무기 yml만 다시 읽으며 플레이어 진행도 캐시는 그대로 유지된다.
     *
     * 처리 순서:
     *  - 현재 캐시된 플레이어 데이터를 먼저 saveAll()로 디스크에 flush (미저장 데이터 보호).
     *  - 무기 reload는 handleReload에서 미리 수행되어 있다.
     *  - 새로 읽은 무기 정의 기준으로 in-memory 레벨 값을 클램프하고 변경분만 다시 디스크에 기록한다.
     */
    public void restartHudTask() {
        stopHudTask();
        reloadRuntimeSettings();
        if (resetItemManager != null) {
            resetItemManager.reload();
        }
        combatManager.reloadMessages();
        combatManager.warmUpHandlerCache();
        combatManager.refreshPassiveTimers();
        if (playerSkillManager != null) {
            playerSkillManager.saveAll();
            playerSkillManager.clampToWeaponDefinitions();
        }
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

    public WeaponSkillManager getWeaponSkillManager() {
        return weaponSkillManager;
    }

    public PlayerSkillManager getPlayerSkillManager() {
        return playerSkillManager;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public GUIManager getGUIManager() {
        return guiManager;
    }

    public ResetItemManager getResetItemManager() {
        return resetItemManager;
    }

    public boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }
}
