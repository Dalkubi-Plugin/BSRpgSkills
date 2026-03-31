package kr.yongpyo.bsskill.manager;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.skill.ModifiableSkill;
import io.lumine.mythic.lib.skill.handler.MythicMobsSkillHandler;
import io.lumine.mythic.lib.skill.trigger.TriggerMetadata;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.model.CombatState;
import kr.yongpyo.bsskill.model.PassiveSlot;
import kr.yongpyo.bsskill.model.SkillSlot;
import kr.yongpyo.bsskill.model.WeaponSkill;
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

/**
 * 전투 모드 진입, 무기 고정, 스킬 캐스팅, 패시브 타이머까지
 * 전투 시스템의 핵심 흐름을 관리하는 매니저입니다.
 *
 * 스킬 발동 조건 검증과 캐스팅 결과 처리를 메서드 단위로 분리하여
 * 리스너에서는 "무슨 입력이 들어왔는가"에만 집중할 수 있도록 구성합니다.
 */
public class CombatManager {

    public static final int WEAPON_SLOT = 8;

    private final BSSkill plugin;
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

    public CombatManager(BSSkill plugin) {
        this.plugin = plugin;
        reloadMessages();
    }

    public void reloadMessages() {
        // 메시지/사운드는 리로드 시 즉시 반영될 수 있도록 한곳에서 다시 읽어옵니다.
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
        // 서버 시작 또는 리로드 직후 자주 쓰일 핸들러를 미리 캐싱해 첫 발동 지연을 줄입니다.
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
            // 등록된 핸들러가 있으면 그대로 쓰고, 없으면 MythicLib가 처리 가능한 형태로 래핑합니다.
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
            // 전투 모드가 아닐 때 F 키는 전투 모드 진입 시도로 사용합니다.
            return tryEnableCombat(player, state);
        }

        if (state.isHoldingWeapon(player.getInventory().getHeldItemSlot())) {
            // 이미 전투 무기를 들고 있으면 같은 키로 전투 모드를 종료합니다.
            disableCombatMode(player, state);
            return true;
        }

        // 전투 중 다른 슬롯으로 잠시 빠졌다면 무기 슬롯으로 강제로 복귀시킵니다.
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

        // 전투 모드 진입 시 무기를 8번 슬롯으로 고정하고, 전투 중 필요한 런타임 상태를 초기화합니다.
        ensureWeaponAtSlot8(player, state);
        state.setCombatMode(true);
        state.setCurrentWeaponId(weaponId);
        state.setWeaponSlot(WEAPON_SLOT);
        state.clearAllCooldowns();
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

        // 전투 중에는 무기 슬롯을 고정해 입력 체계를 단순화합니다.
        // 원래 슬롯 정보는 종료 시 되돌리기 위해 상태 객체에 보관합니다.
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

        // 전투 시작 전에 들고 있던 슬롯 구성을 복구해 사용감이 어색하지 않도록 합니다.
        ItemStack weaponItem = inventory.getItem(WEAPON_SLOT);
        ItemStack originalItem = inventory.getItem(originalSlot);

        inventory.setItem(originalSlot, weaponItem);
        inventory.setItem(WEAPON_SLOT, originalItem);
        inventory.setHeldItemSlot(originalSlot);
        state.setSwapped(false);
    }

    private void startPassiveTimers(Player player, CombatState state, WeaponSkill weapon) {
        for (PassiveSlot passive : weapon.getPassives()) {
            if (!passive.isValid()) {
                continue;
            }

            // 패시브마다 독립 타이머를 등록하되, 실제 실행 가능 여부는 매 틱 검증합니다.
            long intervalTicks = Math.max(1L, Math.round(passive.getTimer() * 20));
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!canRunPassive(player, state)) {
                    return;
                }

                castPassive(player, state, passive);
            }, intervalTicks, intervalTicks);
            state.addTimerTask(task);
        }
    }

    private boolean canRunPassive(Player player, CombatState state) {
        // 패시브는 전투 모드 유지, 온라인 상태, 전투 무기 장착이 모두 맞을 때만 실행합니다.
        return state.isCombatMode()
                && player.isOnline()
                && state.isHoldingWeapon(player.getInventory().getHeldItemSlot());
    }

    private void castPassive(Player player, CombatState state, PassiveSlot passive) {
        int cooldownKey = 100 + passive.getIndex();
        if (passive.getCooldown() > 0 && state.isOnCooldown(cooldownKey)) {
            return;
        }

        boolean castSucceeded = executeCast(player, state, passive.getType(), passive.getModifiers());
        if (castSucceeded && passive.getCooldown() > 0) {
            state.setCooldown(cooldownKey, passive.getCooldown());
        }
    }

    public void castSkill(Player player, int slotNumber) {
        CombatState state = getState(player);
        if (!state.isCombatMode()) {
            return;
        }

        // 슬롯 해석, 무기 검증, 스킬 유효성 검사를 먼저 모아서 실패 지점을 단순화합니다.
        SkillSlot skill = resolveActiveSkill(player, state, slotNumber);
        if (skill == null) {
            return;
        }

        if (!state.canTriggerSkill(slotNumber)) {
            return;
        }

        if (state.isOnCooldown(slotNumber)) {
            double remaining = state.getRemainingCooldown(slotNumber);
            sendMsg(player, msgCooldown.replace("{remaining}", String.format("%.1f", remaining)));
            playSound(player, sndCooldownDeny);
            return;
        }

        // 실제 캐스팅 직전에 중복 발동 방지용 타임스탬프를 기록합니다.
        state.markCast(slotNumber);

        Map<String, Double> modifiers = buildSkillModifiers(player, skill);
        boolean castSucceeded = executeCast(player, state, skill.getMythicId(), modifiers);

        if (!castSucceeded) {
            sendMsg(player, msgCastFailed);
            return;
        }

        if (skill.getCooldown() > 0) {
            state.setCooldown(slotNumber, skill.getCooldown());
        }

        sendMsg(player, msgSkillCast.replace("{skill}", skill.getDisplayName()));
        playSound(player, sndSkillCast);
    }

    private SkillSlot resolveActiveSkill(Player player, CombatState state, int slotNumber) {
        if (!validateWeapon(player, state)) {
            // 전투 중 무기가 바뀌거나 사라졌다면 잘못된 상태를 끌고 가지 않고 즉시 전투를 종료합니다.
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
            // 빈 슬롯은 조용히 무시해 입력 체계는 유지하고 불필요한 경고는 만들지 않습니다.
            return null;
        }

        return skill;
    }

    private Map<String, Double> buildSkillModifiers(Player player, SkillSlot skill) {
        // 액티브 스킬 modifier는 계산하지 않고 그대로 전달합니다.
        // 예를 들어 damage와 ratio가 있으면 둘 다 각각 MythicLib에 전달됩니다.
        Map<String, Double> modifiers = new LinkedHashMap<>();

        for (var entry : skill.getModifiers().entrySet()) {
            modifiers.put(entry.getKey(), entry.getValue());
        }

        return modifiers;
    }

    private boolean executeCast(Player player, CombatState state, String mythicId, Map<String, Double> modifiers) {
        // 동일 이벤트 체인 안에서 재귀 발동하지 않도록 캐스팅 시작 직전에 내부 락을 겁니다.
        state.setInternalCasting(true);

        try {
            MMOPlayerData playerData = MMOPlayerData.get(player.getUniqueId());
            if (playerData == null) {
                return false;
            }

            MythicMobsSkillHandler handler = getOrCreateHandler(mythicId);
            ModifiableSkill skill = new ModifiableSkill(handler);

            // 설정 기반 modifier를 모두 주입한 뒤 API 트리거로 직접 스킬을 실행합니다.
            for (var entry : modifiers.entrySet()) {
                skill.registerModifier(entry.getKey(), entry.getValue());
            }

            return skill.cast(new TriggerMetadata(playerData, TriggerType.API, (Entity) null)).isSuccessful();
        } catch (Exception exception) {
            plugin.getLogger().warning("스킬 캐스팅 실패: " + mythicId + " (" + exception.getMessage() + ")");
            plugin.logDebug("캐스팅 예외 상세: " + mythicId, exception);
            return false;
        } finally {
            // 기존에는 다음 틱까지 락을 유지해서 입력이 살짝 끊겨 보였기 때문에
            // 동기 캐스팅 스택이 끝나는 즉시 락을 해제하여 반응성을 높입니다.
            state.setInternalCasting(false);
        }
    }

    private boolean validateWeapon(Player player, CombatState state) {
        // 전투 상태가 실제 인벤토리의 MMOITEMS_ITEM_ID와 일치하는지 매번 확인해
        // 슬롯 이동, 드롭, 교체 등으로 생기는 불일치를 조기에 차단합니다.
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
        // 메인핸드 아이템의 MMOITEMS_ITEM_ID를 읽어 전투 가능 무기인지 판별합니다.
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
        // MMOPlayerData의 ATTACK_DAMAGE를 기준으로 스킬 계수를 곱할 수 있게 기본 공격력을 가져옵니다.
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
        // Placeholder나 HUD에서 현재 전투 무기 정보를 조회할 때 사용하는 진입점입니다.
        CombatState state = states.get(player.getUniqueId());
        if (state == null || !state.isCombatMode() || state.getCurrentWeaponId() == null) {
            return null;
        }

        return plugin.getWeaponSkillManager().getWeapon(state.getCurrentWeaponId());
    }

    public boolean isHoldingWeapon(Player player) {
        // 현재 들고 있는 슬롯이 전투용 고정 슬롯인지 빠르게 판별합니다.
        CombatState state = states.get(player.getUniqueId());
        return state != null
                && state.isCombatMode()
                && player.getInventory().getHeldItemSlot() == WEAPON_SLOT;
    }

    public boolean isHoldingWeapon(int slot) {
        return slot == WEAPON_SLOT;
    }
}
