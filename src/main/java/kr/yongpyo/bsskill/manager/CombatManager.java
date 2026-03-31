package kr.yongpyo.bsskill.manager;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.skill.ModifiableSkill;
import io.lumine.mythic.lib.skill.handler.MythicMobsSkillHandler;
import io.lumine.mythic.lib.skill.trigger.TriggerMetadata;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.model.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 전투 모드 + 스킬 캐스팅 + 패시브 TIMER 엔진
 *
 * <h3>무기 9번 슬롯 고정</h3>
 * 전투 모드 진입 시 무기를 핫바 인덱스 8(키보드 9)로 이동.
 * 해제 시 원래 위치로 복구. ItemStack 통째로 교환하므로 NBT/CMD 보존.
 *
 * <h3>좌클릭 스킬 + 몬스터 타격 동시 처리</h3>
 * <ul>
 *   <li>공기/블록 좌클릭 (PlayerInteractEvent) → 슬롯 1 스킬만</li>
 *   <li>몬스터 근접 타격 (EntityDamageByEntityEvent, ENTITY_ATTACK) → 슬롯 1 스킬 + 데미지 통과</li>
 *   <li>투사체 피격 (PROJECTILE) → 스킬 발동 안 함 (원거리 무한 시전 방지)</li>
 * </ul>
 *
 * <h3>MythicLib API 시전</h3>
 * <pre>
 *   ModifiableSkill.registerModifier(key, double)
 *   skill.cast(new TriggerMetadata(pd, TriggerType.API, (Entity) null))
 * </pre>
 */
public class CombatManager {

    /** 무기 고정 슬롯 — 핫바 인덱스 8 (키보드 9) */
    public static final int WEAPON_SLOT = 8;

    private final BSSkill plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, CombatState> states = new ConcurrentHashMap<>();
    private final Map<String, MythicMobsSkillHandler> handlerCache = new ConcurrentHashMap<>();

    // 메시지/사운드 캐시 (빈 문자열이면 출력 안 함)
    private String msgCombatOn, msgCombatOff, msgNoWeapon, msgWeaponLost;
    private String msgCooldown, msgSkillCast, msgNoSkill, msgCastFailed, msgReturnWeapon;
    private String sndCombatOn, sndCombatOff, sndSkillCast, sndCooldownDeny, sndReturnWeapon;

    public CombatManager(BSSkill plugin) {
        this.plugin = plugin;
        reloadMessages();
    }

    public void reloadMessages() {
        var c = plugin.getConfig();
        msgCombatOn     = c.getString("messages.combat-on", "");
        msgCombatOff    = c.getString("messages.combat-off", "");
        msgNoWeapon     = c.getString("messages.no-weapon", "");
        msgWeaponLost   = c.getString("messages.weapon-lost", "");
        msgCooldown     = c.getString("messages.cooldown", "");
        msgSkillCast    = c.getString("messages.skill-cast", "");
        msgNoSkill      = c.getString("messages.no-skill", "");
        msgCastFailed   = c.getString("messages.cast-failed", "");
        msgReturnWeapon = c.getString("messages.return-weapon", "");
        sndCombatOn     = c.getString("sounds.combat-on", "");
        sndCombatOff    = c.getString("sounds.combat-off", "");
        sndSkillCast    = c.getString("sounds.skill-cast", "");
        sndCooldownDeny = c.getString("sounds.cooldown-deny", "");
        sndReturnWeapon = c.getString("sounds.return-weapon", "");
    }

    // ===================================================================
    // 핸들러 캐시
    // ===================================================================

    public void warmUpHandlerCache() {
        handlerCache.clear();
        for (WeaponSkill w : plugin.getWeaponSkillManager().getAllWeapons()) {
            for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
                SkillSlot s = w.getSkill(i);
                if (s.isValid()) getOrCreateHandler(s.getMythicId());
            }
            for (PassiveSlot p : w.getPassives())
                if (p.isValid()) getOrCreateHandler(p.getType());
        }
        plugin.getLogger().info("[CombatManager] 핸들러 " + handlerCache.size() + "개 캐싱");
    }

    private MythicMobsSkillHandler getOrCreateHandler(String name) {
        return handlerCache.computeIfAbsent(name, n -> {
            var reg = MythicLib.plugin.getSkills().getHandler(n);
            if (reg instanceof MythicMobsSkillHandler h) return h;
            return new MythicMobsSkillHandler(new org.bukkit.configuration.MemoryConfiguration(), n);
        });
    }

    // ===================================================================
    // 상태 조회
    // ===================================================================

    public CombatState getState(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), CombatState::new);
    }

    public void removeState(UUID uuid) { states.remove(uuid); }

    public boolean isInCombatMode(Player player) {
        CombatState s = states.get(player.getUniqueId());
        return s != null && s.isCombatMode();
    }

    public WeaponSkill getCurrentWeapon(Player player) {
        CombatState s = states.get(player.getUniqueId());
        if (s == null || !s.isCombatMode() || s.getCurrentWeaponId() == null) return null;
        return plugin.getWeaponSkillManager().getWeapon(s.getCurrentWeaponId());
    }

    /** 플레이어가 현재 무기 슬롯(8)을 들고 있는지 */
    public boolean isHoldingWeapon(Player player) {
        CombatState s = states.get(player.getUniqueId());
        if (s == null || !s.isCombatMode()) return false;
        return s.isHoldingWeapon(player.getInventory().getHeldItemSlot());
    }

    // ===================================================================
    // F키 핸들러
    // ===================================================================

    /**
     * @return true이면 이벤트 취소
     */
    public boolean handleFKey(Player player) {
        CombatState state = getState(player);
        if (!state.isCombatMode()) return tryEnableCombat(player, state);
        if (state.isHoldingWeapon(player.getInventory().getHeldItemSlot())) {
            disableCombatMode(player, state);
            return true;
        }
        // 다른 슬롯에서 F → 무기(9번) 복귀
        player.getInventory().setHeldItemSlot(WEAPON_SLOT);
        sendMsg(player, msgReturnWeapon);
        playSound(player, sndReturnWeapon);
        return true;
    }

    // ===================================================================
    // 전투 모드 진입/해제
    // ===================================================================

    private boolean tryEnableCombat(Player player, CombatState state) {
        String wId = detectWeaponId(player);
        if (wId == null || !plugin.getWeaponSkillManager().hasWeapon(wId)) return false;

        WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(wId);

        // 무기를 핫바 9번(인덱스 8)으로 이동
        ensureWeaponAtSlot8(player, state);

        state.setCombatMode(true);
        state.setCurrentWeaponId(wId);
        state.setWeaponSlot(WEAPON_SLOT);
        state.clearAllCooldowns();

        // 패시브 타이머 시작
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
        CombatState s = states.get(player.getUniqueId());
        if (s == null || !s.isCombatMode()) return;
        restoreSlot(player, s);
        s.reset();
    }

    // ===================================================================
    // 무기 9번 슬롯 고정
    // ===================================================================

    private void ensureWeaponAtSlot8(Player player, CombatState state) {
        PlayerInventory inv = player.getInventory();
        int currentSlot = inv.getHeldItemSlot();

        if (currentSlot == WEAPON_SLOT) {
            state.setSwapped(false);
            return;
        }

        // 현재 슬롯 <-> 8번 슬롯 교환 (NBT/CMD 보존)
        ItemStack weaponItem = inv.getItem(currentSlot);
        ItemStack slot8Item = inv.getItem(WEAPON_SLOT);
        inv.setItem(WEAPON_SLOT, weaponItem);
        inv.setItem(currentSlot, slot8Item);
        inv.setHeldItemSlot(WEAPON_SLOT);

        state.setOriginalSlot(currentSlot);
        state.setSwapped(true);
    }

    private void restoreSlot(Player player, CombatState state) {
        if (!state.isSwapped() || !player.isOnline()) return;

        PlayerInventory inv = player.getInventory();
        int original = state.getOriginalSlot();

        if (original >= 0 && original <= 7) {
            ItemStack weaponItem = inv.getItem(WEAPON_SLOT);
            ItemStack originalItem = inv.getItem(original);
            inv.setItem(original, weaponItem);
            inv.setItem(WEAPON_SLOT, originalItem);
            inv.setHeldItemSlot(original);
        }

        state.setSwapped(false);
        state.setOriginalSlot(-1);
    }

    // ===================================================================
    // 패시브 TIMER
    // ===================================================================

    private void startPassiveTimers(Player player, CombatState state, WeaponSkill weapon) {
        for (PassiveSlot passive : weapon.getPassives()) {
            if (!passive.isValid()) continue;
            long ticks = (long) (passive.getTimer() * 20);
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!state.isCombatMode() || !player.isOnline()) return;
                if (!state.isHoldingWeapon(player.getInventory().getHeldItemSlot())) return;
                castPassive(player, state, passive);
            }, ticks, ticks);
            state.addTimerTask(task);
        }
    }

    private void castPassive(Player player, CombatState state, PassiveSlot passive) {
        int cdKey = 100 + passive.getIndex();
        if (passive.getCooldown() > 0 && state.isOnCooldown(cdKey)) return;
        boolean ok = executeCast(player, passive.getType(), passive.getModifiers());
        if (ok && passive.getCooldown() > 0) state.setCooldown(cdKey, passive.getCooldown());
    }

    // ===================================================================
    // 액티브 스킬 캐스팅
    // ===================================================================

    /**
     * 슬롯 번호(1~6)로 액티브 스킬 시전
     *
     * @param player     시전자
     * @param slotNumber 스킬 슬롯 (1~6)
     */
    public void castSkill(Player player, int slotNumber) {
        CombatState state = getState(player);
        if (!state.isCombatMode()) return;

        // 동일 틱 중복 시전 방지 (좌클릭 시 Interact + DamageEvent 동시 발생 대비)
        if (state.isDoubleCast(slotNumber)) return;

        // 무기 NBT 검증
        if (!validateWeapon(player, state)) {
            disableCombatMode(player, state);
            sendMsg(player, msgWeaponLost);
            return;
        }

        WeaponSkill w = plugin.getWeaponSkillManager().getWeapon(state.getCurrentWeaponId());
        if (w == null) return;

        SkillSlot skill = w.getSkill(slotNumber);
        if (skill == null || !skill.isValid()) {
            sendMsg(player, msgNoSkill);
            return;
        }

        // 쿨타임 확인
        if (state.isOnCooldown(slotNumber)) {
            double r = state.getRemainingCooldown(slotNumber);
            sendMsg(player, msgCooldown.replace("{remaining}", String.format("%.1f", r)));
            playSound(player, sndCooldownDeny);
            return;
        }

        // damage 계산 + extra -> modifiers 맵 구성
        double weaponDmg = getWeaponDamage(player);
        Map<String, Double> mods = new java.util.LinkedHashMap<>();
        mods.put("damage", weaponDmg * skill.getDamage());
        for (var e : skill.getExtraParams().entrySet()) {
            mods.put(e.getKey(), toDouble(e.getValue()));
        }

        boolean ok = executeCast(player, skill.getMythicId(), mods);
        if (ok) {
            state.markCast(slotNumber); // 중복 시전 방지 기록
            if (skill.getCooldown() > 0) state.setCooldown(slotNumber, skill.getCooldown());
            sendMsg(player, msgSkillCast.replace("{skill}", skill.getDisplayName()));
            playSound(player, sndSkillCast);
        } else {
            sendMsg(player, msgCastFailed);
        }
    }

    // ===================================================================
    // MythicLib API 공통 시전
    // ===================================================================

    /**
     * MythicLib API로 스킬 시전
     *
     * ModifiableSkill.registerModifier(key, double) -> MythicMobs YML: <modifier.key>
     *
     * TriggerMetadata 생성 시 세 번째 인자는 (Entity) null로 캐스트 필요.
     * (MythicLib JAR 바이트코드 검증: TriggerMetadata(MMOPlayerData, TriggerType, Entity))
     */
    private boolean executeCast(Player player, String mythicId, Map<String, Double> modifiers) {
        try {
            MMOPlayerData pd = MMOPlayerData.get(player.getUniqueId());
            if (pd == null) return false;

            MythicMobsSkillHandler handler = getOrCreateHandler(mythicId);
            ModifiableSkill skill = new ModifiableSkill(handler);

            // 모디파이어 등록 — 대소문자 구분, 키 제한 없음
            for (var e : modifiers.entrySet()) {
                skill.registerModifier(e.getKey(), e.getValue());
            }

            // (Entity) null 캐스트 필수 — 오버로드 모호성 해소
            return skill.cast(
                    new TriggerMetadata(pd, TriggerType.API, (Entity) null)
            ).isSuccessful();

        } catch (Exception e) {
            plugin.getLogger().warning("[CombatManager] 시전 실패 (" + mythicId + "): " + e.getMessage());
            return false;
        }
    }

    // ===================================================================
    // 유틸
    // ===================================================================

    private boolean validateWeapon(Player player, CombatState state) {
        int ws = state.getWeaponSlot();
        if (ws < 0) return false;
        ItemStack item = player.getInventory().getItem(ws);
        if (item == null || item.getType().isAir()) return false;
        try {
            NBTItem nbt = NBTItem.get(item);
            if (!nbt.hasType()) return false;
            String id = nbt.getString("MMOITEMS_ITEM_ID");
            return id != null && id.equals(state.getCurrentWeaponId());
        } catch (Exception e) { return false; }
    }

    public String detectWeaponId(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) return null;
        try {
            NBTItem nbt = NBTItem.get(item);
            if (!nbt.hasType()) return null;
            String id = nbt.getString("MMOITEMS_ITEM_ID");
            return (id != null && !id.isEmpty()) ? id : null;
        } catch (Exception ignored) {}
        return null;
    }

    private double getWeaponDamage(Player player) {
        try {
            MMOPlayerData d = MMOPlayerData.get(player.getUniqueId());
            if (d != null) return d.getStatMap().getStat("ATTACK_DAMAGE");
        } catch (Exception ignored) {}
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        return attr != null ? attr.getValue() : 1.0;
    }

    private void sendMsg(Player p, String msg) {
        if (msg != null && !msg.isEmpty()) p.sendMessage(mm.deserialize(msg));
    }

    private void playSound(Player p, String snd) {
        if (snd == null || snd.isEmpty()) return;
        try {
            if (snd.contains(".")) {
                p.playSound(p.getLocation(), snd, 0.8f, 1.0f);
            } else {
                Sound s = Registry.SOUNDS.get(NamespacedKey.minecraft(snd.toLowerCase(java.util.Locale.ROOT)));
                if (s != null) p.playSound(p.getLocation(), s, 0.8f, 1.0f);
                else p.playSound(p.getLocation(), snd.toLowerCase(java.util.Locale.ROOT), 0.8f, 1.0f);
            }
        } catch (Exception ignored) {}
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }
}
