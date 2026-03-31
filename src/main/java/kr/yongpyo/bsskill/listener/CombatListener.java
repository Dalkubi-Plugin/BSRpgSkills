package kr.yongpyo.bsskill.listener;

import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.manager.CombatManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

/**
 * 전투 모드 핵심 리스너
 *
 * <h3>좌클릭 스킬 + 몬스터 타격 동시 처리</h3>
 * <pre>
 *   1. 공기/블록 좌클릭 (PlayerInteractEvent)
 *      → 슬롯 1 스킬 시전, 이벤트 취소
 *
 *   2. 몬스터 근접 타격 (EntityDamageByEntityEvent)
 *      조건: damager가 Player 본인 (투사체 제외)
 *      → 슬롯 1 스킬 시전 + 바닐라 데미지 유지 (이벤트 취소 안 함)
 *
 *   3. 투사체(화살 등) 피격
 *      조건: damager가 Projectile
 *      → 스킬 발동 안 함 (원거리 무한 시전 방지)
 * </pre>
 *
 * <h3>핫바 규칙</h3>
 * <pre>
 *   인덱스 0~3 (키 1~4) → 항상 차단 (무기 들고 있을 때 스킬 3~6 시전)
 *   인덱스 4~7 (키 5~8) → 자유 전환
 *   인덱스 8   (키 9)   → 무기 고정 (F키로만 복귀/해제)
 * </pre>
 */
public class CombatListener implements Listener {

    private final BSSkill plugin;
    private final CombatManager combat;

    public CombatListener(BSSkill plugin) {
        this.plugin = plugin;
        this.combat = plugin.getCombatManager();
    }

    // ===================================================================
    // F키 -- 전투 모드 토글 / 무기 복귀
    // ===================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (combat.isInCombatMode(player) || combat.detectWeaponId(player) != null) {
            if (combat.handleFKey(player)) event.setCancelled(true);
        }
    }

    // ===================================================================
    // 좌/우클릭 -- 공기/블록 대상 (무기 들고 있을 때만)
    // 좌클릭 = 슬롯 1, 우클릭 = 슬롯 2
    // ===================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!combat.isInCombatMode(player)) return;
        if (!combat.isHoldingWeapon(player)) return;

        Action action = event.getAction();

        // 좌클릭 (공기/블록) → 슬롯 1
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            // 이벤트 취소하지 않음 — 블록 파괴 등 일반 행동은 유지 가능
            // 단, 엔티티 타격은 여기서 처리되지 않음 (EntityDamageByEntityEvent에서 처리)
            combat.castSkill(player, 1);
            return;
        }

        // 우클릭 → 슬롯 2
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true); // 우클릭 기본 행동(방패 등) 차단
            combat.castSkill(player, 2);
        }
    }

    // ===================================================================
    // 몬스터 근접 타격 → 슬롯 1 스킬 동시 발동
    //
    // 핵심: 투사체(Projectile)가 아닌 직접 공격(ENTITY_ATTACK)만 처리
    // → 원거리 직업의 무한 스킬 시전 문제 방지
    //
    // 이벤트를 취소하지 않으므로 바닐라/MMOItems 데미지가 정상 적용됨
    // ===================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeAttack(EntityDamageByEntityEvent event) {
        // damager가 Player 본인인지 확인 (투사체 제외)
        if (!(event.getDamager() instanceof Player player)) return;

        // 투사체를 통한 데미지는 무시 (원거리 무한 시전 방지)
        // 투사체 피격 시 damager = Projectile 이므로 위 instanceof Player에서 이미 걸러짐
        // 추가 안전장치: getCause 확인
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        // 전투 모드 + 무기 들고 있을 때만
        if (!combat.isInCombatMode(player)) return;
        if (!combat.isHoldingWeapon(player)) return;

        // 자기 자신을 때리는 경우 제외
        if (event.getEntity().equals(player)) return;

        // 슬롯 1 스킬 시전 (데미지 이벤트는 취소하지 않음 → 바닐라 타격 유지)
        combat.castSkill(player, 1);
    }

    // ===================================================================
    // 핫바 키 처리
    //
    // 인덱스 0~3 (키 1~4): 항상 차단
    //   - 무기 들고 있을 때 → 스킬 3~6 시전
    //   - 다른 슬롯에서 → 이동만 차단 (스킬 안 시전)
    //
    // 인덱스 4~7 (키 5~8): 자유 이동
    // 인덱스 8   (키 9): 무기 고정, 이동 차단 (F키로만)
    // ===================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onHotbarChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!combat.isInCombatMode(player)) return;

        int newSlot = event.getNewSlot();

        // 인덱스 0~3 (키 1~4) → 항상 차단
        if (newSlot >= 0 && newSlot <= 3) {
            event.setCancelled(true);
            // 무기를 들고 있을 때만 스킬 시전
            if (combat.isHoldingWeapon(player)) {
                combat.castSkill(player, newSlot + 3); // 0→3, 1→4, 2→5, 3→6
            }
            return;
        }

        // 인덱스 8 (키 9 / 무기) → 차단 (F키로만 복귀)
        if (newSlot == CombatManager.WEAPON_SLOT) {
            event.setCancelled(true);
            return;
        }

        // 인덱스 4~7 (키 5~8) → 자유 이동 (이벤트 취소 안 함)
    }

    // ===================================================================
    // Q키 -- 무기 슬롯에서만 드롭 차단
    // ===================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (combat.isInCombatMode(event.getPlayer()) && combat.isHoldingWeapon(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ===================================================================
    // 사망 / 퇴장
    // ===================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (combat.isInCombatMode(event.getEntity())) combat.forceDisable(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combat.forceDisable(event.getPlayer());
        combat.removeState(event.getPlayer().getUniqueId());
    }
}
