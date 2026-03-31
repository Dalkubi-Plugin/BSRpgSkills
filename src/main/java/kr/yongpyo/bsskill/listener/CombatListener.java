package kr.yongpyo.bsskill.listener;

import io.lumine.mythic.lib.api.event.PlayerAttackEvent;
import io.lumine.mythic.lib.api.event.PlayerClickEvent;
import io.lumine.mythic.lib.damage.AttackMetadata;
import io.lumine.mythic.lib.damage.DamageType;
import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.manager.CombatManager;
import kr.yongpyo.bsskill.model.CombatState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.UUID;

/**
 * 실제 입력 이벤트를 전투 스킬 슬롯과 연결하는 리스너입니다.
 * 핵심 규칙은 단순합니다.
 *
 * 1. 평타 슬롯 1은 "일반 무기 공격(WEAPON)" 또는 "좌클릭 입력"만 발동시킵니다.
 * 2. 스킬성 데미지는 스킬성 데미지로만 처리하고, 평타 발동 조건에 절대 섞지 않습니다.
 * 3. 같은 입력이 두 이벤트로 들어와도 CombatState의 중복 발동 가드가 마지막 안전장치가 됩니다.
 */
public class CombatListener implements Listener {

    private final CombatManager combat;

    public CombatListener(BSSkill plugin) {
        this.combat = plugin.getCombatManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerAttack(PlayerAttackEvent event) {
        AttackMetadata attack = event.getAttack();

        // 평타 슬롯 1은 "일반 공격"일 때만 발동합니다.
        // 따라서 스킬이 WEAPON 데미지를 만들지 않도록 설계하면 재귀가 구조적으로 사라집니다.
        if (!attack.getDamage().hasType(DamageType.WEAPON)) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null || !canUseCombatWeapon(player)) {
            return;
        }

        CombatState state = combat.getState(player);
        if (!state.canTriggerSkill(1)) {
            return;
        }

        combat.castSkill(player, 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerClick(PlayerClickEvent event) {
        Player player = event.getPlayer();
        if (!canUseCombatWeapon(player)) {
            return;
        }

        CombatState state = combat.getState(player);

        if (event.isLeftClick()) {
            if (!state.canTriggerSkill(1)) {
                return;
            }

            // 허공 좌클릭/블록 좌클릭 모두 평타 슬롯 1로 연결합니다.
            // 실제 타격이 있었다면 PlayerAttackEvent도 들어오지만, 중복 발동 가드가 1회를 보장합니다.
            if (!event.hasBlock() || event.getClickedBlock() != null) {
                combat.castSkill(player, 1);
            }
            return;
        }

        // 우클릭은 전투 모드에서 기본 상호작용보다 스킬 우선이므로 슬롯 2를 실행합니다.
        event.setCancelled(true);
        combat.castSkill(player, 2);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (combat.isInCombatMode(player) || combat.detectWeaponId(player) != null) {
            if (combat.handleFKey(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHotbarChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!combat.isInCombatMode(player)) {
            return;
        }

        int newSlot = event.getNewSlot();
        if (newSlot >= 0 && newSlot <= 3) {
            event.setCancelled(true);
            if (combat.isHoldingWeapon(player)) {
                combat.castSkill(player, newSlot + 3);
            }
            return;
        }

        if (newSlot == CombatManager.WEAPON_SLOT) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (combat.isInCombatMode(event.getPlayer()) && combat.isHoldingWeapon(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (combat.isInCombatMode(event.getEntity())) {
            combat.forceDisable(event.getEntity());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        combat.forceDisable(event.getPlayer());
        combat.removeState(uuid);
    }

    /**
     * 전투 모드와 전투 무기 장착 여부를 함께 검사해
     * 입력 처리 기준이 모든 이벤트에서 동일하게 유지되도록 합니다.
     */
    private boolean canUseCombatWeapon(Player player) {
        return combat.isInCombatMode(player) && combat.isHoldingWeapon(player);
    }
}
