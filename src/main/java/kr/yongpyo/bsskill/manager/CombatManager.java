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
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 전투 모드 + 스킬 캐스팅 + 패시브 스케줄러 엔진
 *
 * <h3>핫바 슬롯 설계</h3>
 * <pre>
 *   index 0~3 (키 1~4) → 스킬 3~6 (항상 차단, 무기 들고 있을 때만 시전)
 *   index 4~7 (키 5~8) → 자유 전환 (포션/음식)
 *   index 8   (키 9)   → 무기 고정
 * </pre>
 *
 * <h3>MythicLib API 시전 (registerModifier)</h3>
 * <pre>
 *   MythicMobsSkillHandler handler = getOrCreateHandler(mythicId);
 *   ModifiableSkill modSkill = new ModifiableSkill(handler);
 *   modSkill.registerModifier("damage", finalDmg);      // {@code <modifier.damage>}
 *   modSkill.registerModifier("heal", 3.0);              // {@code <modifier.heal>}
 *   modSkill.cast(new TriggerMetadata(playerData, TriggerType.API, null));
 * </pre>
 */
public class CombatManager {

    /** 무기 고정 슬롯 — 핫바 인덱스 8 (키보드 9) */
    public static final int WEAPON_SLOT = 8;

    private final BSSkill plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, CombatState> states = new ConcurrentHashMap<>();

    /** MythicMobs 스킬 핸들러 캐시 (서버 시작 시 워밍) */
    private final Map<String, MythicMobsSkillHandler> handlerCache = new ConcurrentHashMap<>();

    // ── 메시지/사운드 캐시 (빈 문자열이면 출력 안 함) ──
    private String msgCombatOn, msgCombatOff, msgNoWeapon, msgWeaponLost;
    private String msgCooldown, msgSkillCast, msgNoSkill, msgCastFailed, msgReturnWeapon;
    private String sndCombatOn, sndCombatOff, sndSkillCast, sndCooldownDeny, sndReturnWeapon;

    public CombatManager(BSSkill plugin) {
        this.plugin = plugin;
        reloadMessages();
    }

    // ===================================================================
    // Config 메시지/사운드 캐싱
    // ===================================================================

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

    /**
     * 무기 데이터 로드 후 모든 스킬/패시브의 핸들러를 사전 캐싱.
     * Jules(MythicLib 개발자) 권장: "SkillHandler는 메타데이터를 포함하므로 startup 시 생성이 성능상 유리"
     */
    public void warmUpHandlerCache() {
        handlerCache.clear();
        for (WeaponSkill weapon : plugin.getWeaponSkillManager().getAllWeapons()) {
            // 액티브 스킬
            for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
                SkillSlot s = weapon.getSkill(i);
                if (s.isValid()) getOrCreateHandler(s.getMythicId());
            }
            // 패시브
            for (PassiveSlot p : weapon.getPassives()) {
                if (p.isValid()) getOrCreateHandler(p.getType());
            }
        }
        plugin.getLogger().info("[CombatManager] 핸들러 " + handlerCache.size() + "개 캐싱 완료");
    }

    /**
     * MythicMobsSkillHandler 캐시 조회 또는 생성.
     * <ol>
     *   <li>MythicLib 스킬 레지스트리에서 조회 시도</li>
     *   <li>없으면 MythicMobsSkillHandler 직접 생성 (MemoryConfiguration)</li>
     * </ol>
     */
    private MythicMobsSkillHandler getOrCreateHandler(String mythicSkillName) {
        return handlerCache.computeIfAbsent(mythicSkillName, name -> {
            // MythicLib 레지스트리에서 조회
            var registered = MythicLib.plugin.getSkills().getHandler(name);
            if (registered instanceof MythicMobsSkillHandler mmh) return mmh;
            // 레지스트리에 없으면 직접 생성
            return new MythicMobsSkillHandler(
                    new org.bukkit.configuration.MemoryConfiguration(), name);
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

    /** 플레이어가 현재 무기 슬롯(8)을 들고 있는지 확인 */
    public boolean isHoldingWeaponSlot(Player player) {
        CombatState st = states.get(player.getUniqueId());
        if (st == null || !st.isCombatMode()) return false;
        return st.isHoldingWeapon(player.getInventory().getHeldItemSlot());
    }

    // ===================================================================
    // F키 동작 (이중 기능)
    // ===================================================================

    /**
     * F키 처리
     * <ul>
     *   <li>전투 모드 OFF + 등록된 무기 → 진입</li>
     *   <li>전투 모드 ON + 무기 슬롯 → 해제</li>
     *   <li>전투 모드 ON + 아이템 슬롯 → 무기 슬롯(8)으로 복귀</li>
     * </ul>
     * @return true이면 이벤트 취소 필요
     */
    public boolean handleFKey(Player player) {
        CombatState state = getState(player);

        if (!state.isCombatMode()) {
            return tryEnableCombat(player, state);
        }

        // 무기 슬롯에서 F → 해제
        if (state.isHoldingWeapon(player.getInventory().getHeldItemSlot())) {
            disableCombatMode(player, state);
            return true;
        }

        // 아이템 슬롯에서 F → 무기 복귀
        player.getInventory().setHeldItemSlot(state.getWeaponSlot());
        sendMsg(player, msgReturnWeapon);
        playSound(player, sndReturnWeapon);
        return true;
    }

    // ===================================================================
    // 전투 모드 진입/해제
    // ===================================================================

    /**
     * 전투 모드 진입
     * <ol>
     *   <li>NBT로 MMOITEMS_ITEM_ID 검증</li>
     *   <li>무기를 핫바 8(키보드 9)로 이동 (0~7에 있으면 스왑)</li>
     *   <li>패시브 TIMER 스케줄러 등록</li>
     * </ol>
     */
    private boolean tryEnableCombat(Player player, CombatState state) {
        String weaponId = detectWeaponId(player);
        if (weaponId == null || !plugin.getWeaponSkillManager().hasWeapon(weaponId)) {
            return false; // 등록된 무기가 아니면 정상 F키 동작
        }

        WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(weaponId);

        // 무기를 핫바 8로 이동
        ensureWeaponAtSlot8(player, state);

        state.setCombatMode(true);
        state.setCurrentWeaponId(weaponId);
        state.setWeaponSlot(WEAPON_SLOT);
        state.clearAllCooldowns();
        player.closeInventory();

        // 패시브 TIMER 스케줄러 시작
        startPassiveTimers(player, state, weapon);

        sendMsg(player, msgCombatOn.replace("{weapon}", weapon.getDisplayName()));
        playSound(player, sndCombatOn);
        player.showTitle(Title.title(
                mm.deserialize("<gradient:red:gold>⚔ 전투 모드 ON</gradient>"),
                mm.deserialize(weapon.getDisplayName()),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(1), Duration.ofMillis(300))));
        return true;
    }

    /** 전투 모드 해제 (메시지 포함) */
    public void disableCombatMode(Player player, CombatState state) {
        restoreSlot(player, state);
        state.reset(); // 타이머 취소 + 쿨타임 클리어 + 상태 초기화

        sendMsg(player, msgCombatOff);
        playSound(player, sndCombatOff);
        player.showTitle(Title.title(
                mm.deserialize("<gray>⚔ 전투 모드 OFF</gray>"),
                mm.deserialize("<dark_gray>일반 모드로 전환</dark_gray>"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(1), Duration.ofMillis(300))));
    }

    /** 강제 해제 (사망/퇴장 — 메시지 없음) */
    public void forceDisable(Player player) {
        CombatState state = states.get(player.getUniqueId());
        if (state == null || !state.isCombatMode()) return;
        restoreSlot(player, state);
        state.reset();
    }

    // ===================================================================
    // 핫바 슬롯 8 고정 (무기 이동/복구)
    // ===================================================================

    /**
     * 무기를 핫바 인덱스 8(키보드 9)로 이동.
     * 이미 8에 있으면 스왑 불필요. 다른 슬롯에 있으면 8과 교환.
     * MMOItems NBT 데이터는 ItemStack에 보존되므로 CustomModelData 정상 출력.
     */
    private void ensureWeaponAtSlot8(Player player, CombatState state) {
        PlayerInventory inv = player.getInventory();
        int currentSlot = inv.getHeldItemSlot();

        if (currentSlot == WEAPON_SLOT) {
            // 이미 8번 슬롯 — 스왑 불필요
            state.setSwapped(false);
            return;
        }

        // 현재 슬롯의 무기와 8번 슬롯의 아이템을 교환
        ItemStack weaponItem = inv.getItem(currentSlot);
        ItemStack slotItem = inv.getItem(WEAPON_SLOT);
        inv.setItem(WEAPON_SLOT, weaponItem);
        inv.setItem(currentSlot, slotItem);
        inv.setHeldItemSlot(WEAPON_SLOT);

        state.setOriginalSlot(currentSlot);
        state.setSwapped(true);
    }

    /** 전투 모드 해제 시 무기를 원래 슬롯으로 복구 */
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
    // 패시브 TIMER 스케줄러
    // ===================================================================

    /**
     * 전투 모드 진입 시 패시브 TIMER 스케줄러를 등록한다.
     * <p>
     * 각 패시브는 {@code timer}초 간격으로 반복 실행되며,
     * 전투 모드 해제 또는 무기를 내리면 즉시 취소된다.
     * 무기를 들고 있을 때만 시전 (아이템 슬롯에서는 스킵).
     * </p>
     */
    private void startPassiveTimers(Player player, CombatState state, WeaponSkill weapon) {
        for (PassiveSlot passive : weapon.getPassives()) {
            if (!passive.isValid()) continue;

            long intervalTicks = (long) (passive.getTimer() * 20); // 초 → 틱

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                // 전투 모드 해제 or 오프라인 → 스킵 (태스크는 reset()에서 취소됨)
                if (!state.isCombatMode() || !player.isOnline()) return;

                // 무기를 들고 있을 때만 발동
                if (!state.isHoldingWeapon(player.getInventory().getHeldItemSlot())) return;

                // 패시브 시전 (쿨타임 적용)
                castPassive(player, state, passive);

            }, intervalTicks, intervalTicks);

            state.addTimerTask(task);
        }
    }

    /**
     * 패시브 스킬 시전 (TIMER에서 호출)
     * <p>쿨타임이 설정되어 있으면 확인 후 적용</p>
     */
    private void castPassive(Player player, CombatState state, PassiveSlot passive) {
        // 패시브 쿨타임 (인덱스에 100을 더해 액티브와 충돌 방지)
        int cdKey = 100 + passive.getIndex();

        if (passive.getCooldown() > 0 && state.isOnCooldown(cdKey)) return;

        boolean success = executeCast(player, passive.getType(), passive.getModifiers());

        if (success && passive.getCooldown() > 0) {
            state.setCooldown(cdKey, passive.getCooldown());
        }
    }

    // ===================================================================
    // 액티브 스킬 캐스팅
    // ===================================================================

    /** 슬롯 번호(1~6)로 액티브 스킬 시전 */
    public void castSkill(Player player, int slotNumber) {
        CombatState state = getState(player);
        if (!state.isCombatMode()) return;

        // 무기 슬롯에 무기가 아직 있는지 NBT 검증
        if (!validateWeaponInSlot(player, state)) {
            disableCombatMode(player, state);
            sendMsg(player, msgWeaponLost);
            return;
        }

        WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(state.getCurrentWeaponId());
        if (weapon == null) return;

        SkillSlot skill = weapon.getSkill(slotNumber);
        if (skill == null || !skill.isValid()) {
            sendMsg(player, msgNoSkill);
            return;
        }

        // 쿨타임 확인
        if (state.isOnCooldown(slotNumber)) {
            double rem = state.getRemainingCooldown(slotNumber);
            sendMsg(player, msgCooldown.replace("{remaining}", String.format("%.1f", rem)));
            playSound(player, sndCooldownDeny);
            return;
        }

        // ── MythicLib API 시전 ──
        // damage 계산: MythicLib ATTACK_DAMAGE × skill.damage
        double weaponDmg = getWeaponDamage(player);
        double finalDmg = weaponDmg * skill.getDamage();

        // extra 파라미터를 Map<String, Double>로 변환
        Map<String, Double> modifiers = new java.util.LinkedHashMap<>();
        modifiers.put("damage", finalDmg);
        for (var entry : skill.getExtraParams().entrySet()) {
            modifiers.put(entry.getKey(), toDouble(entry.getValue()));
        }

        boolean success = executeCast(player, skill.getMythicId(), modifiers);

        if (success) {
            if (skill.getCooldown() > 0) state.setCooldown(slotNumber, skill.getCooldown());
            sendMsg(player, msgSkillCast.replace("{skill}", skill.getDisplayName()));
            playSound(player, sndSkillCast);
        } else {
            sendMsg(player, msgCastFailed);
        }
    }

    // ===================================================================
    // MythicLib API 시전 (공통)
    // ===================================================================

    /**
     * MythicLib API로 스킬을 시전하는 공통 메서드.
     * <p>
     * 액티브 스킬과 패시브 모두 이 메서드를 통해 시전한다.
     * </p>
     *
     * <h4>MythicLib API 호출 순서 (JAR 바이트코드에서 검증 완료)</h4>
     * <ol>
     *   <li>{@code MMOPlayerData.get(UUID)} — 시전자 데이터</li>
     *   <li>{@code getOrCreateHandler(mythicId)} — 캐싱된 핸들러</li>
     *   <li>{@code new ModifiableSkill(handler)} — 시전 래퍼 생성</li>
     *   <li>{@code modSkill.registerModifier(key, value)} — 모디파이어 등록</li>
     *   <li>{@code modSkill.cast(TriggerMetadata)} → {@code SkillResult.isSuccessful()}</li>
     * </ol>
     *
     * <h4>MythicMobs YML에서 참조</h4>
     * <pre>
     *   mmodamage{amount="<modifier.damage>"} @target
     *   heal{amount="<modifier.heal>"} @self
     * </pre>
     *
     * @param player    시전자
     * @param mythicId  MythicMobs 스킬 이름
     * @param modifiers key → value 맵 (registerModifier로 등록)
     * @return true: 시전 성공
     */
    private boolean executeCast(Player player, String mythicId, Map<String, Double> modifiers) {
        try {
            // 1. 시전자 데이터
            MMOPlayerData playerData = MMOPlayerData.get(player.getUniqueId());
            if (playerData == null) {
                plugin.getLogger().warning("[CombatManager] MMOPlayerData null: " + player.getName());
                return false;
            }

            // 2. 캐싱된 핸들러
            MythicMobsSkillHandler handler = getOrCreateHandler(mythicId);

            // 3. ModifiableSkill 생성 (SimpleSkill은 모든 파라미터를 0으로 반환 → 반드시 ModifiableSkill 사용)
            ModifiableSkill modSkill = new ModifiableSkill(handler);

            // 4. 모디파이어 등록 — 대소문자 구분, 키 이름 제한 없음 (MythicLib 개발자 확인)
            for (var entry : modifiers.entrySet()) {
                modSkill.registerModifier(entry.getKey(), entry.getValue());
            }

            // 5. 시전 — 스탯 스냅샷 자동 적용, 마나/쿨타임 체크 스킵
            //    TriggerMetadata(MMOPlayerData, TriggerType, Entity) — 3인자 (JAR 검증)
            return modSkill.cast(
                    new TriggerMetadata(playerData, TriggerType.API, (Entity) null)
            ).isSuccessful();

        } catch (Exception e) {
            plugin.getLogger().warning("[CombatManager] 시전 실패 (" + mythicId + "): " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ===================================================================
    // 유틸리티
    // ===================================================================

    /** 무기 슬롯에 무기가 아직 있는지 NBT 검증 */
    private boolean validateWeaponInSlot(Player player, CombatState state) {
        int wSlot = state.getWeaponSlot();
        if (wSlot < 0) return false;
        ItemStack item = player.getInventory().getItem(wSlot);
        if (item == null || item.getType().isAir()) return false;
        try {
            NBTItem nbt = NBTItem.get(item);
            if (!nbt.hasType()) return false;
            String id = nbt.getString("MMOITEMS_ITEM_ID");
            return id != null && id.equals(state.getCurrentWeaponId());
        } catch (Exception e) { return false; }
    }

    /** 메인핸드의 MMOITEMS_ITEM_ID를 NBT로 읽기 */
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

    /** MythicLib ATTACK_DAMAGE 조회 */
    private double getWeaponDamage(Player player) {
        try {
            MMOPlayerData d = MMOPlayerData.get(player.getUniqueId());
            if (d != null) return d.getStatMap().getStat("ATTACK_DAMAGE");
        } catch (Exception ignored) {}
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        return attr != null ? attr.getValue() : 1.0;
    }

    /**
     * 메시지 전송 — 빈 문자열("")이면 전송하지 않음
     */
    private void sendMsg(Player player, String msg) {
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(mm.deserialize(msg));
        }
    }

    /**
     * 사운드 재생 — 빈 문자열("")이면 재생하지 않음
     * <p>Minecraft 리소스 키(점 포함)와 레거시 Bukkit Enum 이름 모두 지원</p>
     */
    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isEmpty()) return;
        try {
            if (soundName.contains(".")) {
                // Minecraft 리소스 키: entity.ender_dragon.growl
                player.playSound(player.getLocation(), soundName, 0.8f, 1.0f);
            } else {
                // Registry 조회 시도
                String key = soundName.toLowerCase(java.util.Locale.ROOT);
                NamespacedKey nsKey = NamespacedKey.minecraft(key);
                Sound sound = Registry.SOUNDS.get(nsKey);
                if (sound != null) player.playSound(player.getLocation(), sound, 0.8f, 1.0f);
                else player.playSound(player.getLocation(), key, 0.8f, 1.0f);
            }
        } catch (Exception ignored) {}
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) { try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {} }
        return 0.0;
    }
}
