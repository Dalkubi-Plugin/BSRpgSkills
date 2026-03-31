package kr.yongpyo.bsskill.gui;

import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.model.PassiveSlot;
import kr.yongpyo.bsskill.model.SkillSlot;
import kr.yongpyo.bsskill.model.WeaponSkill;
import net.Indyuce.mmoitems.MMOItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 무기, 액티브 스킬, 패시브 스킬 설정을 편집하는 GUI 매니저입니다.
 * 운영자가 자주 바꾸는 값을 YAML을 열지 않고도 빠르게 수정할 수 있도록 구성합니다.
 */
public class GUIManager {

    private final BSSkill plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public GUIManager(BSSkill plugin) {
        this.plugin = plugin;
    }

    public void openWeaponListGUI(Player player) {
        WeaponListHolder holder = new WeaponListHolder();
        Inventory gui = Bukkit.createInventory(holder, 54, text("<dark_gray>BSSkill - 무기 목록</dark_gray>"));
        holder.setInventory(gui);

        int index = 0;
        for (WeaponSkill weapon : plugin.getWeaponSkillManager().getAllWeapons()) {
            if (index >= 45) {
                break;
            }

            ItemStack icon = buildMMOItemIcon(weapon);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(text(weapon.getDisplayName()));

            List<Component> lore = new ArrayList<>();
            lore.add(text("<dark_gray>────────────────</dark_gray>"));
            lore.add(text("<gray>ID: <white>" + weapon.getWeaponId() + "</white></gray>"));
            lore.add(text("<gray>MMO 타입: <white>" + weapon.getMmoType() + "</white></gray>"));
            lore.add(text("<gray>액티브: <white>" + weapon.getActiveSkillCount() + "/" + WeaponSkill.MAX_SLOTS + "</white></gray>"));
            lore.add(text("<gray>패시브: <white>" + weapon.getPassives().size() + "개</white></gray>"));
            lore.add(text("<dark_gray>────────────────</dark_gray>"));
            lore.add(text("<yellow>클릭하여 편집</yellow>"));
            meta.lore(lore);
            icon.setItemMeta(meta);

            gui.setItem(index, icon);
            holder.mapSlot(index, weapon.getWeaponId());
            index++;
        }

        player.openInventory(gui);
    }

    private ItemStack buildMMOItemIcon(WeaponSkill weapon) {
        try {
            var type = MMOItems.plugin.getTypes().get(weapon.getMmoType());
            if (type != null) {
                ItemStack mmoItem = MMOItems.plugin.getItem(type, weapon.getWeaponId());
                if (mmoItem != null) {
                    return mmoItem.clone();
                }
            }
        } catch (Exception exception) {
            plugin.logDebug("MMOItems 아이콘 로드에 실패했습니다: " + weapon.getWeaponId(), exception);
        }

        return new ItemStack(Material.DIAMOND_SWORD);
    }

    public void openWeaponDetailGUI(Player player, WeaponSkill weapon) {
        WeaponDetailHolder holder = new WeaponDetailHolder(weapon.getWeaponId());
        Inventory gui = Bukkit.createInventory(holder, 54, text("<dark_gray>BSSkill - " + weapon.getWeaponId() + "</dark_gray>"));
        holder.setInventory(gui);

        gui.setItem(4, buildItem(Material.BOOK, weapon.getDisplayName(),
                List.of("<gray>" + weapon.getMmoType() + " : " + weapon.getWeaponId() + "</gray>")));

        gui.setItem(18, buildItem(Material.IRON_SWORD, "<white>액티브 스킬</white>", List.of()));
        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            gui.setItem(18 + i, buildSkillIcon(weapon.getSkill(i)));
        }

        gui.setItem(36, buildItem(Material.ENDER_EYE, "<white>패시브 스킬</white>", List.of()));
        int passiveSlot = 37;
        for (int i = 0; i < weapon.getPassives().size() && passiveSlot <= 43; i++) {
            gui.setItem(passiveSlot, buildPassiveIcon(weapon.getPassives().get(i)));
            holder.mapPassiveSlot(passiveSlot, i);
            passiveSlot++;
        }

        gui.setItem(45, buildItem(Material.ARROW, "<gray>뒤로</gray>", List.of()));
        player.openInventory(gui);
    }

    private ItemStack buildSkillIcon(SkillSlot skill) {
        ItemStack icon = buildIconWithCMD(skill.getIcon(), skill.getCustomModelData());
        ItemMeta meta = icon.getItemMeta();

        String status = skill.isValid() ? "<green>ON</green>" : "<red>OFF</red>";
        meta.displayName(text("[" + status + "<white>] 슬롯 " + skill.getSlot()
                + " (" + skill.getKeybindLabel() + ") </white>" + skill.getDisplayName()));

        List<Component> lore = new ArrayList<>();
        lore.add(text("<dark_gray>────────────────</dark_gray>"));
        if (skill.isValid()) {
            lore.add(text("<gray>스킬 ID: <white>" + skill.getMythicId() + "</white></gray>"));
            lore.add(text("<gray>기본 데미지: <white>" + skill.getDamage() + "</white></gray>"));
            lore.add(text("<gray>쿨타임: <white>" + skill.getCooldown() + "초</white></gray>"));
            if (!skill.getModifiers().isEmpty()) {
                lore.add(text("<gray>modifier 수: <white>" + skill.getModifiers().size() + "개</white></gray>"));
            }
            if (!skill.getDescription().isEmpty()) {
                lore.add(text("<dark_gray>────────────────</dark_gray>"));
                lore.add(text("<gray>" + skill.getDescription() + "</gray>"));
            }
        } else {
            lore.add(text("<dark_gray>비어 있는 슬롯입니다.</dark_gray>"));
        }
        lore.add(text("<dark_gray>────────────────</dark_gray>"));
        lore.add(text("<yellow>클릭하여 편집</yellow>"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack buildPassiveIcon(PassiveSlot passive) {
        ItemStack icon = buildIconWithCMD(passive.getIcon(), passive.getCustomModelData());
        ItemMeta meta = icon.getItemMeta();

        String status = passive.isEnabled() ? "<green>ON</green>" : "<red>OFF</red>";
        meta.displayName(text("[" + status + "<white>] 패시브 </white>" + passive.getDisplayName()));

        List<Component> lore = new ArrayList<>();
        lore.add(text("<dark_gray>────────────────</dark_gray>"));
        lore.add(text("<gray>스킬 ID: <white>" + passive.getType() + "</white></gray>"));
        lore.add(text("<gray>발동 주기: <white>" + passive.getTimer() + "초</white></gray>"));
        if (passive.getCooldown() > 0) {
            lore.add(text("<gray>쿨타임: <white>" + passive.getCooldown() + "초</white></gray>"));
        }
        if (!passive.getModifiers().isEmpty()) {
            lore.add(text("<gray>modifier 목록</gray>"));
            for (var entry : passive.getModifiers().entrySet()) {
                lore.add(text(" <gray>- " + entry.getKey() + ": <white>" + entry.getValue() + "</white></gray>"));
            }
        }
        if (!passive.getDescription().isEmpty()) {
            lore.add(text("<dark_gray>────────────────</dark_gray>"));
            lore.add(text("<gray>" + passive.getDescription() + "</gray>"));
        }
        lore.add(text("<dark_gray>────────────────</dark_gray>"));
        lore.add(text("<yellow>클릭하여 편집</yellow>"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    public void openSkillEditGUI(Player player, WeaponSkill weapon, int slotNumber) {
        SkillSlot skill = weapon.getSkill(slotNumber);
        if (skill == null) {
            return;
        }

        SkillEditHolder holder = new SkillEditHolder(weapon.getWeaponId(), slotNumber);
        Inventory gui = Bukkit.createInventory(holder, 36,
                text("<dark_gray>액티브 편집 - 슬롯 " + slotNumber + " (" + skill.getKeybindLabel() + ")</dark_gray>"));
        holder.setInventory(gui);

        gui.setItem(10, buildItem(skill.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                skill.isEnabled() ? "<green>활성화</green>" : "<red>비활성화</red>",
                List.of("<yellow>클릭하여 전환</yellow>")));
        gui.setItem(11, buildItem(Material.NAME_TAG,
                "<white>스킬 ID: " + skill.getMythicId() + "</white>",
                List.of("<yellow>클릭 - 채팅 입력</yellow>")));
        gui.setItem(12, buildItem(Material.WRITABLE_BOOK,
                "<white>표시 이름: </white>" + skill.getDisplayName(),
                List.of("<yellow>클릭 - 채팅 입력</yellow>")));
        gui.setItem(14, buildItem(Material.IRON_SWORD,
                "<white>기본 데미지: " + skill.getDamage() + "</white>",
                List.of("<yellow>좌클릭 -1 / 우클릭 +1</yellow>", "<yellow>Shift 좌클릭 -5 / Shift 우클릭 +5</yellow>")));
        gui.setItem(15, buildItem(Material.CLOCK,
                "<white>쿨타임: " + skill.getCooldown() + "초</white>",
                List.of("<yellow>좌클릭 -0.5 / 우클릭 +0.5</yellow>", "<yellow>Shift 좌클릭 -5 / Shift 우클릭 +5</yellow>")));
        gui.setItem(16, buildItem(Material.PAPER,
                "<white>설명: " + (skill.getDescription().isEmpty() ? "없음" : skill.getDescription()) + "</white>",
                List.of("<yellow>클릭 - 채팅 입력</yellow>")));

        int modifierSlot = 20;
        for (var entry : skill.getModifiers().entrySet()) {
            if ("damage".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            if (modifierSlot > 24) {
                break;
            }

            gui.setItem(modifierSlot, buildItem(Material.PAPER,
                    "<white>" + entry.getKey() + ": " + entry.getValue() + "</white>",
                    List.of("<yellow>좌클릭 -1 / 우클릭 +1</yellow>", "<yellow>Shift 좌클릭 -5 / Shift 우클릭 +5</yellow>")));
            holder.mapModifierSlot(modifierSlot, entry.getKey());
            modifierSlot++;
        }

        gui.setItem(25, buildItem(Material.ANVIL,
                "<green>Modifier 추가</green>",
                List.of("<yellow>클릭 - key:value 형식 입력</yellow>")));
        gui.setItem(26, buildItem(Material.BOOK,
                "<gold>Modifier 규칙</gold>",
                List.of(
                        "<gray>예시: <white>radius:3.5</white></gray>",
                        "<gray>이름은 자유롭게 입력 가능</gray>",
                        "<gray>damage는 전용 버튼으로 수정</gray>"
                )));
        gui.setItem(27, buildItem(Material.ARROW, "<gray>뒤로</gray>", List.of()));
        gui.setItem(31, buildItem(Material.EMERALD, "<green>자동 저장</green>", List.of("<gray>변경 시 즉시 저장됩니다.</gray>")));

        player.openInventory(gui);
    }

    public void openPassiveEditGUI(Player player, WeaponSkill weapon, int passiveIndex) {
        if (passiveIndex < 0 || passiveIndex >= weapon.getPassives().size()) {
            return;
        }

        PassiveSlot passive = weapon.getPassives().get(passiveIndex);
        PassiveEditHolder holder = new PassiveEditHolder(weapon.getWeaponId(), passiveIndex);
        Inventory gui = Bukkit.createInventory(holder, 36,
                text("<dark_gray>패시브 편집 - " + passive.getType() + "</dark_gray>"));
        holder.setInventory(gui);

        gui.setItem(10, buildItem(passive.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                passive.isEnabled() ? "<green>활성화</green>" : "<red>비활성화</red>",
                List.of("<yellow>클릭하여 전환</yellow>")));
        gui.setItem(11, buildItem(Material.NAME_TAG,
                "<white>스킬 ID: " + passive.getType() + "</white>",
                List.of("<yellow>클릭 - 채팅 입력</yellow>")));
        gui.setItem(12, buildItem(Material.WRITABLE_BOOK,
                "<white>표시 이름: </white>" + passive.getDisplayName(),
                List.of("<yellow>클릭 - 채팅 입력</yellow>")));
        gui.setItem(14, buildItem(Material.REPEATER,
                "<white>발동 주기: " + passive.getTimer() + "초</white>",
                List.of("<yellow>좌클릭 -0.5 / 우클릭 +0.5</yellow>", "<yellow>Shift 좌클릭 -5 / Shift 우클릭 +5</yellow>")));
        gui.setItem(15, buildItem(Material.CLOCK,
                "<white>쿨타임: " + passive.getCooldown() + "초</white>",
                List.of("<yellow>좌클릭 -0.5 / 우클릭 +0.5</yellow>", "<yellow>Shift 좌클릭 -5 / Shift 우클릭 +5</yellow>")));
        gui.setItem(16, buildItem(Material.PAPER,
                "<white>설명: " + (passive.getDescription().isEmpty() ? "없음" : passive.getDescription()) + "</white>",
                List.of("<yellow>클릭 - 채팅 입력</yellow>")));

        int modifierSlot = 20;
        for (var entry : passive.getModifiers().entrySet()) {
            if (modifierSlot > 25) {
                break;
            }
            gui.setItem(modifierSlot, buildItem(Material.PAPER,
                    "<white>" + entry.getKey() + ": " + entry.getValue() + "</white>",
                    List.of("<yellow>좌클릭 -1 / 우클릭 +1</yellow>", "<yellow>Shift 좌클릭 -5 / Shift 우클릭 +5</yellow>")));
            holder.mapModifierSlot(modifierSlot, entry.getKey());
            modifierSlot++;
        }

        gui.setItem(27, buildItem(Material.ARROW, "<gray>뒤로</gray>", List.of()));
        gui.setItem(31, buildItem(Material.EMERALD, "<green>자동 저장</green>", List.of("<gray>변경 시 즉시 저장됩니다.</gray>")));

        player.openInventory(gui);
    }

    private ItemStack buildIconWithCMD(String materialName, int customModelData) {
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (Exception exception) {
            material = Material.BARRIER;
        }

        ItemStack item = new ItemStack(material);
        if (customModelData > 0) {
            ItemMeta meta = item.getItemMeta();
            meta.setCustomModelData(customModelData);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name));

        if (lore != null && !lore.isEmpty()) {
            List<Component> components = new ArrayList<>();
            for (String line : lore) {
                components.add(text(line));
            }
            meta.lore(components);
        }

        item.setItemMeta(meta);
        return item;
    }

    private Component text(String value) {
        return mm.deserialize(value).decoration(TextDecoration.ITALIC, false);
    }

    public static class WeaponListHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> weaponMap = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public void mapSlot(int slot, String weaponId) {
            weaponMap.put(slot, weaponId);
        }

        public String getWeaponId(int slot) {
            return weaponMap.get(slot);
        }
    }

    public static class WeaponDetailHolder implements InventoryHolder {
        private Inventory inventory;
        private final String weaponId;
        private final Map<Integer, Integer> passiveMap = new HashMap<>();

        public WeaponDetailHolder(String weaponId) {
            this.weaponId = weaponId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public String getWeaponId() {
            return weaponId;
        }

        public void mapPassiveSlot(int guiSlot, int passiveIndex) {
            passiveMap.put(guiSlot, passiveIndex);
        }

        public Integer getPassiveIndex(int guiSlot) {
            return passiveMap.get(guiSlot);
        }
    }

    public static class SkillEditHolder implements InventoryHolder {
        private Inventory inventory;
        private final String weaponId;
        private final int slotNumber;
        private final Map<Integer, String> modifierMap = new HashMap<>();

        public SkillEditHolder(String weaponId, int slotNumber) {
            this.weaponId = weaponId;
            this.slotNumber = slotNumber;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public String getWeaponId() {
            return weaponId;
        }

        public int getSlotNumber() {
            return slotNumber;
        }

        public void mapModifierSlot(int guiSlot, String key) {
            modifierMap.put(guiSlot, key);
        }

        public String getModifierKey(int guiSlot) {
            return modifierMap.get(guiSlot);
        }
    }

    public static class PassiveEditHolder implements InventoryHolder {
        private Inventory inventory;
        private final String weaponId;
        private final int passiveIndex;
        private final Map<Integer, String> modifierMap = new HashMap<>();

        public PassiveEditHolder(String weaponId, int passiveIndex) {
            this.weaponId = weaponId;
            this.passiveIndex = passiveIndex;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public String getWeaponId() {
            return weaponId;
        }

        public int getPassiveIndex() {
            return passiveIndex;
        }

        public void mapModifierSlot(int guiSlot, String key) {
            modifierMap.put(guiSlot, key);
        }

        public String getModifierKey(int guiSlot) {
            return modifierMap.get(guiSlot);
        }
    }
}
