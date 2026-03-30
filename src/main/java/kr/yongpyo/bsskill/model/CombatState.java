package kr.yongpyo.bsskill.model;

import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/**
 * 플레이어 전투 모드 상태 추적
 * 슬롯 이동 제한 없음 — 무기 위치만 추적
 */
public class CombatState {

    private final UUID playerUuid;
    private boolean combatMode;
    private String currentWeaponId;
    private int weaponSlot = -1;

    private final Map<Integer, Long> cooldowns = new HashMap<>();
    private final List<BukkitTask> timerTasks = new ArrayList<>();

    public CombatState(UUID playerUuid) { this.playerUuid = playerUuid; }

    // -- 쿨타임 --
    public void setCooldown(int slot, double seconds) {
        cooldowns.put(slot, System.currentTimeMillis() + (long) (seconds * 1000));
    }
    public boolean isOnCooldown(int slot) {
        Long e = cooldowns.get(slot);
        return e != null && System.currentTimeMillis() < e;
    }
    public double getRemainingCooldown(int slot) {
        Long e = cooldowns.get(slot);
        if (e == null) return 0;
        long r = e - System.currentTimeMillis();
        return r > 0 ? r / 1000.0 : 0;
    }
    public void clearAllCooldowns() { cooldowns.clear(); }

    // -- 타이머 --
    public void addTimerTask(BukkitTask t) { timerTasks.add(t); }
    public void cancelAllTimers() {
        for (BukkitTask t : timerTasks) { if (!t.isCancelled()) t.cancel(); }
        timerTasks.clear();
    }

    // -- 무기 슬롯 --
    public boolean isHoldingWeapon(int heldSlot) { return weaponSlot >= 0 && heldSlot == weaponSlot; }

    public void reset() {
        combatMode = false;
        currentWeaponId = null;
        weaponSlot = -1;
        clearAllCooldowns();
        cancelAllTimers();
    }

    // -- Getters & Setters --
    public UUID getPlayerUuid() { return playerUuid; }
    public boolean isCombatMode() { return combatMode; }
    public void setCombatMode(boolean v) { combatMode = v; }
    public String getCurrentWeaponId() { return currentWeaponId; }
    public void setCurrentWeaponId(String v) { currentWeaponId = v; }
    public int getWeaponSlot() { return weaponSlot; }
    public void setWeaponSlot(int v) { weaponSlot = v; }
}
