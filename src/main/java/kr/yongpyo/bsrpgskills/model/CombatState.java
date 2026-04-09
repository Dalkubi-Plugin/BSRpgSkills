package kr.yongpyo.bsrpgskills.model;

import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 전투 모드 중 플레이어별 런타임 상태를 관리하는 객체입니다.
 * 쿨타임, 내부 캐스팅 락, 무기 슬롯 정보만 담당하도록 단순화해
 * "어떤 데미지 타입이 어떤 스킬을 발동시키는가"라는 규칙이 코드에 직접 드러나게 합니다.
 */
public class CombatState {

    /**
     * 같은 슬롯이 매우 짧은 시간 안에 중복 발동되는 것을 막아
     * 좌클릭 입력과 일반 공격 이벤트가 동시에 들어와도 스킬이 두 번 실행되지 않도록 합니다.
     */
    private static final long DOUBLE_CAST_GUARD_MILLIS = 50L;

    private boolean combatMode;
    private String currentWeaponId;
    private int weaponSlot = -1;
    private int originalSlot = -1;
    private boolean swapped;

    private final Map<Integer, Long> cooldowns = new HashMap<>();
    private final List<BukkitTask> timerTasks = new ArrayList<>();
    private final Map<Integer, Long> lastCastTimes = new HashMap<>();

    /**
     * 동일 이벤트 체인 안에서 재귀 캐스팅이 일어나지 않도록
     * MythicLib 캐스팅 호출 구간 동안만 잠시 true가 됩니다.
     */
    private volatile boolean internalCasting;

    public void setCooldown(int slot, double seconds) {
        cooldowns.put(slot, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    public boolean isOnCooldown(int slot) {
        Long expiresAt = cooldowns.get(slot);
        return expiresAt != null && System.currentTimeMillis() < expiresAt;
    }

    public double getRemainingCooldown(int slot) {
        Long expiresAt = cooldowns.get(slot);
        if (expiresAt == null) {
            return 0;
        }

        return Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000.0);
    }

    public void clearAllCooldowns() {
        cooldowns.clear();
    }

    /**
     * 현재 남아 있는 쿨타임만 디버그/표시용으로 복사해 반환합니다.
     * 이미 끝난 쿨타임은 제외해 실제로 의미 있는 값만 보이게 합니다.
     */
    public Map<Integer, Double> getCooldownSnapshot() {
        Map<Integer, Double> snapshot = new LinkedHashMap<>();
        for (Integer key : cooldowns.keySet()) {
            double remaining = getRemainingCooldown(key);
            if (remaining > 0) {
                snapshot.put(key, remaining);
            }
        }
        return snapshot;
    }

    public boolean isDoubleCast(int slot) {
        Long lastCastAt = lastCastTimes.get(slot);
        return lastCastAt != null && (System.currentTimeMillis() - lastCastAt) < DOUBLE_CAST_GUARD_MILLIS;
    }

    public void markCast(int slot) {
        lastCastTimes.put(slot, System.currentTimeMillis());
    }

    public boolean canTriggerSkill(int slot) {
        return !internalCasting && !isDoubleCast(slot);
    }

    public boolean isInternalCasting() {
        return internalCasting;
    }

    public void setInternalCasting(boolean internalCasting) {
        this.internalCasting = internalCasting;
    }

    public void addTimerTask(BukkitTask task) {
        timerTasks.add(task);
    }

    public void cancelAllTimers() {
        timerTasks.forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        timerTasks.clear();
    }

    public boolean isHoldingWeapon(int heldSlot) {
        return weaponSlot >= 0 && heldSlot == weaponSlot;
    }

    public void reset() {
        combatMode = false;
        currentWeaponId = null;
        weaponSlot = -1;
        originalSlot = -1;
        swapped = false;
        internalCasting = false;
        clearAllCooldowns();
        cancelAllTimers();
        lastCastTimes.clear();
    }

    public boolean isCombatMode() {
        return combatMode;
    }

    public void setCombatMode(boolean combatMode) {
        this.combatMode = combatMode;
    }

    public String getCurrentWeaponId() {
        return currentWeaponId;
    }

    public void setCurrentWeaponId(String currentWeaponId) {
        this.currentWeaponId = currentWeaponId;
    }

    public int getWeaponSlot() {
        return weaponSlot;
    }

    public void setWeaponSlot(int weaponSlot) {
        this.weaponSlot = weaponSlot;
    }

    public int getOriginalSlot() {
        return originalSlot;
    }

    public void setOriginalSlot(int originalSlot) {
        this.originalSlot = originalSlot;
    }

    public boolean isSwapped() {
        return swapped;
    }

    public void setSwapped(boolean swapped) {
        this.swapped = swapped;
    }
}
