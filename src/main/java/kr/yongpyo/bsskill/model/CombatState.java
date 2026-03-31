package kr.yongpyo.bsskill.model;

import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/**
 * 플레이어 전투 모드 상태 추적
 * 전투 모드 진입 시 무기를 핫바 8(키보드 9)으로 이동, 해제 시 원래 위치로 복구
 */
public class CombatState {

    private final UUID playerUuid;
    private boolean combatMode;
    private String currentWeaponId;

    /** 무기가 위치한 핫바 슬롯 (전투 모드 중 항상 8) */
    private int weaponSlot = -1;

    /** 전투 진입 전 무기가 있던 핫바 슬롯 (복구용) */
    private int originalSlot = -1;

    /** 슬롯 스왑이 실행되었는지 여부 */
    private boolean swapped = false;

    private final Map<Integer, Long> cooldowns = new HashMap<>();
    private final List<BukkitTask> timerTasks = new ArrayList<>();

    /**
     * 슬롯별 마지막 시전 시각 (동일 틱 중복 시전 방지)
     * 좌클릭 시 PlayerInteractEvent + EntityDamageByEntityEvent가 같은 틱에 발생할 수 있음
     */
    private final Map<Integer, Long> lastCastTime = new HashMap<>();

    /** 동일 틱 중복 시전 방지 간격 (ms) — 1틱 = 50ms */
    private static final long DOUBLE_CAST_GUARD = 50L;

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

    /**
     * 동일 틱 중복 시전인지 확인 (true면 중복 → 스킵 필요)
     */
    public boolean isDoubleCast(int slot) {
        Long last = lastCastTime.get(slot);
        return last != null && (System.currentTimeMillis() - last) < DOUBLE_CAST_GUARD;
    }

    /** 시전 시각 기록 */
    public void markCast(int slot) {
        lastCastTime.put(slot, System.currentTimeMillis());
    }

    // -- 타이머 --
    public void addTimerTask(BukkitTask t) { timerTasks.add(t); }
    public void cancelAllTimers() {
        for (BukkitTask t : timerTasks) { if (!t.isCancelled()) t.cancel(); }
        timerTasks.clear();
    }

    // -- 무기 슬롯 --
    public boolean isHoldingWeapon(int heldSlot) { return weaponSlot >= 0 && heldSlot == weaponSlot; }

    /** 전투 모드 해제 시 전체 초기화 (인벤토리 복구는 CombatManager에서 처리) */
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
    public int getOriginalSlot() { return originalSlot; }
    public void setOriginalSlot(int v) { originalSlot = v; }
    public boolean isSwapped() { return swapped; }
    public void setSwapped(boolean v) { swapped = v; }
}
