package kr.yongpyo.bsskill.model;

import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/**
 * 플레이어 전투 모드 상태 추적
 * <p>무기는 항상 핫바 인덱스 8(키보드 9)에 고정된다.</p>
 */
public class CombatState {

    private final UUID playerUuid;
    private boolean combatMode;
    private String currentWeaponId;

    /** 무기가 위치한 핫바 슬롯 (전투 모드 중 항상 8) */
    private int weaponSlot = -1;

    /** ensureSafeSlot에 의해 스왑되었는지 */
    private boolean swapped;
    /** 스왑 전 원래 슬롯 */
    private int originalSlot = -1;

    /** 슬롯(1~6) → 쿨타임 만료 시각 */
    private final Map<Integer, Long> cooldowns = new HashMap<>();

    /** 패시브 TIMER 스케줄러 (전투 모드 해제 시 전부 취소) */
    private final List<BukkitTask> timerTasks = new ArrayList<>();

    public CombatState(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    // ── 쿨타임 ──

    public void setCooldown(int slot, double seconds) {
        cooldowns.put(slot, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    public boolean isOnCooldown(int slot) {
        Long expiry = cooldowns.get(slot);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public double getRemainingCooldown(int slot) {
        Long expiry = cooldowns.get(slot);
        if (expiry == null) return 0;
        long rem = expiry - System.currentTimeMillis();
        return rem > 0 ? rem / 1000.0 : 0;
    }

    public void clearAllCooldowns() { cooldowns.clear(); }

    // ── 패시브 타이머 ──

    public void addTimerTask(BukkitTask task) { timerTasks.add(task); }

    public void cancelAllTimers() {
        for (BukkitTask t : timerTasks) {
            if (!t.isCancelled()) t.cancel();
        }
        timerTasks.clear();
    }

    // ── 무기 슬롯 ──

    public boolean isHoldingWeapon(int heldSlot) {
        return weaponSlot >= 0 && heldSlot == weaponSlot;
    }

    /** 전투 모드 해제 시 전체 초기화 */
    public void reset() {
        combatMode = false;
        currentWeaponId = null;
        weaponSlot = -1;
        clearAllCooldowns();
        cancelAllTimers();
    }

    // ── Getters & Setters ──

    public UUID getPlayerUuid() { return playerUuid; }
    public boolean isCombatMode() { return combatMode; }
    public void setCombatMode(boolean v) { this.combatMode = v; }
    public String getCurrentWeaponId() { return currentWeaponId; }
    public void setCurrentWeaponId(String v) { this.currentWeaponId = v; }
    public int getWeaponSlot() { return weaponSlot; }
    public void setWeaponSlot(int v) { this.weaponSlot = v; }
    public int getOriginalSlot() { return originalSlot; }
    public void setOriginalSlot(int v) { this.originalSlot = v; }
    public boolean isSwapped() { return swapped; }
    public void setSwapped(boolean v) { this.swapped = v; }
}
