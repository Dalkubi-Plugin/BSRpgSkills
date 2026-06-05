package kr.yongpyo.bsrpgskills.gui;

import kr.yongpyo.bsrpgskills.BSRpgSkills;
import kr.yongpyo.bsrpgskills.model.PassiveSlot;
import kr.yongpyo.bsrpgskills.model.PassiveTrigger;
import kr.yongpyo.bsrpgskills.model.PlayerSkillData;
import kr.yongpyo.bsrpgskills.model.SkillSlot;
import kr.yongpyo.bsrpgskills.model.WeaponSkill;
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
 * BSRpgSkills 전용 GUI 매니저입니다.
 */
public class GUIManager {

    private static final int[] WEAPON_LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final BSRpgSkills plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public GUIManager(BSRpgSkills plugin) {
        this.plugin = plugin;
    }

    public void openWeaponListGUI(Player player) {
        WeaponListHolder holder = new WeaponListHolder();
        Inventory gui = Bukkit.createInventory(holder, 45, text("<dark_gray>무기 설정 목록</dark_gray>"));
        holder.setInventory(gui);

        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE);
        decorateSection(gui, 1, 7, Material.GREEN_STAINED_GLASS_PANE, "<green>무기 선택</green>");
        gui.setItem(40, buildItem(Material.BOOK,
                "<gold>BSRpgSkills 편집기</gold>",
                List.of(
                        "<gray>무기를 클릭하면 상세 설정 화면으로 이동합니다.</gray>",
                        "<gray>액티브와 패시브를 각각 편집할 수 있습니다.</gray>"
                )));

        int index = 0;
        for (WeaponSkill weapon : plugin.getWeaponSkillManager().getAllWeapons()) {
            if (index >= WEAPON_LIST_SLOTS.length) {
                break;
            }

            int guiSlot = WEAPON_LIST_SLOTS[index++];
            ItemStack icon = buildMMOItemIcon(weapon);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(text(weapon.getDisplayName()));
            meta.lore(List.of(
                    text("<gray>ID: <white>" + weapon.getWeaponId() + "</white></gray>"),
                    text("<gray>MMO 타입: <white>" + weapon.getMmoType() + "</white></gray>"),
                    text("<gray>액티브: <white>" + weapon.getActiveSkillCount() + "/" + WeaponSkill.MAX_SLOTS + "</white></gray>"),
                    text("<gray>패시브: <white>" + weapon.getPassives().size() + "개</white></gray>"),
                    text("<yellow>클릭하여 편집</yellow>")
            ));
            icon.setItemMeta(meta);

            gui.setItem(guiSlot, icon);
            holder.mapSlot(guiSlot, weapon.getWeaponId());
        }

        player.openInventory(gui);
    }

    public void openWeaponDetailGUI(Player player, WeaponSkill weapon) {
        WeaponDetailHolder holder = new WeaponDetailHolder(weapon.getWeaponId());
        Inventory gui = Bukkit.createInventory(holder, 54, text("<dark_gray>" + weapon.getWeaponId() + " 설정</dark_gray>"));
        holder.setInventory(gui);

        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE);
        decorateSection(gui, 10, 16, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "<aqua>무기 정보</aqua>");
        decorateSection(gui, 19, 25, Material.RED_STAINED_GLASS_PANE, "<red>액티브 스킬</red>");
        decorateSection(gui, 37, 43, Material.LIME_STAINED_GLASS_PANE, "<green>패시브 스킬</green>");

        gui.setItem(4, buildItem(Material.NETHER_STAR, weapon.getDisplayName(), List.of(
                "<gray>ID: <white>" + weapon.getWeaponId() + "</white></gray>",
                "<gray>MMO 타입: <white>" + weapon.getMmoType() + "</white></gray>"
        )));

        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            gui.setItem(18 + i, buildSkillIcon(weapon.getSkill(i)));
        }

        int passiveSlot = 37;
        for (int i = 0; i < weapon.getPassives().size() && passiveSlot <= 42; i++) {
            gui.setItem(passiveSlot, buildPassiveIcon(weapon.getPassives().get(i)));
            holder.mapPassiveSlot(passiveSlot, i);
            passiveSlot++;
        }

        gui.setItem(49, buildItem(Material.ARROW, "<gray>뒤로</gray>", List.of("<yellow>무기 목록으로 돌아갑니다.</yellow>")));
        player.openInventory(gui);
    }

    public void openSkillEditGUI(Player player, WeaponSkill weapon, int slotNumber) {
        SkillSlot skill = weapon.getSkill(slotNumber);
        if (skill == null) {
            return;
        }

        SkillEditHolder holder = new SkillEditHolder(weapon.getWeaponId(), slotNumber);
        Inventory gui = Bukkit.createInventory(holder, 45,
                text("<dark_gray>액티브 편집 - 슬롯 " + slotNumber + " (" + skill.getKeybindLabel() + ")</dark_gray>"));
        holder.setInventory(gui);

        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE);
        decorateSection(gui, 10, 16, Material.CYAN_STAINED_GLASS_PANE, "<aqua>기본 정보</aqua>");
        decorateSection(gui, 28, 34, Material.ORANGE_STAINED_GLASS_PANE, "<gold>Modifiers</gold>");

        gui.setItem(4, buildItem(Material.NETHER_STAR,
                skill.getDisplayName(),
                List.of(
                        "<gray>Mythic ID: <white>" + skill.getMythicId() + "</white></gray>",
                        "<gray>키바인드: <white>" + skill.getKeybindLabel() + "</white></gray>",
                        "<gray>활성화: <white>" + (skill.isEnabled() ? "ON" : "OFF") + "</white></gray>"
                )));

        gui.setItem(10, buildItem(skill.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                skill.isEnabled() ? "<green>활성화</green>" : "<red>비활성화</red>",
                List.of("<yellow>클릭하여 전환</yellow>")));
        gui.setItem(11, buildItem(Material.NAME_TAG,
                "<white>스킬 ID</white>",
                List.of("<gray>" + emptyFallback(skill.getMythicId()) + "</gray>", "<yellow>클릭 - 채팅 입력</yellow>")));
        gui.setItem(12, buildItem(Material.WRITABLE_BOOK,
                "<white>표시 이름</white>",
                List.of("<gray>" + emptyFallback(skill.getDisplayName()) + "</gray>", "<yellow>클릭 - 채팅 입력</yellow>")));
        gui.setItem(14, buildItem(Material.CLOCK,
                "<white>쿨다운: " + skill.getCooldown() + "초</white>",
                List.of("<yellow>좌클릭 +0.1 / 우클릭 -0.1</yellow>", "<yellow>휠클릭 +0.01</yellow>", "<yellow>Shift 좌클릭 +1.0 / Shift 우클릭 -1.0</yellow>")));
        gui.setItem(15, buildItem(Material.PAPER,
                "<white>설명</white>",
                List.of("<gray>" + emptyFallback(skill.getDescription()) + "</gray>", "<yellow>클릭 - 채팅 입력</yellow>")));
        gui.setItem(16, buildItem(Material.BOOK,
                "<gold>편집 안내</gold>",
                List.of(
                        "<gray>아래줄에서 damage, ratio, 기타 modifier를 수정합니다.</gray>",
                        "<gray>추가 modifier는 key:value 형식으로 입력합니다.</gray>"
                )));

        gui.setItem(28, buildItem(Material.IRON_SWORD,
                "<white>damage: " + skill.getDamage() + "</white>",
                List.of("<yellow>좌클릭 +0.1 / 우클릭 -0.1</yellow>", "<yellow>휠클릭 +0.01</yellow>", "<yellow>Shift 좌클릭 +1.0 / Shift 우클릭 -1.0</yellow>")));
        gui.setItem(29, buildItem(Material.BLAZE_POWDER,
                "<white>ratio: " + skill.getRatio() + "</white>",
                List.of("<yellow>좌클릭 +0.1 / 우클릭 -0.1</yellow>", "<yellow>휠클릭 +0.01</yellow>", "<yellow>Shift 좌클릭 +1.0 / Shift 우클릭 -1.0</yellow>")));

        int modifierSlot = 30;
        for (var entry : skill.getModifiers().entrySet()) {
            if ("damage".equalsIgnoreCase(entry.getKey()) || "ratio".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            if (modifierSlot > 33) {
                break;
            }

            gui.setItem(modifierSlot, buildItem(Material.PAPER,
                    "<white>" + entry.getKey() + ": " + entry.getValue() + "</white>",
                    List.of("<yellow>좌클릭 +0.1 / 우클릭 -0.1</yellow>", "<yellow>휠클릭 +0.01</yellow>", "<yellow>Shift 좌클릭 +1.0 / Shift 우클릭 -1.0</yellow>")));
            holder.mapModifierSlot(modifierSlot, entry.getKey());
            modifierSlot++;
        }

        gui.setItem(34, buildItem(Material.ANVIL,
                "<green>Modifier 추가</green>",
                List.of("<yellow>클릭 - key:value 입력</yellow>")));
        gui.setItem(36, buildItem(Material.ARROW, "<gray>뒤로</gray>", List.of("<yellow>무기 상세 화면으로 돌아갑니다.</yellow>")));
        gui.setItem(42, buildItem(Material.EXPERIENCE_BOTTLE,
                "<aqua>레벨 편집</aqua>",
                List.of(
                        "<gray>최대 레벨: <white>" + skill.getMaxLevel() + "</white></gray>",
                        "<gray>레벨별 cooldown / damage / lore 조정</gray>",
                        "<yellow>클릭하여 열기</yellow>"
                )));
        gui.setItem(44, buildItem(Material.COMPASS,
                "<gold>표시 규칙</gold>",
                List.of(
                        "<gray>damage와 ratio는 전용 버튼으로 수정</gray>",
                        "<gray>기타 modifier는 최대 4개까지 아래줄에 표시</gray>"
                )));

        player.openInventory(gui);
    }

    /**
     * 플레이어가 자신의 무기 스킬을 강화하는 GUI입니다. 전투 모드에서만 열 수 있습니다.
     */
    public void openSkillUpgradeGUI(Player player, WeaponSkill weapon) {
        SkillUpgradeHolder holder = new SkillUpgradeHolder(weapon.getWeaponId());
        Inventory gui = Bukkit.createInventory(holder, 36,
                text("<dark_gray>스킬 강화 - </dark_gray>" + weapon.getDisplayName()));
        holder.setInventory(gui);

        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE);

        PlayerSkillData data = plugin.getPlayerSkillManager().get(player);

        gui.setItem(4, buildItem(Material.NETHER_STAR, weapon.getDisplayName(), List.of(
                "<gray>공용 스킬 포인트: <yellow>" + data.getPoints() + "</yellow></gray>",
                "<gray>현재 전투 무기 스킬 중 하나를 선택해 강화합니다.</gray>",
                "<gray>슬롯을 클릭하면 1포인트로 레벨이 상승합니다.</gray>"
        )));

        // slot-1 (좌클릭, max-level 1)은 강화 불가이므로 제외
        int[] skillSlots = {11, 12, 13, 14, 15};
        for (int i = 2; i <= WeaponSkill.MAX_SLOTS; i++) {
            int guiSlot = skillSlots[i - 2];
            SkillSlot skill = weapon.getSkill(i);
            gui.setItem(guiSlot, buildUpgradeIcon(skill, data, weapon.getWeaponId(), i));
            holder.mapSlot(guiSlot, i);
        }

        gui.setItem(31, buildItem(Material.BARRIER, "<red>닫기</red>", List.of("<gray>창을 닫습니다.</gray>")));
        player.openInventory(gui);
    }

    private ItemStack buildUpgradeIcon(SkillSlot skill, PlayerSkillData data, String weaponId, int slotNumber) {
        if (skill == null || !skill.isValid()) {
            return buildItem(Material.GRAY_DYE,
                    "<dark_gray>슬롯 " + slotNumber + " - 비어있음</dark_gray>",
                    List.of("<gray>이 슬롯에 활성화된 스킬이 없습니다.</gray>"));
        }

        int level = data.getLevel(weaponId, slotNumber);
        int points = data.getPoints();
        int max = skill.getMaxLevel();

        ItemStack icon = buildIconWithCMD(skill.getIcon(), skill.getCustomModelData());
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(text(skill.getDisplayName() + " <gray>[Lv " + level + "/" + max + "]</gray>"));

        List<Component> lore = new ArrayList<>();
        lore.add(text("<gray>키바인드: <white>" + skill.getKeybindLabel() + "</white></gray>"));
        lore.add(text("<gray>데미지: <white>" + format(skill.getDamageForLevel(level))
                + "</white> <gray>| 쿨다운: <white>" + format(skill.getCooldownForLevel(level)) + "초</white></gray>"));
        lore.add(text("<gray>공용 스킬 포인트: <yellow>" + points + "</yellow></gray>"));

        for (String line : skill.getDescriptionLinesForLevel(level)) {
            if (line == null || line.isBlank()) {
                continue;
            }
            lore.add(text(line));
        }

        if (!skill.isLevelable()) {
            lore.add(Component.empty());
            lore.add(text("<dark_red>이 스킬은 강화할 수 없습니다.</dark_red>"));
        } else if (level >= max) {
            lore.add(Component.empty());
            lore.add(text("<gold>이미 최대 레벨입니다.</gold>"));
        } else {
            lore.add(Component.empty());
            lore.add(text("<aqua>다음 레벨 (" + (level + 1) + "):</aqua>"));
            lore.add(text("<gray> - 데미지 <white>" + format(skill.getDamageForLevel(level + 1)) + "</white></gray>"));
            double nextCd = skill.getCooldownForLevel(level + 1);
            double baseCd = skill.getCooldown();
            if (Double.compare(nextCd, baseCd) != 0) {
                lore.add(text("<gray> - 쿨다운 <white>" + format(nextCd) + "초</white></gray>"));
            }
            for (String line : skill.getResolvedLoreForSingleLevel(level + 1)) {
                lore.add(text("<gray> - " + line + "</gray>"));
            }
            lore.add(Component.empty());
            if (points >= 1) {
                lore.add(text("<green>클릭하여 강화 (1pt)</green>"));
            } else {
                lore.add(text("<red>포인트가 부족합니다.</red>"));
            }
        }

        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private String format(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }

    /**
     * 관리자가 특정 스킬의 레벨별 cooldown / damage / lore 를 편집하는 GUI입니다.
     */
    public void openSkillLevelsEditGUI(Player player, WeaponSkill weapon, int slotNumber) {
        SkillSlot skill = weapon.getSkill(slotNumber);
        if (skill == null) {
            return;
        }

        SkillLevelsEditHolder holder = new SkillLevelsEditHolder(weapon.getWeaponId(), slotNumber);
        Inventory gui = Bukkit.createInventory(holder, 54,
                text("<dark_gray>레벨 편집 - 슬롯 " + slotNumber + "</dark_gray>"));
        holder.setInventory(gui);

        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE);

        gui.setItem(4, buildItem(Material.NETHER_STAR,
                skill.getDisplayName(),
                List.of(
                        "<gray>Mythic ID: <white>" + skill.getMythicId() + "</white></gray>",
                        "<gray>슬롯: <white>" + slotNumber + " (" + skill.getKeybindLabel() + ")</white></gray>"
                )));

        gui.setItem(11, buildItem(Material.EXPERIENCE_BOTTLE,
                "<gold>최대 레벨: " + skill.getMaxLevel() + "</gold>",
                List.of(
                        "<gray>좌/우 클릭으로 1단계 증감</gray>",
                        skill.getMaxLevel() == 1 ? "<dark_red>강화 불가 (max-level = 1)</dark_red>"
                                : "<green>강화 가능 (level 2 ~ " + skill.getMaxLevel() + ")</green>"
                )));
        gui.setItem(13, buildItem(Material.RED_CONCRETE, "<red>최대 레벨 -1</red>",
                List.of("<gray>현재 max-level을 1 줄입니다.</gray>")));
        gui.setItem(15, buildItem(Material.LIME_CONCRETE, "<green>최대 레벨 +1</green>",
                List.of("<gray>현재 max-level을 1 늘립니다 (상한 10).</gray>")));

        int[] rowStarts = {18, 27, 36, 45};
        for (int i = 0; i < rowStarts.length; i++) {
            int level = i + 2;
            int start = rowStarts[i];
            boolean active = level <= skill.getMaxLevel();
            SkillSlot.LevelOverride override = skill.getLevels().get(level);

            Double cdValue = override != null ? override.getCooldown() : null;
            Double dmgValue = override != null ? override.getDamage() : null;
            List<String> loreList = override != null ? override.getLore() : List.of();

            String levelTitle = active ? "<aqua>Lv " + level + "</aqua>" : "<dark_gray>Lv " + level + " (비활성)</dark_gray>";
            gui.setItem(start, buildItem(active ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                    levelTitle,
                    List.of(
                            "<gray>cooldown: <white>" + formatNullable(cdValue) + "</white></gray>",
                            "<gray>damage: <white>" + formatNullable(dmgValue) + "</white></gray>",
                            "<gray>lore 줄 수: <white>" + loreList.size() + "</white></gray>"
                    )));

            gui.setItem(start + 2, buildItem(Material.CLOCK,
                    "<white>cooldown: " + formatNullable(cdValue) + "</white>",
                    List.of(
                            "<yellow>좌클릭 +0.1 / 우클릭 -0.1</yellow>",
                            "<yellow>휠클릭 +0.01</yellow>",
                            "<yellow>Shift 좌클릭 +1.0 / Shift 우클릭 -1.0</yellow>",
                            "<yellow>Drop (Q) 키로 초기화 (base 사용)</yellow>"
                    )));

            gui.setItem(start + 4, buildItem(Material.IRON_SWORD,
                    "<white>damage: " + formatNullable(dmgValue) + "</white>",
                    List.of(
                            "<yellow>좌클릭 +0.1 / 우클릭 -0.1</yellow>",
                            "<yellow>휠클릭 +0.01</yellow>",
                            "<yellow>Shift 좌클릭 +1.0 / Shift 우클릭 -1.0</yellow>",
                            "<yellow>Drop (Q) 키로 초기화 (base 사용)</yellow>"
                    )));

            List<String> lorePreview = new ArrayList<>();
            lorePreview.add("<yellow>좌클릭 - 채팅으로 lore 입력</yellow>");
            lorePreview.add("<yellow>여러 줄은 \"|\" 로 구분</yellow>");
            lorePreview.add("<yellow>우클릭 - lore 전체 삭제</yellow>");
            if (!loreList.isEmpty()) {
                lorePreview.add("<gray>현재 lore:</gray>");
                for (String line : loreList) {
                    lorePreview.add("<gray> - " + emptyFallback(line) + "</gray>");
                }
            }
            gui.setItem(start + 6, buildItem(Material.WRITABLE_BOOK,
                    "<white>lore (" + loreList.size() + " 줄)</white>",
                    lorePreview));
        }

        gui.setItem(53, buildItem(Material.ARROW, "<gray>뒤로</gray>",
                List.of("<yellow>스킬 편집 화면으로 돌아갑니다.</yellow>")));

        player.openInventory(gui);
    }

    private String formatNullable(Double value) {
        if (value == null) {
            return "(base)";
        }
        return format(value);
    }

    public void openPassiveEditGUI(Player player, WeaponSkill weapon, int passiveIndex) {
        if (passiveIndex < 0 || passiveIndex >= weapon.getPassives().size()) {
            return;
        }

        PassiveSlot passive = weapon.getPassives().get(passiveIndex);
        PassiveEditHolder holder = new PassiveEditHolder(weapon.getWeaponId(), passiveIndex);
        Inventory gui = Bukkit.createInventory(holder, 45,
                text("<dark_gray>패시브 편집 - " + passive.getType() + "</dark_gray>"));
        holder.setInventory(gui);

        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE);
        decorateSection(gui, 10, 16, Material.LIME_STAINED_GLASS_PANE, "<green>기본 정보</green>");
        decorateSection(gui, 28, 33, Material.YELLOW_STAINED_GLASS_PANE, "<yellow>Modifiers</yellow>");

        gui.setItem(4, buildItem(Material.NETHER_STAR,
                passive.getDisplayName(),
                List.of(
                        "<gray>스킬 ID: <white>" + passive.getType() + "</white></gray>",
                        "<gray>트리거: <white>" + passive.getTriggerType().getLabel() + "</white></gray>",
                        "<gray>활성화: <white>" + (passive.isEnabled() ? "ON" : "OFF") + "</white></gray>"
                )));

        gui.setItem(10, buildItem(passive.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                passive.isEnabled() ? "<green>활성화</green>" : "<red>비활성화</red>",
                List.of("<yellow>클릭하여 전환</yellow>")));
        gui.setItem(11, buildItem(Material.NAME_TAG,
                "<white>스킬 ID</white>",
                List.of("<gray>" + emptyFallback(passive.getType()) + "</gray>", "<yellow>클릭 - 채팅 입력</yellow>")));
        gui.setItem(12, buildItem(Material.WRITABLE_BOOK,
                "<white>표시 이름</white>",
                List.of("<gray>" + emptyFallback(passive.getDisplayName()) + "</gray>", "<yellow>클릭 - 채팅 입력</yellow>")));
        gui.setItem(13, buildItem(Material.COMPASS,
                "<aqua>발동 조건: " + passive.getTriggerType().getLabel() + "</aqua>",
                List.of("<yellow>클릭하여 전환</yellow>",
                        "<gray>주기 발동 / 피격 시 / 가격 시 변경</gray>")));
        gui.setItem(14, buildItem(Material.REPEATER,
                "<white>발동 주기: " + passive.getTimer() + "초</white>",
                List.of("<yellow>좌클릭 +0.1 / 우클릭 -0.1</yellow>", "<yellow>휠클릭 +0.01</yellow>", "<yellow>Shift 좌클릭 +1.0 / Shift 우클릭 -1.0</yellow>",
                        passive.getTriggerType().isEventTrigger() ? "<dark_gray>(이벤트 트리거에서는 미사용)</dark_gray>" : "")));
        gui.setItem(15, buildItem(Material.CLOCK,
                "<white>쿨다운: " + passive.getCooldown() + "초</white>",
                List.of("<yellow>좌클릭 +0.1 / 우클릭 -0.1</yellow>", "<yellow>휠클릭 +0.01</yellow>", "<yellow>Shift 좌클릭 +1.0 / Shift 우클릭 -1.0</yellow>")));
        gui.setItem(16, buildItem(Material.PAPER,
                "<white>설명</white>",
                List.of("<gray>" + emptyFallback(passive.getDescription()) + "</gray>", "<yellow>클릭 - 채팅 입력</yellow>")));

        int modifierSlot = 28;
        for (var entry : passive.getModifiers().entrySet()) {
            if (modifierSlot > 33) {
                break;
            }
            gui.setItem(modifierSlot, buildItem(Material.PAPER,
                    "<white>" + entry.getKey() + ": " + entry.getValue() + "</white>",
                    List.of("<yellow>좌클릭 +0.1 / 우클릭 -0.1</yellow>", "<yellow>휠클릭 +0.01</yellow>", "<yellow>Shift 좌클릭 +1.0 / Shift 우클릭 -1.0</yellow>")));
            holder.mapModifierSlot(modifierSlot, entry.getKey());
            modifierSlot++;
        }

        gui.setItem(36, buildItem(Material.ARROW, "<gray>뒤로</gray>", List.of("<yellow>무기 상세 화면으로 돌아갑니다.</yellow>")));
        gui.setItem(44, buildItem(Material.COMPASS,
                "<gold>Modifier 안내</gold>",
                List.of("<gray>패시브 modifier는 아래줄에서 바로 조절합니다.</gray>")));

        player.openInventory(gui);
    }

    private ItemStack buildSkillIcon(SkillSlot skill) {
        ItemStack icon = buildIconWithCMD(skill.getIcon(), skill.getCustomModelData());
        ItemMeta meta = icon.getItemMeta();

        String status = skill.isValid() ? "<green>ON</green>" : "<red>OFF</red>";
        meta.displayName(text("[" + status + "<white>] 슬롯 " + skill.getSlot()
                + " (" + skill.getKeybindLabel() + ") </white>" + skill.getDisplayName()));
        meta.lore(List.of(
                text("<gray>스킬 ID: <white>" + emptyFallback(skill.getMythicId()) + "</white></gray>"),
                text("<gray>damage: <white>" + skill.getDamage() + "</white></gray>"),
                text("<gray>ratio: <white>" + skill.getRatio() + "</white></gray>"),
                text("<gray>쿨다운: <white>" + skill.getCooldown() + "초</white></gray>"),
                text("<yellow>클릭하여 편집</yellow>")
        ));
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack buildPassiveIcon(PassiveSlot passive) {
        ItemStack icon = buildIconWithCMD(passive.getIcon(), passive.getCustomModelData());
        ItemMeta meta = icon.getItemMeta();

        String status = passive.isEnabled() ? "<green>ON</green>" : "<red>OFF</red>";
        meta.displayName(text("[" + status + "<white>] 패시브</white>" + passive.getDisplayName()));

        List<Component> lore = new ArrayList<>();
        lore.add(text("<gray>스킬 ID: <white>" + passive.getType() + "</white></gray>"));
        lore.add(text("<gray>트리거: <white>" + passive.getTriggerType().getLabel() + "</white></gray>"));
        if (passive.getTriggerType() == PassiveTrigger.TIMER) {
            lore.add(text("<gray>발동 주기: <white>" + passive.getTimer() + "초</white></gray>"));
        } else {
            lore.add(text("<gray>이벤트 발생 시 쿨다운 기준으로 발동</gray>"));
        }
        lore.add(text("<gray>쿨다운: <white>" + passive.getCooldown() + "초</white></gray>"));
        if (!passive.getModifiers().isEmpty()) {
            lore.add(text("<gray>modifier: <white>" + passive.getModifiers().keySet() + "</white></gray>"));
        }
        lore.add(text("<yellow>클릭하여 편집</yellow>"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private void fillBackground(Inventory inventory, Material material) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, buildPane(material));
        }
    }

    private void decorateSection(Inventory inventory, int from, int to, Material material, String title) {
        for (int slot = from; slot <= to; slot++) {
            inventory.setItem(slot, buildPane(material));
        }

        int center = (from + to) / 2;
        inventory.setItem(center, buildItem(Material.NAME_TAG, title, List.of()));
    }

    private ItemStack buildPane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
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

    private String emptyFallback(String value) {
        return value == null || value.isBlank() ? "없음" : value;
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

    public static class SkillLevelsEditHolder implements InventoryHolder {
        private Inventory inventory;
        private final String weaponId;
        private final int slotNumber;

        public SkillLevelsEditHolder(String weaponId, int slotNumber) {
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
    }

    public static class SkillUpgradeHolder implements InventoryHolder {
        private Inventory inventory;
        private final String weaponId;
        private final Map<Integer, Integer> slotMap = new HashMap<>();

        public SkillUpgradeHolder(String weaponId) {
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

        public void mapSlot(int guiSlot, int skillSlot) {
            slotMap.put(guiSlot, skillSlot);
        }

        public Integer getSkillSlot(int guiSlot) {
            return slotMap.get(guiSlot);
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
