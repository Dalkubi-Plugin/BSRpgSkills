package kr.yongpyo.bsrpgskills.model;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 플레이어별 스킬 레벨/포인트 데이터입니다.
 * 레벨 Key는 "weaponId:slot" 형식이며, 포인트는 모든 무기가 공유하는 플레이어 공용 값입니다.
 */
public class PlayerSkillData {

    private final UUID playerId;
    private final Map<String, Integer> levels = new ConcurrentHashMap<>();
    private int points;

    public PlayerSkillData(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getLevel(String weaponId, int slot) {
        return levels.getOrDefault(key(weaponId, slot), 1);
    }

    public int getPoints(String weaponId, int slot) {
        return getPoints();
    }

    public int getPoints() {
        return points;
    }

    public void setLevel(String weaponId, int slot, int level) {
        levels.put(key(weaponId, slot), Math.max(1, level));
    }

    public void setPoints(String weaponId, int slot, int amount) {
        setPoints(amount);
    }

    public void setPoints(int amount) {
        points = Math.max(0, amount);
    }

    public void addPoints(String weaponId, int slot, int amount) {
        addPoints(amount);
    }

    public void addPoints(int amount) {
        if (amount == 0) {
            return;
        }
        points = Math.max(0, points + amount);
    }

    /**
     * 포인트를 차감합니다. 보유 포인트가 부족하면 false를 반환하고 변경하지 않습니다.
     */
    public boolean spendPoint(String weaponId, int slot, int amount) {
        return spendPoint(amount);
    }

    /**
     * 공용 스킬 포인트를 차감합니다. 보유 포인트가 부족하면 false를 반환하고 변경하지 않습니다.
     */
    public boolean spendPoint(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (points < amount) {
            return false;
        }
        points -= amount;
        return true;
    }

    public Map<String, Integer> getAllLevels() {
        return Collections.unmodifiableMap(levels);
    }

    public Map<String, Integer> getAllPoints() {
        return Collections.singletonMap("global", points);
    }

    /**
     * 특정 스킬의 레벨을 1로 되돌리고 사용한 포인트를 환불해 남은 포인트에 더합니다.
     *
     * @return 환불된 포인트 수
     */
    public int reset(String weaponId, int slot) {
        String key = key(weaponId, slot);
        int level = levels.getOrDefault(key, 1);
        int refund = Math.max(0, level - 1);
        if (refund > 0) {
            addPoints(refund);
        }
        levels.remove(key);
        return refund;
    }

    /**
     * 특정 무기의 모든 슬롯 레벨을 1로 되돌리고 환불합니다.
     */
    public int resetWeapon(String weaponId) {
        int total = 0;
        for (int slot = 1; slot <= 6; slot++) {
            total += reset(weaponId, slot);
        }
        return total;
    }

    /**
     * 모든 스킬 레벨을 초기화하고 모든 포인트를 환불합니다.
     */
    public int resetAll() {
        int total = 0;
        for (String key : new java.util.ArrayList<>(levels.keySet())) {
            String[] parts = key.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                total += reset(parts[0], Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {
            }
        }
        return total;
    }

    /**
     * 저장된 레벨이 max보다 크면 max로 잘라 환불합니다. 무기 yml에서 max-level이
     * 줄어든 경우 호출됩니다.
     */
    public int clampLevel(String weaponId, int slot, int max) {
        String key = key(weaponId, slot);
        int current = levels.getOrDefault(key, 1);
        if (current <= max) {
            return 0;
        }
        int refund = current - Math.max(1, max);
        levels.put(key, Math.max(1, max));
        if (refund > 0) {
            addPoints(refund);
        }
        return refund;
    }

    public static String key(String weaponId, int slot) {
        return weaponId + ":" + slot;
    }
}
