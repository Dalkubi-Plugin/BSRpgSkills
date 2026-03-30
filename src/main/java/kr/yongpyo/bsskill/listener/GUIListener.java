package kr.yongpyo.bsskill.listener;

import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.gui.GUIManager.*;
import kr.yongpyo.bsskill.model.SkillSlot;
import kr.yongpyo.bsskill.model.WeaponSkill;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GUIListener implements Listener {

    private final BSSkill plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, InputCtx> pending = new ConcurrentHashMap<>();

    public GUIListener(BSSkill plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getClickedInventory() == null) return;
        var holder = event.getInventory().getHolder();

        if (holder instanceof WeaponListHolder wlh) { event.setCancelled(true); handleWeaponList(p, wlh, event); }
        else if (holder instanceof SlotListHolder slh) { event.setCancelled(true); handleSlotList(p, slh, event); }
        else if (holder instanceof SlotEditHolder seh) { event.setCancelled(true); handleSlotEdit(p, seh, event); }
    }

    private void handleWeaponList(Player p, WeaponListHolder h, InventoryClickEvent e) {
        String id = h.getWeaponId(e.getSlot());
        if (id == null) return;
        WeaponSkill w = plugin.getWeaponSkillManager().getWeapon(id);
        if (w != null) { plugin.getGUIManager().openSlotListGUI(p, w); click(p); }
    }

    private void handleSlotList(Player p, SlotListHolder h, InventoryClickEvent e) {
        if (e.getSlot() == 27) { plugin.getGUIManager().openWeaponListGUI(p); click(p); return; }
        if (e.getSlot() >= 10 && e.getSlot() <= 15) {
            WeaponSkill w = plugin.getWeaponSkillManager().getWeapon(h.getWeaponId());
            if (w != null) { plugin.getGUIManager().openSlotEditGUI(p, w, e.getSlot() - 9); click(p); }
        }
    }

    private void handleSlotEdit(Player p, SlotEditHolder h, InventoryClickEvent e) {
        WeaponSkill w = plugin.getWeaponSkillManager().getWeapon(h.getWeaponId());
        if (w == null) return;
        SkillSlot s = w.getSkill(h.getSlotNumber());
        if (s == null) return;
        ClickType c = e.getClick();

        switch (e.getSlot()) {
            case 10 -> { s.setEnabled(!s.isEnabled()); saveRefresh(p, w, h.getSlotNumber()); click(p); }
            case 12 -> { p.closeInventory(); startInput(p, Field.ID, h); prompt(p, "MythicMobs 스킬 ID"); }
            case 14 -> { p.closeInventory(); startInput(p, Field.NAME, h); prompt(p, "표시 이름 (MiniMessage)"); }
            case 16 -> { double d = delta(c, 1, 5); if (d != 0) { s.setDamage(s.getDamage() + d); saveRefresh(p, w, h.getSlotNumber()); } }
            case 19 -> { double d = delta(c, 0.5, 5); if (d != 0) { s.setCooldown(s.getCooldown() + d); saveRefresh(p, w, h.getSlotNumber()); } }
            case 21 -> { p.closeInventory(); startInput(p, Field.DESC, h); prompt(p, "스킬 설명"); }
            case 23 -> { p.closeInventory(); startInput(p, Field.ICON, h); prompt(p, "아이콘 Material 이름"); }
            case 27 -> { plugin.getGUIManager().openSlotListGUI(p, w); click(p); }
        }
    }

    // ── 채팅 입력 ──

    private void startInput(Player p, Field f, SlotEditHolder h) {
        pending.put(p.getUniqueId(), new InputCtx(f, h.getWeaponId(), h.getSlotNumber()));
    }

    private void prompt(Player p, String label) {
        p.sendMessage(mm.deserialize("<gradient:aqua:blue>✎ " + label + "을 입력하세요.</gradient>"));
        p.sendMessage(mm.deserialize("<red>취소: 'cancel'</red>"));
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
            SkillSlot s = w.getSkill(ctx.slot);
            if (s == null) return;
            switch (ctx.field) {
                case ID -> s.setMythicId(input.toUpperCase());
                case NAME -> s.setDisplayName(input);
                case DESC -> s.setDescription(input);
                case ICON -> { try { Material.valueOf(input.toUpperCase()); s.setIcon(input.toUpperCase()); } catch (Exception ex) { p.sendMessage(mm.deserialize("<red>잘못된 Material</red>")); reopen(p, ctx); return; } }
            }
            plugin.getWeaponSkillManager().save(w);
            p.sendMessage(mm.deserialize("<green>✔ 저장 완료</green>"));
            reopen(p, ctx);
        });
    }

    private void reopen(Player p, InputCtx c) {
        WeaponSkill w = plugin.getWeaponSkillManager().getWeapon(c.weaponId);
        if (w != null) plugin.getGUIManager().openSlotEditGUI(p, w, c.slot);
    }

    private void saveRefresh(Player p, WeaponSkill w, int s) {
        plugin.getWeaponSkillManager().save(w);
        plugin.getGUIManager().openSlotEditGUI(p, w, s);
    }

    private double delta(ClickType c, double sm, double lg) {
        return switch (c) { case LEFT -> -sm; case RIGHT -> sm; case SHIFT_LEFT -> -lg; case SHIFT_RIGHT -> lg; default -> 0; };
    }

    private void click(Player p) { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f); }

    private enum Field { ID, NAME, DESC, ICON }
    private record InputCtx(Field field, String weaponId, int slot) {}
}
