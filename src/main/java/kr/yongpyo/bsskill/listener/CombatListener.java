package kr.yongpyo.bsskill.listener;

import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.manager.CombatManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

/**
 * 전투 모드 리스너
 *
 * 슬롯 이동 제한 없음 — 마우스 휠, 숫자키 전부 자유
 * 타이틀 출력 없음
 *
 * 스킬 발동:
 *   좌/우클릭 (무기 들고 있을 때) → 슬롯 1/2
 *   핫바 1~4키 (무기 들고 있을 때) → 슬롯 3~6 (이벤트 취소하여 무기 유지)
 *   그 외 모든 슬롯 변경 → 자유 허용
 */
public class CombatListener implements Listener {

    private final BSSkill plugin;
    private final CombatManager combat;

    public CombatListener(BSSkill plugin) {
        this.plugin = plugin;
        this.combat = plugin.getCombatManager();
    }

    // -- F키: 전투 모드 토글 / 무기 복귀 --
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (combat.isInCombatMode(player) || combat.detectWeaponId(player) != null) {
            if (combat.handleFKey(player)) event.setCancelled(true);
        }
    }

    // -- 좌/우클릭: 무기 들고 있을 때만 스킬 --
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!combat.isInCombatMode(player)) return;
        if (!combat.isHoldingWeapon(player)) return;

        Action action = event.getAction();
        boolean left = (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
        boolean right = (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
        if (!left && !right) return;

        event.setCancelled(true);
        combat.castSkill(player, left ? 1 : 2);
    }

    // -- 핫바 키: 1~4(무기 상태)만 스킬 시전 + 이벤트 취소, 나머지 전부 자유 --
    @EventHandler(priority = EventPriority.HIGH)
    public void onHotbarChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!combat.isInCombatMode(player)) return;
        if (!combat.isHoldingWeapon(player)) return; // 이미 다른 슬롯이면 자유 이동

        int newSlot = event.getNewSlot();
        // 핫바 0~3(키보드 1~4) → 스킬 3~6, 무기 슬롯 유지
        if (newSlot >= 0 && newSlot <= 3) {
            event.setCancelled(true);
            combat.castSkill(player, newSlot + 3);
        }
        // 그 외 (5~9키, 휠 등) → 자유 이동, 이벤트 취소 안 함
    }

    // -- Q키: 무기 슬롯에서만 드롭 차단 --
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (combat.isInCombatMode(event.getPlayer()) && combat.isHoldingWeapon(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // -- 사망: 전투 모드 해제 --
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (combat.isInCombatMode(event.getEntity())) combat.forceDisable(event.getEntity());
    }

    // -- 퇴장: 정리 --
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combat.forceDisable(event.getPlayer());
        combat.removeState(event.getPlayer().getUniqueId());
    }
}
