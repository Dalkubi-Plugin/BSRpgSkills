package kr.yongpyo.bsskill.util;

import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.model.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * ActionBar HUD — 전투 모드 중 슬롯 상태 표시
 * 이모지 없음, 숫자/텍스트만
 */
public class CombatHudTask extends BukkitRunnable {

    private final BSSkill plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private volatile boolean enabled;

    public CombatHudTask(BSSkill plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("use-actionbar", true);
    }

    public void start() {
        int interval = plugin.getConfig().getInt("actionbar-interval", 5);
        // Bukkit 엔티티/플레이어 API는 메인 스레드 기준으로 다루는 것이 안전하므로
        // HUD 갱신도 동기 태스크로 실행해 서버 상태와 충돌하지 않도록 합니다.
        runTaskTimer(plugin, 0L, Math.max(1, interval));
    }

    @Override
    public void run() {
        if (!enabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getCombatManager().isInCombatMode(player)) continue;
            var state = plugin.getCombatManager().getState(player);
            String wId = state.getCurrentWeaponId();
            if (wId == null) continue;
            WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(wId);
            if (weapon == null) continue;

            // 형식: [1:OK] [2:3s] [3:OK] [4:-] [5:OK] [6:-]
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
                SkillSlot s = weapon.getSkill(i);
                if (!s.isValid()) { sb.append("<dark_gray>[").append(i).append(":-]</dark_gray> "); continue; }
                double rem = state.getRemainingCooldown(i);
                if (rem > 0) sb.append("<red>[").append(i).append(":").append(String.format("%.0f", rem)).append("s]</red> ");
                else sb.append("<green>[").append(i).append(":OK]</green> ");
            }

            Component bar = mm.deserialize(sb.toString().trim());
            if (player.isOnline()) {
                player.sendActionBar(bar);
            }
        }
    }
}
