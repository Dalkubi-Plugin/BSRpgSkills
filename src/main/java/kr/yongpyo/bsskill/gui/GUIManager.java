package kr.yongpyo.bsskill.gui;

import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.model.*;
import net.Indyuce.mmoitems.MMOItems;
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
 * 4단계 GUI 에디터 (이모지 없음, 텍스트 기반)
 *
 * 1단계: 무기 목록 (MMOItems 실제 아이템 렌더링)
 * 2단계: 무기 상세 (액티브 6 + 패시브, CMD 아이콘)
 * 3단계: 액티브 스킬 편집
 * 4단계: 패시브 스킬 편집 (액티브와 동일 디자인)
 *
 * 아이템 유출/복사 방지: InventoryClickEvent에서 shift-click, drag 전체 차단
 */
public class GUIManager {

    private final BSSkill plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public GUIManager(BSSkill plugin) { this.plugin = plugin; }

    // ===================================================================
    // 1단계: 무기 목록 — MMOItems 실제 아이템 렌더링
    // ===================================================================

    public void openWeaponListGUI(Player player) {
        var holder = new WeaponListHolder();
        Inventory gui = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>BSSkill - 무기 목록</dark_gray>"));
        holder.setInventory(gui);

        int idx = 0;
        for (WeaponSkill w : plugin.getWeaponSkillManager().getAllWeapons()) {
            if (idx >= 45) break;

            // MMOItems API로 실제 아이템 생성 시도 (리소스팩 모델 반영)
            ItemStack icon = buildMMOItemIcon(w);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(text(w.getDisplayName()));
            List<Component> lore = new ArrayList<>();
            lore.add(text("<dark_gray>──────────────────</dark_gray>"));
            lore.add(text("<gray>ID: <white>" + w.getWeaponId() + "</white></gray>"));
            lore.add(text("<gray>타입: <white>" + w.getMmoType() + "</white></gray>"));
            lore.add(text("<gray>스킬: <white>" + w.getActiveSkillCount() + "/" + WeaponSkill.MAX_SLOTS + "</white></gray>"));
            lore.add(text("<gray>패시브: <white>" + w.getPassives().size() + "개</white></gray>"));
            lore.add(text("<dark_gray>──────────────────</dark_gray>"));
            lore.add(text("<yellow>클릭하여 편집</yellow>"));
            meta.lore(lore);
            icon.setItemMeta(meta);

            gui.setItem(idx, icon);
            holder.mapSlot(idx, w.getWeaponId());
            idx++;
        }
        player.openInventory(gui);
    }

    /**
     * MMOItems API로 실제 아이템 생성 (CustomModelData, Material 유지)
     * 실패 시 DIAMOND_SWORD 폴백
     */
    private ItemStack buildMMOItemIcon(WeaponSkill w) {
        try {
            var type = MMOItems.plugin.getTypes().get(w.getMmoType());
            if (type != null) {
                ItemStack mmoItem = MMOItems.plugin.getItem(type, w.getWeaponId());
                if (mmoItem != null) return mmoItem.clone();
            }
        } catch (Exception e) {
            plugin.getLogger().fine("[GUI] MMOItems 아이템 로드 실패: " + w.getWeaponId());
        }
        return new ItemStack(Material.DIAMOND_SWORD);
    }

    // ===================================================================
    // 2단계: 무기 상세 — 액티브 6슬롯 + 패시브 목록
    // ===================================================================

    public void openWeaponDetailGUI(Player player, WeaponSkill w) {
        var holder = new WeaponDetailHolder(w.getWeaponId());
        Inventory gui = Bukkit.createInventory(holder, 54,
                mm.deserialize("<dark_gray>BSSkill - " + w.getWeaponId() + "</dark_gray>"));
        holder.setInventory(gui);

        // 상단: 무기 정보 (슬롯 4)
        gui.setItem(4, buildItem(Material.BOOK, w.getDisplayName(), List.of(
                "<gray>" + w.getMmoType() + " : " + w.getWeaponId() + "</gray>")));

        // 액티브 스킬 (슬롯 19~24 — 3열 중앙)
        gui.setItem(18, buildItem(Material.IRON_SWORD, "<white>액티브 스킬</white>", List.of()));
        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            SkillSlot s = w.getSkill(i);
            gui.setItem(18 + i, buildSkillIcon(s));
        }

        // 패시브 (슬롯 37~)
        gui.setItem(36, buildItem(Material.ENDER_EYE, "<white>패시브 스킬</white>", List.of()));
        int pIdx = 37;
        for (int i = 0; i < w.getPassives().size() && pIdx <= 43; i++) {
            PassiveSlot p = w.getPassives().get(i);
            gui.setItem(pIdx, buildPassiveIcon(p));
            holder.mapPassiveSlot(pIdx, i);
            pIdx++;
        }

        // 하단: 뒤로 가기 (슬롯 45)
        gui.setItem(45, buildItem(Material.ARROW, "<gray>뒤로</gray>", List.of()));
        player.openInventory(gui);
    }

    private ItemStack buildSkillIcon(SkillSlot s) {
        ItemStack icon = buildIconWithCMD(s.getIcon(), s.getCustomModelData());
        ItemMeta meta = icon.getItemMeta();

        String status = s.isValid() ? "<green>ON</green>" : "<red>OFF</red>";
        meta.displayName(text("[" + status + "<white>] 슬롯 " + s.getSlot()
                + " (" + s.getKeybindLabel() + ") </white>" + s.getDisplayName()));

        List<Component> lore = new ArrayList<>();
        lore.add(text("<dark_gray>──────────────────</dark_gray>"));
        if (s.isValid()) {
            lore.add(text("<gray>스킬 ID: <white>" + s.getMythicId() + "</white></gray>"));
            lore.add(text("<gray>데미지 배수: <white>" + s.getDamage() + "</white></gray>"));
            lore.add(text("<gray>쿨타임: <white>" + s.getCooldown() + "초</white></gray>"));
            if (!s.getExtraParams().isEmpty())
                lore.add(text("<gray>추가 변수: <white>" + s.getExtraParams().keySet() + "</white></gray>"));
            if (!s.getDescription().isEmpty()) {
                lore.add(text("<dark_gray>──────────────────</dark_gray>"));
                lore.add(text("<gray>" + s.getDescription() + "</gray>"));
            }
        } else {
            lore.add(text("<dark_gray>스킬 미설정</dark_gray>"));
        }
        lore.add(text("<dark_gray>──────────────────</dark_gray>"));
        lore.add(text("<yellow>클릭하여 편집</yellow>"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack buildPassiveIcon(PassiveSlot p) {
        ItemStack icon = buildIconWithCMD(p.getIcon(), p.getCustomModelData());
        ItemMeta meta = icon.getItemMeta();

        String status = p.isEnabled() ? "<green>ON</green>" : "<red>OFF</red>";
        meta.displayName(text("[" + status + "<white>] 패시브 </white>" + p.getDisplayName()));

        List<Component> lore = new ArrayList<>();
        lore.add(text("<dark_gray>──────────────────</dark_gray>"));
        lore.add(text("<gray>스킬 ID: <white>" + p.getType() + "</white></gray>"));
        lore.add(text("<gray>발동 주기: <white>" + p.getTimer() + "초</white></gray>"));
        if (p.getCooldown() > 0)
            lore.add(text("<gray>쿨타임: <white>" + p.getCooldown() + "초</white></gray>"));
        if (!p.getModifiers().isEmpty()) {
            lore.add(text("<gray>모디파이어:</gray>"));
            for (var e : p.getModifiers().entrySet())
                lore.add(text("  <gray>- " + e.getKey() + ": <white>" + e.getValue() + "</white></gray>"));
        }
        if (!p.getDescription().isEmpty()) {
            lore.add(text("<dark_gray>──────────────────</dark_gray>"));
            lore.add(text("<gray>" + p.getDescription() + "</gray>"));
        }
        lore.add(text("<dark_gray>──────────────────</dark_gray>"));
        lore.add(text("<yellow>클릭하여 편집</yellow>"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    // ===================================================================
    // 3단계: 액티브 스킬 편집 (통합 디자인)
    // ===================================================================

    public void openSkillEditGUI(Player player, WeaponSkill w, int slotNumber) {
        SkillSlot s = w.getSkill(slotNumber);
        if (s == null) return;

        var holder = new SkillEditHolder(w.getWeaponId(), slotNumber);
        Inventory gui = Bukkit.createInventory(holder, 36,
                mm.deserialize("<dark_gray>액티브 편집 - 슬롯 " + slotNumber + " (" + s.getKeybindLabel() + ")</dark_gray>"));
        holder.setInventory(gui);

        // 통합 편집 레이아웃
        gui.setItem(10, buildItem(s.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                s.isEnabled() ? "<green>활성화</green>" : "<red>비활성화</red>", List.of("<yellow>클릭 토글</yellow>")));
        gui.setItem(11, buildItem(Material.NAME_TAG,
                "<white>스킬 ID: " + s.getMythicId() + "</white>", List.of("<yellow>클릭 - 채팅 입력</yellow>")));
        gui.setItem(12, buildItem(Material.WRITABLE_BOOK,
                "<white>이름: </white>" + s.getDisplayName(), List.of("<yellow>클릭 - 채팅 입력</yellow>")));
        gui.setItem(14, buildItem(Material.IRON_SWORD,
                "<white>데미지: " + s.getDamage() + "</white>",
                List.of("<yellow>좌:-1  우:+1  Shift좌:-5  Shift우:+5</yellow>")));
        gui.setItem(15, buildItem(Material.CLOCK,
                "<white>쿨타임: " + s.getCooldown() + "초</white>",
                List.of("<yellow>좌:-0.5  우:+0.5  Shift좌:-5  Shift우:+5</yellow>")));
        gui.setItem(16, buildItem(Material.PAPER,
                "<white>설명: " + (s.getDescription().isEmpty() ? "없음" : s.getDescription()) + "</white>",
                List.of("<yellow>클릭 - 채팅 입력</yellow>")));

        gui.setItem(27, buildItem(Material.ARROW, "<gray>뒤로</gray>", List.of()));
        gui.setItem(31, buildItem(Material.EMERALD, "<green>자동 저장</green>", List.of()));

        player.openInventory(gui);
    }

    // ===================================================================
    // 4단계: 패시브 편집 (액티브와 동일 디자인 언어)
    // ===================================================================

    public void openPassiveEditGUI(Player player, WeaponSkill w, int passiveIndex) {
        if (passiveIndex < 0 || passiveIndex >= w.getPassives().size()) return;
        PassiveSlot p = w.getPassives().get(passiveIndex);

        var holder = new PassiveEditHolder(w.getWeaponId(), passiveIndex);
        Inventory gui = Bukkit.createInventory(holder, 36,
                mm.deserialize("<dark_gray>패시브 편집 - " + p.getType() + "</dark_gray>"));
        holder.setInventory(gui);

        gui.setItem(10, buildItem(p.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                p.isEnabled() ? "<green>활성화</green>" : "<red>비활성화</red>", List.of("<yellow>클릭 토글</yellow>")));
        gui.setItem(11, buildItem(Material.NAME_TAG,
                "<white>스킬 ID: " + p.getType() + "</white>", List.of("<yellow>클릭 - 채팅 입력</yellow>")));
        gui.setItem(12, buildItem(Material.WRITABLE_BOOK,
                "<white>이름: </white>" + p.getDisplayName(), List.of("<yellow>클릭 - 채팅 입력</yellow>")));
        gui.setItem(14, buildItem(Material.REPEATER,
                "<white>타이머: " + p.getTimer() + "초</white>",
                List.of("<yellow>좌:-0.5  우:+0.5  Shift좌:-5  Shift우:+5</yellow>")));
        gui.setItem(15, buildItem(Material.CLOCK,
                "<white>쿨타임: " + p.getCooldown() + "초</white>",
                List.of("<yellow>좌:-0.5  우:+0.5  Shift좌:-5  Shift우:+5</yellow>")));
        gui.setItem(16, buildItem(Material.PAPER,
                "<white>설명: " + (p.getDescription().isEmpty() ? "없음" : p.getDescription()) + "</white>",
                List.of("<yellow>클릭 - 채팅 입력</yellow>")));

        // 모디파이어 표시 (20~25)
        int mIdx = 20;
        for (var e : p.getModifiers().entrySet()) {
            if (mIdx > 25) break;
            gui.setItem(mIdx, buildItem(Material.PAPER,
                    "<white>" + e.getKey() + ": " + e.getValue() + "</white>",
                    List.of("<yellow>좌:-1  우:+1  Shift좌:-5  Shift우:+5</yellow>")));
            holder.mapModifierSlot(mIdx, e.getKey());
            mIdx++;
        }

        gui.setItem(27, buildItem(Material.ARROW, "<gray>뒤로</gray>", List.of()));
        gui.setItem(31, buildItem(Material.EMERALD, "<green>자동 저장</green>", List.of()));

        player.openInventory(gui);
    }

    // ===================================================================
    // 아이템 빌더
    // ===================================================================

    /** CustomModelData 적용 아이콘 생성 */
    private ItemStack buildIconWithCMD(String materialName, int cmd) {
        Material mat;
        try { mat = Material.valueOf(materialName.toUpperCase()); } catch (Exception e) { mat = Material.BARRIER; }
        ItemStack item = new ItemStack(mat);
        if (cmd > 0) {
            ItemMeta meta = item.getItemMeta();
            meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> comps = new ArrayList<>();
            for (String l : lore) comps.add(text(l));
            meta.lore(comps);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** MiniMessage 파싱 + 이탤릭 제거 */
    private Component text(String s) {
        return mm.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    // ===================================================================
    // InventoryHolder (GUI 식별)
    // ===================================================================

    public static class WeaponListHolder implements InventoryHolder {
        private Inventory inv;
        private final Map<Integer, String> map = new HashMap<>();
        @Override public Inventory getInventory() { return inv; }
        public void setInventory(Inventory v) { inv = v; }
        public void mapSlot(int s, String id) { map.put(s, id); }
        public String getWeaponId(int s) { return map.get(s); }
    }

    public static class WeaponDetailHolder implements InventoryHolder {
        private Inventory inv;
        private final String weaponId;
        private final Map<Integer, Integer> passiveMap = new HashMap<>();
        public WeaponDetailHolder(String id) { weaponId = id; }
        @Override public Inventory getInventory() { return inv; }
        public void setInventory(Inventory v) { inv = v; }
        public String getWeaponId() { return weaponId; }
        public void mapPassiveSlot(int guiSlot, int passiveIndex) { passiveMap.put(guiSlot, passiveIndex); }
        public Integer getPassiveIndex(int guiSlot) { return passiveMap.get(guiSlot); }
    }

    public static class SkillEditHolder implements InventoryHolder {
        private Inventory inv;
        private final String weaponId;
        private final int slotNumber;
        public SkillEditHolder(String id, int s) { weaponId = id; slotNumber = s; }
        @Override public Inventory getInventory() { return inv; }
        public void setInventory(Inventory v) { inv = v; }
        public String getWeaponId() { return weaponId; }
        public int getSlotNumber() { return slotNumber; }
    }

    public static class PassiveEditHolder implements InventoryHolder {
        private Inventory inv;
        private final String weaponId;
        private final int passiveIndex;
        private final Map<Integer, String> modifierMap = new HashMap<>();
        public PassiveEditHolder(String id, int pi) { weaponId = id; passiveIndex = pi; }
        @Override public Inventory getInventory() { return inv; }
        public void setInventory(Inventory v) { inv = v; }
        public String getWeaponId() { return weaponId; }
        public int getPassiveIndex() { return passiveIndex; }
        public void mapModifierSlot(int guiSlot, String key) { modifierMap.put(guiSlot, key); }
        public String getModifierKey(int guiSlot) { return modifierMap.get(guiSlot); }
    }
}
