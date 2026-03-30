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
 * 전투 모드 핵심 리스너 — 핫바 9번 무기 + 마우스 휠 방지
 *
 * <h3>전투 모드 중 핫바 규칙</h3>
 * <pre>
 *   ┌───────────────┬────────────────────────┬────────────────────┐
 *   │ newSlot       │ 무기(8) 들고 있을 때    │ 아이템(4~7) 들 때   │
 *   ├───────────────┼────────────────────────┼────────────────────┤
 *   │ 0~3 (키 1~4)  │ 차단 + 스킬 3~6 시전   │ 차단 (보호)         │
 *   │ 4~7 (키 5~8)  │ 자유 전환              │ 자유 전환           │
 *   │ 8   (키 9)    │ — (이미 무기)          │ 차단 (F키로 복귀)   │
 *   └───────────────┴────────────────────────┴────────────────────┘
 * </pre>
 *
 * <h3>마우스 휠 방지 원리</h3>
 * <p>
 * 핫바 0~3과 8은 전투 모드 중 항상 차단되므로, 마우스 휠이 이 영역을
 * 지나가더라도 이벤트가 취소되어 슬롯 변경이 일어나지 않는다.
 * 자유 영역(4~7)에서만 휠/키 이동이 허용된다.
 * </p>
 */
public class CombatListener implements Listener {

    private final BSSkill plugin;
    private final CombatManager combat;

    public CombatListener(BSSkill plugin) {
        this.plugin = plugin;
        this.combat = plugin.getCombatManager();
    }

    // ===================================================================
    // F키 → 전투 모드 토글 / 무기 복귀
    // ===================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        // 전투 모드 중이거나, 등록된 무기를 들고 있으면 F키 가로채기
        if (combat.isInCombatMode(player) || combat.detectWeaponId(player) != null) {
            boolean handled = combat.handleFKey(player);
            if (handled) event.setCancelled(true);
        }
    }

    // ===================================================================
    // 좌/우클릭 → 슬롯 1/2 (무기 슬롯일 때만)
    // ===================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!combat.isInCombatMode(player)) return;
        if (!combat.isHoldingWeaponSlot(player)) return; // 아이템 슬롯이면 패스스루

        Action action = event.getAction();
        boolean isLeft = (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
        boolean isRight = (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
        if (!isLeft && !isRight) return;

        event.setCancelled(true);
        combat.castSkill(player, isLeft ? 1 : 2);
    }

    // ===================================================================
    // 핫바 키 + 마우스 휠 제어
    // ===================================================================

    /**
     * 전투 모드 중 핫바 변경 이벤트 처리.
     * <ul>
     *   <li>newSlot 0~3: 항상 차단. 무기 들고 있으면 스킬 시전.</li>
     *   <li>newSlot 4~7: 자유 허용.</li>
     *   <li>newSlot 8: 차단 (무기 복귀는 F키로만).</li>
     * </ul>
     * <p>
     * 마우스 휠은 ±1 순차 이동이므로, 0~3과 8이 차단되면
     * 자유 영역(4~7)을 벗어날 수 없어 안전하다.
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHotbarChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!combat.isInCombatMode(player)) return;

        int newSlot = event.getNewSlot();

        // ── 슬롯 0~3 (키 1~4): 항상 차단 ──
        if (newSlot >= 0 && newSlot <= 3) {
            event.setCancelled(true);
            // 무기를 들고 있을 때만 스킬 시전
            if (combat.isHoldingWeaponSlot(player)) {
                int skillSlot = newSlot + 3; // 0→3, 1→4, 2→5, 3→6
                combat.castSkill(player, skillSlot);
            }
            return;
        }

        // ── 슬롯 8 (키 9 / 무기): 차단 (F키로만 복귀) ──
        if (newSlot == CombatManager.WEAPON_SLOT) {
            event.setCancelled(true);
            return;
        }

        // ── 슬롯 4~7 (키 5~8): 자유 전환 허용 ──
        // 이벤트 취소 안 함 → 포션/음식 등 사용 가능
    }

    // ===================================================================
    // Q키 (드롭) — 무기 슬롯에서만 차단
    // ===================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (!combat.isInCombatMode(event.getPlayer())) return;
        if (combat.isHoldingWeaponSlot(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ===================================================================
    // 사망 → 강제 해제
    // ===================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (combat.isInCombatMode(event.getEntity())) {
            combat.forceDisable(event.getEntity());
        }
    }

    // ===================================================================
    // 퇴장 → 정리
    // ===================================================================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        combat.forceDisable(p);
        combat.removeState(p.getUniqueId());
    }
}
