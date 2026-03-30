package kr.yongpyo.bsskill.listener;

import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.gui.GUIManager.*;
import kr.yongpyo.bsskill.model.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI 에디터 이벤트 핸들러
 * 아이템 유출/복사 방지: shift-click, number-key, drag 전부 차단
 * 패시브 편집 GUI 포함 (액티브와 동일 디자인)
 */
public class GUIListener implements Listener {

    private final BSSkill plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, InputCtx> pending = new ConcurrentHashMap<>();

    public GUIListener(BSSkill plugin) { this.plugin = plugin; }

    // ===================================================================
    // 아이템 유출 방지 — drag 이벤트
    // ===================================================================

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isBSSkillGUI(event.getInventory())) event.setCancelled(true);
    }

    private boolean isBSSkillGUI(org.bukkit.inventory.Inventory inv) {
        var h = inv.getHolder();
        return h instanceof WeaponListHolder || h instanceof WeaponDetailHolder
                || h instanceof SkillEditHolder || h instanceof PassiveEditHolder;
    }

    // ===================================================================
    // 클릭 라우터 — shift-click, number-key 차단
    // ===================================================================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        var holder = event.getInventory().getHolder();

        // BSSkill GUI가 아니면 무시
        if (!isBSSkillGUI(event.getInventory())) return;

        // 아이템 유출 방지: shift-click, number-key, 하단 인벤토리 클릭 전부 차단
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) return; // 하단 클릭 무시

        if (holder instanceof WeaponListHolder h) handleWeaponList(p, h, event);
        else if (holder instanceof WeaponDetailHolder h) handleWeaponDetail(p, h, event);
        else if (holder instanceof SkillEditHolder h) handleSkillEdit(p, h, event);
        else if (holder instanceof PassiveEditHolder h) handlePassiveEdit(p, h, event);
    }

    // ===================================================================
    // 1단계: 무기 목록
    // ===================================================================

    private void handleWeaponList(Player p, WeaponListHolder h, InventoryClickEvent e) {
        String id = h.getWeaponId(e.getSlot());
        if (id == null) return;
        WeaponSkill w = plugin.getWeaponSkillManager().getWeapon(id);
        if (w != null) { plugin.getGUIManager().openWeaponDetailGUI(p, w); click(p); }
    }

    // ===================================================================
    // 2단계: 무기 상세
    // ===================================================================

    private void handleWeaponDetail(Player p, WeaponDetailHolder h, InventoryClickEvent e) {
        int slot = e.getSlot();

        // 뒤로 (45)
        if (slot == 45) { plugin.getGUIManager().openWeaponListGUI(p); click(p); return; }

        // 액티브 스킬 (19~24)
        if (slot >= 19 && slot <= 24) {
            int skillSlot = slot - 18; // 19→1, 24→6
            WeaponSkill w = plugin.getWeaponSkillManager().getWeapon(h.getWeaponId());
            if (w != null) { plugin.getGUIManager().openSkillEditGUI(p, w, skillSlot); click(p); }
            return;
        }

        // 패시브 (37~43)
        Integer passiveIdx = h.getPassiveIndex(slot);
        if (passiveIdx != null) {
            WeaponSkill w = plugin.getWeaponSkillManager().getWeapon(h.getWeaponId());
            if (w != null) { plugin.getGUIManager().openPassiveEditGUI(p, w, passiveIdx); click(p); }
        }
    }

    // ===================================================================
    // 3단계: 액티브 편집
    // ===================================================================

    private void handleSkillEdit(Player p, SkillEditHolder h, InventoryClickEvent e) {
        WeaponSkill w = plugin.getWeaponSkillManager().getWeapon(h.getWeaponId());
        if (w == null) return;
        SkillSlot s = w.getSkill(h.getSlotNumber());
        if (s == null) return;
        ClickType c = e.getClick();

        switch (e.getSlot()) {
            case 10 -> { s.setEnabled(!s.isEnabled()); saveRefreshSkill(p, w, h.getSlotNumber()); }
            case 11 -> { p.closeInventory(); startInput(p, InputType.SKILL_ID, h.getWeaponId(), h.getSlotNumber(), -1, null); prompt(p, "MythicMobs 스킬 ID"); }
            case 12 -> { p.closeInventory(); startInput(p, InputType.SKILL_NAME, h.getWeaponId(), h.getSlotNumber(), -1, null); prompt(p, "표시 이름 (MiniMessage)"); }
            case 14 -> { double d = delta(c, 1, 5); if (d != 0) { s.setDamage(s.getDamage() + d); saveRefreshSkill(p, w, h.getSlotNumber()); } }
            case 15 -> { double d = delta(c, 0.5, 5); if (d != 0) { s.setCooldown(s.getCooldown() + d); saveRefreshSkill(p, w, h.getSlotNumber()); } }
            case 16 -> { p.closeInventory(); startInput(p, InputType.SKILL_DESC, h.getWeaponId(), h.getSlotNumber(), -1, null); prompt(p, "스킬 설명"); }
            case 27 -> { plugin.getGUIManager().openWeaponDetailGUI(p, w); click(p); }
        }
    }

    // ===================================================================
    // 4단계: 패시브 편집
    // ===================================================================

    private void handlePassiveEdit(Player p, PassiveEditHolder h, InventoryClickEvent e) {
        WeaponSkill w = plugin.getWeaponSkillManager().getWeapon(h.getWeaponId());
        if (w == null) return;
        if (h.getPassiveIndex() >= w.getPassives().size()) return;
        PassiveSlot ps = w.getPassives().get(h.getPassiveIndex());
        ClickType c = e.getClick();

        switch (e.getSlot()) {
            case 10 -> { ps.setEnabled(!ps.isEnabled()); saveRefreshPassive(p, w, h.getPassiveIndex()); }
            case 11 -> { p.closeInventory(); startInput(p, InputType.PASSIVE_TYPE, h.getWeaponId(), -1, h.getPassiveIndex(), null); prompt(p, "MythicMobs 스킬 ID"); }
            case 12 -> { p.closeInventory(); startInput(p, InputType.PASSIVE_NAME, h.getWeaponId(), -1, h.getPassiveIndex(), null); prompt(p, "표시 이름 (MiniMessage)"); }
            case 14 -> { double d = delta(c, 0.5, 5); if (d != 0) { ps.setTimer(ps.getTimer() + d); saveRefreshPassive(p, w, h.getPassiveIndex()); } }
            case 15 -> { double d = delta(c, 0.5, 5); if (d != 0) { ps.setCooldown(ps.getCooldown() + d); saveRefreshPassive(p, w, h.getPassiveIndex()); } }
            case 16 -> { p.closeInventory(); startInput(p, InputType.PASSIVE_DESC, h.getWeaponId(), -1, h.getPassiveIndex(), null); prompt(p, "설명"); }
            case 27 -> { plugin.getGUIManager().openWeaponDetailGUI(p, w); click(p); }
            default -> {
                // 모디파이어 수치 조정 (슬롯 20~25)
                String modKey = h.getModifierKey(e.getSlot());
                if (modKey != null) {
                    double d = delta(c, 1, 5);
                    if (d != 0) {
                        double curr = ps.getModifiers().getOrDefault(modKey, 0.0);
                        ps.putModifier(modKey, Math.max(0, curr + d));
                        saveRefreshPassive(p, w, h.getPassiveIndex());
                    }
                }
            }
        }
    }

    // ===================================================================
    // 채팅 입력
    // ===================================================================

    private void startInput(Player p, InputType type, String wId, int slot, int passive, String modKey) {
        pending.put(p.getUniqueId(), new InputCtx(type, wId, slot, passive, modKey));
    }

    private void prompt(Player p, String label) {
        p.sendMessage(mm.deserialize("<gray>" + label + "을 입력하세요. (cancel = 취소)</gray>"));
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        InputCtx ctx = pending.remove(p.getUniqueId());
        if (ctx == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();

        if ("cancel".equalsIgnoreCase(input)) {
            p.sendMessage(mm.deserialize("<gray>취소됨</gray>"));
            Bukkit.getScheduler().runTask(plugin, () -> reopen(p, ctx));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            WeaponSkill w = plugin.getWeaponSkillManager().getWeapon(ctx.weaponId);
            if (w == null) return;

            switch (ctx.type) {
                case SKILL_ID -> { SkillSlot s = w.getSkill(ctx.slot); if (s != null) s.setMythicId(input.toUpperCase()); }
                case SKILL_NAME -> { SkillSlot s = w.getSkill(ctx.slot); if (s != null) s.setDisplayName(input); }
                case SKILL_DESC -> { SkillSlot s = w.getSkill(ctx.slot); if (s != null) s.setDescription(input); }
                case PASSIVE_TYPE -> { if (ctx.passive < w.getPassives().size()) w.getPassives().get(ctx.passive).setType(input.toUpperCase()); }
                case PASSIVE_NAME -> { if (ctx.passive < w.getPassives().size()) w.getPassives().get(ctx.passive).setDisplayName(input); }
                case PASSIVE_DESC -> { if (ctx.passive < w.getPassives().size()) w.getPassives().get(ctx.passive).setDescription(input); }
            }
            plugin.getWeaponSkillManager().save(w);
            p.sendMessage(mm.deserialize("<green>저장 완료</green>"));
            reopen(p, ctx);
        });
    }

    private void reopen(Player p, InputCtx ctx) {
        WeaponSkill w = plugin.getWeaponSkillManager().getWeapon(ctx.weaponId);
        if (w == null) return;
        if (ctx.slot > 0) plugin.getGUIManager().openSkillEditGUI(p, w, ctx.slot);
        else if (ctx.passive >= 0) plugin.getGUIManager().openPassiveEditGUI(p, w, ctx.passive);
        else plugin.getGUIManager().openWeaponDetailGUI(p, w);
    }

    // ===================================================================
    // 유틸
    // ===================================================================

    private void saveRefreshSkill(Player p, WeaponSkill w, int slot) {
        plugin.getWeaponSkillManager().save(w);
        plugin.getGUIManager().openSkillEditGUI(p, w, slot);
        click(p);
    }

    private void saveRefreshPassive(Player p, WeaponSkill w, int passiveIdx) {
        plugin.getWeaponSkillManager().save(w);
        plugin.getGUIManager().openPassiveEditGUI(p, w, passiveIdx);
        click(p);
    }

    private double delta(ClickType c, double sm, double lg) {
        return switch (c) { case LEFT -> -sm; case RIGHT -> sm; case SHIFT_LEFT -> -lg; case SHIFT_RIGHT -> lg; default -> 0; };
    }

    private void click(Player p) { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f); }

    private enum InputType { SKILL_ID, SKILL_NAME, SKILL_DESC, PASSIVE_TYPE, PASSIVE_NAME, PASSIVE_DESC }
    private record InputCtx(InputType type, String weaponId, int slot, int passive, String modKey) {}
}
