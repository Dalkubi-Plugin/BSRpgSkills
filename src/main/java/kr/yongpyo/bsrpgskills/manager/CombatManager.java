package kr.yongpyo.bsrpgskills.manager;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.skill.ModifiableSkill;
import io.lumine.mythic.lib.skill.handler.MythicMobsSkillHandler;
import io.lumine.mythic.lib.skill.trigger.TriggerMetadata;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import kr.yongpyo.bsrpgskills.BSRpgSkills;
import kr.yongpyo.bsrpgskills.model.CombatState;
import kr.yongpyo.bsrpgskills.model.PassiveSlot;
import kr.yongpyo.bsrpgskills.model.PassiveTrigger;
import kr.yongpyo.bsrpgskills.model.SkillSlot;
import kr.yongpyo.bsrpgskills.model.WeaponSkill;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 전투 모드 진입, 무기 고정, 스킬 캐스팅, 패시브 타이머까지
 * 전투 시스템의 핵심 흐름을 관리하는 매니저입니다.
 *
 * 스킬 발동 조건 검증과 캐스팅 결과 처리를 메서드 단위로 분리하여
 * 리스너에서는 "무슨 입력이 들어왔는가"에만 집중할 수 있도록 구성합니다.
 */
public class CombatManager {

    public static final int WEAPON_SLOT = 8;

    private final BSRpgSkills plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, CombatState> states = new ConcurrentHashMap<>();
    private final Map<String, MythicMobsSkillHandler> handlerCache = new ConcurrentHashMap<>();

    private String msgCombatOn;
    private String msgCombatOff;
    private String msgNoWeapon;
    private String msgWeaponLost;
    private String msgCooldown;
    private String msgSkillCast;
    private String msgNoSkill;
    private String msgCastFailed;
    private String msgReturnWeapon;

    private String sndCombatOn;
    private String sndCombatOff;
    private String sndSkillCast;
    private String sndCooldownDeny;
    private String sndReturnWeapon;

    public CombatManager(BSRpgSkills plugin) {
        this.plugin = plugin;
        reloadMessages();
    }

    public void reloadMessages() {
        var config = plugin.getConfig();
        msgCombatOn = config.getString("messages.combat-on", "");
        msgCombatOff = config.getString("messages.combat-off", "");
        msgNoWeapon = config.getString("messages.no-weapon", "");
        msgWeaponLost = config.getString("messages.weapon-lost", "");
        msgCooldown = config.getString("messages.cooldown", "");
        msgSkillCast = config.getString("messages.skill-cast", "");
        msgNoSkill = config.getString("messages.no-skill", "");
        msgCastFailed = config.getString("messages.cast-failed", "");
        msgReturnWeapon = config.getString("messages.return-weapon", "");

        sndCombatOn = config.getString("sounds.combat-on", "");
        sndCombatOff = config.getString("sounds.combat-off", "");
        sndSkillCast = config.getString("sounds.skill-cast", "");
        sndCooldownDeny = config.getString("sounds.cooldown-deny", "");
        sndReturnWeapon = config.getString("sounds.return-weapon", "");
    }

    public void warmUpHandlerCache() {
        handlerCache.clear();

        for (WeaponSkill weapon : plugin.getWeaponSkillManager().getAllWeapons()) {
            for (int slot = 1; slot <= WeaponSkill.MAX_SLOTS; slot++) {
                SkillSlot skill = weapon.getSkill(slot);
                if (skill != null && skill.isValid()) {
                    getOrCreateHandler(skill.getMythicId());
                }
            }

            for (PassiveSlot passive : weapon.getPassives()) {
                if (passive.isValid()) {
                    getOrCreateHandler(passive.getType());
                }
            }
        }
    }

    private MythicMobsSkillHandler getOrCreateHandler(String skillName) {
        return handlerCache.computeIfAbsent(skillName, key -> {
            var registered = MythicLib.plugin.getSkills().getHandler(key);
            if (registered instanceof MythicMobsSkillHandler handler) {
                return handler;
            }

            return new MythicMobsSkillHandler(new MemoryConfiguration(), key);
        });
    }

    public CombatState getState(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> new CombatState());
    }

    /**
     * 조회만 필요할 때는 상태 객체를 새로 만들지 않고 현재 존재하는 값만 반환합니다.
     * Placeholder/HUD 같은 읽기 전용 경로에서 불필요한 상태 생성을 막는 용도입니다.
     */
    public CombatState getExistingState(Player player) {
        return states.get(player.getUniqueId());
    }

    public void removeState(UUID uuid) {
        states.remove(uuid);
    }

    public boolean isInCombatMode(Player player) {
        CombatState state = states.get(player.getUniqueId());
        return state != null && state.isCombatMode();
    }

    public boolean handleFKey(Player player) {
        CombatState state = getState(player);

        if (!state.isCombatMode()) {
            return tryEnableCombat(player, state);
        }

        if (state.isHoldingWeapon(player.getInventory().getHeldItemSlot())) {
            disableCombatMode(player, state);
            return true;
        }

        player.getInventory().setHeldItemSlot(WEAPON_SLOT);
        sendMsg(player, msgReturnWeapon);
        playSound(player, sndReturnWeapon);
        return true;
    }

    private boolean tryEnableCombat(Player player, CombatState state) {
        String weaponId = detectWeaponId(player);
        if (weaponId == null || !plugin.getWeaponSkillManager().hasWeapon(weaponId)) {
            return false;
        }

        WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(weaponId);
        if (weapon == null) {
            return false;
        }

        ensureWeaponAtSlot8(player, state);
        state.setCombatMode(true);
        state.setCurrentWeaponId(weaponId);
        state.setWeaponSlot(WEAPON_SLOT);
        // 쿨타임은 플레이어 세션 수명 동안 유지됩니다 — 전투 모드 토글로 초기화되지 않습니다.
        startPassiveTimers(player, state, weapon);

        sendMsg(player, msgCombatOn.replace("{weapon}", weapon.getDisplayName()));
        playSound(player, sndCombatOn);
        return true;
    }

    public void disableCombatMode(Player player, CombatState state) {
        restoreSlot(player, state);
        state.reset();
        sendMsg(player, msgCombatOff);
        playSound(player, sndCombatOff);
    }

    public void forceDisable(Player player) {
        CombatState state = states.get(player.getUniqueId());
        if (state == null || !state.isCombatMode()) {
            return;
        }

        restoreSlot(player, state);
        state.reset();
    }

    private void ensureWeaponAtSlot8(Player player, CombatState state) {
        PlayerInventory inventory = player.getInventory();
        int currentSlot = inventory.getHeldItemSlot();

        if (currentSlot == WEAPON_SLOT) {
            state.setSwapped(false);
            return;
        }

        ItemStack weaponItem = inventory.getItem(currentSlot);
        ItemStack slot8Item = inventory.getItem(WEAPON_SLOT);

        inventory.setItem(WEAPON_SLOT, weaponItem);
        inventory.setItem(currentSlot, slot8Item);
        inventory.setHeldItemSlot(WEAPON_SLOT);

        state.setOriginalSlot(currentSlot);
        state.setSwapped(true);
    }

    private void restoreSlot(Player player, CombatState state) {
        if (!state.isSwapped() || !player.isOnline()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int originalSlot = state.getOriginalSlot();
        if (originalSlot < 0 || originalSlot > 7) {
            state.setSwapped(false);
            return;
        }

        ItemStack weaponItem = inventory.getItem(WEAPON_SLOT);
        ItemStack originalItem = inventory.getItem(originalSlot);

        inventory.setItem(originalSlot, weaponItem);
        inventory.setItem(WEAPON_SLOT, originalItem);
        inventory.setHeldItemSlot(originalSlot);
        state.setSwapped(false);
    }

    private void startPassiveTimers(Player player, CombatState state, WeaponSkill weapon) {
        UUID playerId = player.getUniqueId();

        for (PassiveSlot passive : weapon.getPassives()) {
            if (!passive.isValid() || passive.getTriggerType() != PassiveTrigger.TIMER) {
                continue;
            }

            long intervalTicks = Math.max(1L, Math.round(passive.getTimer() * 20));
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                Player current = Bukkit.getPlayer(playerId);
                CombatState currentState = states.get(playerId);
                if (current == null || currentState == null || !canRunPassive(current, currentState)) {
                    return;
                }

                castPassive(current, currentState, passive);
            }, intervalTicks, intervalTicks);
            state.addTimerTask(task);
        }
    }

    /**
     * 이벤트 기반 패시브(ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN)를 발동합니다.
     * 해당 트리거와 일치하는 패시브를 순회하며 chance/cooldown 검증 후 캐스팅합니다.
     *
     * @param player  전투 모드 중인 플레이어
     * @param trigger 발동 조건 (TIMER 제외)
     */
    public void triggerEventPassives(Player player, PassiveTrigger trigger) {
        if (!trigger.isEventTrigger()) {
            return;
        }

        CombatState state = states.get(player.getUniqueId());
        if (state == null || state.isInternalCasting() || !canRunPassive(player, state)) {
            return;
        }

        WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(state.getCurrentWeaponId());
        if (weapon == null) {
            return;
        }

        for (PassiveSlot passive : weapon.getPassives()) {
            if (!passive.isValid() || passive.getTriggerType() != trigger) {
                continue;
            }

            if (passive.getChance() < 1.0 && ThreadLocalRandom.current().nextDouble() >= passive.getChance()) {
                continue;
            }

            castPassive(player, state, passive);
        }
    }

    private boolean canRunPassive(Player player, CombatState state) {
        return state.isCombatMode()
                && player.isOnline()
                && state.isHoldingWeapon(player.getInventory().getHeldItemSlot());
    }

    private void castPassive(Player player, CombatState state, PassiveSlot passive) {
        int cooldownKey = 100 + passive.getIndex();
        String weaponId = state.getCurrentWeaponId();
        if (passive.getCooldown() > 0 && state.isOnCooldown(weaponId, cooldownKey)) {
            return;
        }

        boolean castSucceeded = executeCast(player, state, passive.getType(), passive.getModifiers());
        if (castSucceeded && passive.getCooldown() > 0) {
            state.setCooldown(weaponId, cooldownKey, passive.getCooldown());
        }
    }

    public boolean castSkill(Player player, int slotNumber) {
        CombatState state = getState(player);
        if (!state.isCombatMode()) {
            return false;
        }

        SkillSlot skill = resolveActiveSkill(player, state, slotNumber);
        if (skill == null) {
            return false;
        }

        if (!state.canTriggerSkill(slotNumber)) {
            return false;
        }

        String weaponId = state.getCurrentWeaponId();
        if (state.isOnCooldown(weaponId, slotNumber)) {
            double remaining = state.getRemainingCooldown(weaponId, slotNumber);
            sendMsg(player, msgCooldown.replace("{remaining}", String.format("%.1f", remaining)));
            playSound(player, sndCooldownDeny);
            return false;
        }

        state.markCast(slotNumber);

        Map<String, Double> modifiers = buildSkillModifiers(player, skill);
        boolean castSucceeded = executeCast(player, state, skill.getMythicId(), modifiers);

        if (!castSucceeded) {
            sendMsg(player, msgCastFailed);
            return false;
        }

        if (skill.getCooldown() > 0) {
            state.setCooldown(weaponId, slotNumber, skill.getCooldown());
        }

        sendMsg(player, msgSkillCast.replace("{skill}", skill.getDisplayName()));
        playSound(player, sndSkillCast);
        return true;
    }

    private SkillSlot resolveActiveSkill(Player player, CombatState state, int slotNumber) {
        if (!validateWeapon(player, state)) {
            disableCombatMode(player, state);
            return null;
        }

        WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(state.getCurrentWeaponId());
        if (weapon == null) {
            sendMsg(player, msgWeaponLost);
            return null;
        }

        SkillSlot skill = weapon.getSkill(slotNumber);
        if (skill == null || !skill.isValid()) {
            return null;
        }

        return skill;
    }

    private Map<String, Double> buildSkillModifiers(Player player, SkillSlot skill) {
        Map<String, Double> modifiers = new LinkedHashMap<>();

        for (var entry : skill.getModifiers().entrySet()) {
            modifiers.put(entry.getKey(), entry.getValue());
        }

        return modifiers;
    }

    private boolean executeCast(Player player, CombatState state, String mythicId, Map<String, Double> modifiers) {
        state.setInternalCasting(true);

        try {
            MMOPlayerData playerData = MMOPlayerData.get(player.getUniqueId());
            if (playerData == null) {
                return false;
            }

            MythicMobsSkillHandler handler = getOrCreateHandler(mythicId);
            ModifiableSkill skill = new ModifiableSkill(handler);

            for (var entry : modifiers.entrySet()) {
                skill.registerModifier(entry.getKey(), entry.getValue());
            }

            return skill.cast(new TriggerMetadata(playerData, TriggerType.API, (Entity) null)).isSuccessful();
        } catch (Exception exception) {
            plugin.getLogger().warning("스킬 캐스팅 실패: " + mythicId + " (" + exception.getMessage() + ")");
            plugin.logDebug("캐스팅 예외 상세: " + mythicId, exception);
            return false;
        } finally {
            state.setInternalCasting(false);
        }
    }

    private boolean validateWeapon(Player player, CombatState state) {
        int weaponSlot = state.getWeaponSlot();
        if (weaponSlot < 0) {
            return false;
        }

        ItemStack item = player.getInventory().getItem(weaponSlot);
        if (item == null || item.getType().isAir()) {
            return false;
        }

        try {
            NBTItem nbtItem = NBTItem.get(item);
            return nbtItem.hasType() && nbtItem.getString("MMOITEMS_ITEM_ID").equals(state.getCurrentWeaponId());
        } catch (Exception exception) {
            return false;
        }
    }

    public String detectWeaponId(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            return null;
        }

        try {
            NBTItem nbtItem = NBTItem.get(item);
            String weaponId = nbtItem.getString("MMOITEMS_ITEM_ID");
            return weaponId != null && !weaponId.isEmpty() ? weaponId : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private double getWeaponDamage(Player player) {
        try {
            MMOPlayerData playerData = MMOPlayerData.get(player.getUniqueId());
            if (playerData != null) {
                return playerData.getStatMap().getStat("ATTACK_DAMAGE");
            }
        } catch (Exception ignored) {
            return 1.0;
        }

        return 1.0;
    }

    private void sendMsg(Player player, String message) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(message));
        }
    }

    private void playSound(Player player, String soundKey) {
        if (soundKey == null || soundKey.isEmpty()) {
            return;
        }

        try {
            player.playSound(player.getLocation(), soundKey, 0.8f, 1.0f);
        } catch (Exception ignored) {
        }
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception exception) {
            return 0.0;
        }
    }

    public WeaponSkill getCurrentWeapon(Player player) {
        CombatState state = states.get(player.getUniqueId());
        if (state == null || !state.isCombatMode() || state.getCurrentWeaponId() == null) {
            return null;
        }

        return plugin.getWeaponSkillManager().getWeapon(state.getCurrentWeaponId());
    }

    public boolean isHoldingWeapon(Player player) {
        CombatState state = states.get(player.getUniqueId());
        return state != null
                && state.isCombatMode()
                && player.getInventory().getHeldItemSlot() == WEAPON_SLOT;
    }

    public boolean isHoldingWeapon(int slot) {
        return slot == WEAPON_SLOT;
    }
}
