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

    /**
     * 무기별로 분리된 쿨타임 저장소입니다.
     * 외부 맵: weaponId → 내부 맵: 슬롯키(슬롯 1~6 또는 100+passiveIndex) → 만료 시각(ms)
     *
     * 무기 간 쿨타임 간섭을 구조적으로 차단하고,
     * 전투 모드 토글로 인해 쿨타임이 초기화되지 않도록 플레이어 세션 수명에 맞춰 유지합니다.
     */
    private final Map<String, Map<Integer, Long>> cooldownsByWeapon = new HashMap<>();
    private final List<BukkitTask> timerTasks = new ArrayList<>();
    private final Map<Integer, Long> lastCastTimes = new HashMap<>();

    /**
     * 동일 이벤트 체인 안에서 재귀 캐스팅이 일어나지 않도록
     * MythicLib 캐스팅 호출 구간 동안만 잠시 true가 됩니다.
     */
    private volatile boolean internalCasting;

    public void setCooldown(String weaponId, int slot, double seconds) {
        if (weaponId == null) {
            return;
        }
        cooldownsByWeapon
                .computeIfAbsent(weaponId, k -> new HashMap<>())
                .put(slot, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    public boolean isOnCooldown(String weaponId, int slot) {
        if (weaponId == null) {
            return false;
        }
        Map<Integer, Long> cooldowns = cooldownsByWeapon.get(weaponId);
        if (cooldowns == null) {
            return false;
        }
        Long expiresAt = cooldowns.get(slot);
        return expiresAt != null && System.currentTimeMillis() < expiresAt;
    }

    public double getRemainingCooldown(String weaponId, int slot) {
        if (weaponId == null) {
            return 0;
        }
        Map<Integer, Long> cooldowns = cooldownsByWeapon.get(weaponId);
        if (cooldowns == null) {
            return 0;
        }
        Long expiresAt = cooldowns.get(slot);
        if (expiresAt == null) {
            return 0;
        }

        return Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000.0);
    }

    /**
     * 모든 무기의 쿨타임을 강제로 비웁니다.
     * 관리자 명령 등 명시적 초기화 경로에서만 호출됩니다.
     * 전투 모드 토글/사망에서는 호출하지 않습니다.
     */
    public void clearAllCooldowns() {
        cooldownsByWeapon.clear();
    }

    /**
     * 현재 남아 있는 쿨타임만 디버그/표시용으로 복사해 반환합니다.
     * 이미 끝난 쿨타임은 제외해 실제로 의미 있는 값만 보이게 합니다.
     */
    public Map<Integer, Double> getCooldownSnapshot(String weaponId) {
        Map<Integer, Double> snapshot = new LinkedHashMap<>();
        if (weaponId == null) {
            return snapshot;
        }
        Map<Integer, Long> cooldowns = cooldownsByWeapon.get(weaponId);
        if (cooldowns == null) {
            return snapshot;
        }
        for (Integer key : cooldowns.keySet()) {
            double remaining = getRemainingCooldown(weaponId, key);
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

    /**
     * 전투 모드 관련 런타임 상태를 초기화합니다.
     * 쿨타임은 플레이어 세션 전체에 걸쳐 유지되어야 하므로
     * 여기서는 클리어하지 않습니다. 명시적으로 비우려면 {@link #clearAllCooldowns()}를 호출하세요.
     */
    public void reset() {
        combatMode = false;
        currentWeaponId = null;
        weaponSlot = -1;
        originalSlot = -1;
        swapped = false;
        internalCasting = false;
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
