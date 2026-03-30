package kr.yongpyo.bsskill.gui;

import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.model.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 3단계 GUI 에디터 (InventoryHolder 패턴)
 */
public class GUIManager {

    private final BSSkill plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public GUIManager(BSSkill plugin) { this.plugin = plugin; }

    // ── 1단계: 무기 목록 ──

    public void openWeaponListGUI(Player player) {
        var holder = new WeaponListHolder();
        Inventory gui = Bukkit.createInventory(holder, 54,
                mm.deserialize("<gradient:gold:yellow>⚔ BSSkill — 무기 목록</gradient>"));
        holder.setInventory(gui);

        int idx = 0;
        for (WeaponSkill w : plugin.getWeaponSkillManager().getAllWeapons()) {
            if (idx >= 45) break;
            gui.setItem(idx, buildItem(Material.DIAMOND_SWORD, w.getDisplayName(), List.of(
                    "<dark_gray>────────────────────</dark_gray>",
                    "<gray>ID: <white>" + w.getWeaponId() + "</white></gray>",
                    "<gray>스킬: <aqua>" + w.getActiveSkillCount() + "/" + WeaponSkill.MAX_SLOTS + "</aqua></gray>",
                    "<gray>패시브: <gold>" + w.getPassives().size() + "개</gold></gray>",
                    "<dark_gray>────────────────────</dark_gray>",
                    "<yellow>클릭</yellow><gray> → 편집</gray>")));
            holder.mapSlot(idx, w.getWeaponId());
            idx++;
        }
        player.openInventory(gui);
    }

    // ── 2단계: 슬롯 목록 ──

    public void openSlotListGUI(Player player, WeaponSkill weapon) {
        var holder = new SlotListHolder(weapon.getWeaponId());
        Inventory gui = Bukkit.createInventory(holder, 36,
                mm.deserialize("<gradient:aqua:blue>⚔ " + weapon.getDisplayName() + "</gradient>"));
        holder.setInventory(gui);

        gui.setItem(4, buildItem(Material.BOOK, weapon.getDisplayName(), List.of(
                "<gray>" + weapon.getWeaponId() + "</gray>")));

        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            SkillSlot s = weapon.getSkill(i);
            Material mat;
            try { mat = Material.valueOf(s.getIcon().toUpperCase()); } catch (Exception e) { mat = Material.BARRIER; }
            String status = s.isValid() ? "<green>●</green>" : "<red>●</red>";
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>────────────────────</dark_gray>");
            if (s.isValid()) {
                lore.add("<gray>스킬: <aqua>" + s.getMythicId() + "</aqua></gray>");
                lore.add("<gray>데미지: <red>" + s.getDamage() + "</red> | 쿨타임: <yellow>" + s.getCooldown() + "s</yellow></gray>");
            } else { lore.add("<dark_red>미설정</dark_red>"); }
            lore.add("<dark_gray>────────────────────</dark_gray>");
            lore.add("<yellow>클릭</yellow><gray> → 편집</gray>");
            gui.setItem(9 + i, buildItem(mat,
                    status + " <white>슬롯 " + i + " [" + s.getKeybindLabel() + "]</white> " + s.getDisplayName(), lore));
        }

        // 패시브 표시 (슬롯 22~)
        int pIdx = 22;
        for (PassiveSlot p : weapon.getPassives()) {
            if (pIdx > 25) break;
            gui.setItem(pIdx++, buildItem(Material.ENDER_EYE,
                    "<gold>[패시브] " + p.getType() + "</gold>",
                    List.of("<gray>주기: <yellow>" + p.getTimer() + "s</yellow></gray>",
                            "<gray>모디파이어: <white>" + p.getModifiers() + "</white></gray>")));
        }

        gui.setItem(27, buildItem(Material.ARROW, "<gray>← 뒤로</gray>", List.of()));
        player.openInventory(gui);
    }

    // ── 3단계: 슬롯 편집 ──

    public void openSlotEditGUI(Player player, WeaponSkill weapon, int slotNumber) {
        SkillSlot s = weapon.getSkill(slotNumber);
        if (s == null) return;

        var holder = new SlotEditHolder(weapon.getWeaponId(), slotNumber);
        Inventory gui = Bukkit.createInventory(holder, 36,
                mm.deserialize("<gradient:green:aqua>✎ 슬롯 " + slotNumber + " [" + s.getKeybindLabel() + "]</gradient>"));
        holder.setInventory(gui);

        gui.setItem(10, buildItem(s.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                s.isEnabled() ? "<green>✔ ON</green>" : "<red>✖ OFF</red>", List.of("<yellow>클릭 토글</yellow>")));
        gui.setItem(12, buildItem(Material.NAME_TAG,
                "<aqua>ID: <white>" + s.getMythicId() + "</white></aqua>", List.of("<yellow>클릭 → 채팅 입력</yellow>")));
        gui.setItem(14, buildItem(Material.WRITABLE_BOOK,
                "<gold>이름: </gold>" + s.getDisplayName(), List.of("<yellow>클릭 → 채팅 입력</yellow>")));
        gui.setItem(16, buildItem(Material.IRON_SWORD,
                "<red>데미지: <white>" + s.getDamage() + "</white></red>",
                List.of("<yellow>좌:-1 우:+1 Shift좌:-5 Shift우:+5</yellow>")));
        gui.setItem(19, buildItem(Material.CLOCK,
                "<yellow>쿨타임: <white>" + s.getCooldown() + "s</white></yellow>",
                List.of("<yellow>좌:-0.5 우:+0.5 Shift좌:-5 Shift우:+5</yellow>")));
        gui.setItem(21, buildItem(Material.PAPER,
                "<gray>설명: <white>" + (s.getDescription().isEmpty() ? "없음" : s.getDescription()) + "</white></gray>",
                List.of("<yellow>클릭 → 채팅 입력</yellow>")));
        gui.setItem(23, buildItem(Material.PAINTING,
                "<gold>아이콘: <white>" + s.getIcon() + "</white></gold>",
                List.of("<yellow>클릭 → 채팅 입력</yellow>")));
        gui.setItem(27, buildItem(Material.ARROW, "<gray>← 뒤로</gray>", List.of()));
        gui.setItem(31, buildItem(Material.EMERALD, "<green>✔ 자동 저장</green>", List.of()));

        player.openInventory(gui);
    }

    // ── 아이템 빌더 ──

    private ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(name).decoration(TextDecoration.ITALIC, false));
        if (lore != null && !lore.isEmpty()) {
            List<Component> comps = new ArrayList<>();
            for (String l : lore) comps.add(mm.deserialize(l).decoration(TextDecoration.ITALIC, false));
            meta.lore(comps);
        }
        item.setItemMeta(meta);
        return item;
    }

    // ── InventoryHolder ──

    public static class WeaponListHolder implements InventoryHolder {
        private Inventory inv;
        private final Map<Integer, String> map = new HashMap<>();
        @Override public Inventory getInventory() { return inv; }
        public void setInventory(Inventory v) { inv = v; }
        public void mapSlot(int s, String id) { map.put(s, id); }
        public String getWeaponId(int s) { return map.get(s); }
    }

    public static class SlotListHolder implements InventoryHolder {
        private Inventory inv;
        private final String weaponId;
        public SlotListHolder(String id) { weaponId = id; }
        @Override public Inventory getInventory() { return inv; }
        public void setInventory(Inventory v) { inv = v; }
        public String getWeaponId() { return weaponId; }
    }

    public static class SlotEditHolder implements InventoryHolder {
        private Inventory inv;
        private final String weaponId;
        private final int slotNumber;
        public SlotEditHolder(String id, int s) { weaponId = id; slotNumber = s; }
        @Override public Inventory getInventory() { return inv; }
        public void setInventory(Inventory v) { inv = v; }
        public String getWeaponId() { return weaponId; }
        public int getSlotNumber() { return slotNumber; }
    }
}
