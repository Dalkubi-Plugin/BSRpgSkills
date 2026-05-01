package kr.yongpyo.bsrpgskills.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import kr.yongpyo.bsrpgskills.BSRpgSkills;
import kr.yongpyo.bsrpgskills.gui.GUIManager.PassiveEditHolder;
import kr.yongpyo.bsrpgskills.gui.GUIManager.SkillEditHolder;
import kr.yongpyo.bsrpgskills.gui.GUIManager.WeaponDetailHolder;
import kr.yongpyo.bsrpgskills.gui.GUIManager.WeaponListHolder;
import kr.yongpyo.bsrpgskills.model.PassiveSlot;
import kr.yongpyo.bsrpgskills.model.SkillSlot;
import kr.yongpyo.bsrpgskills.model.WeaponSkill;
import kr.yongpyo.bsrpgskills.util.ModifierValidator;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BSRpgSkills GUI 클릭과 채팅 입력을 처리합니다.
 */
public class GUIListener implements Listener {

    private final BSRpgSkills plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, InputCtx> pending = new ConcurrentHashMap<>();

    public GUIListener(BSRpgSkills plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isBSRpgSkillsGUI(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    private boolean isBSRpgSkillsGUI(org.bukkit.inventory.Inventory inventory) {
        var holder = inventory.getHolder();
        return holder instanceof WeaponListHolder
                || holder instanceof WeaponDetailHolder
                || holder instanceof SkillEditHolder
                || holder instanceof PassiveEditHolder;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!isBSRpgSkillsGUI(event.getInventory())) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }

        var holder = event.getInventory().getHolder();
        if (holder instanceof WeaponListHolder weaponListHolder) {
            handleWeaponList(player, weaponListHolder, event);
            return;
        }
        if (holder instanceof WeaponDetailHolder weaponDetailHolder) {
            handleWeaponDetail(player, weaponDetailHolder, event);
            return;
        }
        if (holder instanceof SkillEditHolder skillEditHolder) {
            handleSkillEdit(player, skillEditHolder, event);
            return;
        }
        if (holder instanceof PassiveEditHolder passiveEditHolder) {
            handlePassiveEdit(player, passiveEditHolder, event);
        }
    }

    private void handleWeaponList(Player player, WeaponListHolder holder, InventoryClickEvent event) {
        String weaponId = holder.getWeaponId(event.getSlot());
        if (weaponId == null) {
            return;
        }

        WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(weaponId);
        if (weapon != null) {
            plugin.getGUIManager().openWeaponDetailGUI(player, weapon);
            click(player);
        }
    }

    private void handleWeaponDetail(Player player, WeaponDetailHolder holder, InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 49) {
            plugin.getGUIManager().openWeaponListGUI(player);
            click(player);
            return;
        }

        if (slot >= 19 && slot <= 24) {
            int skillSlot = slot - 18;
            WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(holder.getWeaponId());
            if (weapon != null) {
                plugin.getGUIManager().openSkillEditGUI(player, weapon, skillSlot);
                click(player);
            }
            return;
        }

        Integer passiveIndex = holder.getPassiveIndex(slot);
        if (passiveIndex != null) {
            WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(holder.getWeaponId());
            if (weapon != null) {
                plugin.getGUIManager().openPassiveEditGUI(player, weapon, passiveIndex);
                click(player);
            }
        }
    }

    private void handleSkillEdit(Player player, SkillEditHolder holder, InventoryClickEvent event) {
        WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(holder.getWeaponId());
        if (weapon == null) {
            return;
        }

        SkillSlot skill = weapon.getSkill(holder.getSlotNumber());
        if (skill == null) {
            return;
        }

        ClickType clickType = event.getClick();
        switch (event.getSlot()) {
            case 10 -> {
                skill.setEnabled(!skill.isEnabled());
                saveRefreshSkill(player, weapon, holder.getSlotNumber());
            }
            case 11 -> {
                player.closeInventory();
                startInput(player, InputType.SKILL_ID, holder.getWeaponId(), holder.getSlotNumber(), -1);
                prompt(player, "MythicMobs 스킬 ID");
            }
            case 12 -> {
                player.closeInventory();
                startInput(player, InputType.SKILL_NAME, holder.getWeaponId(), holder.getSlotNumber(), -1);
                prompt(player, "표시 이름 (MiniMessage 가능)");
            }
            case 14 -> {
                double delta = skillDelta(clickType);
                if (delta != 0) {
                    skill.setCooldown(roundToHundredth(skill.getCooldown() + delta));
                    saveRefreshSkill(player, weapon, holder.getSlotNumber());
                }
            }
            case 15 -> {
                player.closeInventory();
                startInput(player, InputType.SKILL_DESC, holder.getWeaponId(), holder.getSlotNumber(), -1);
                prompt(player, "스킬 설명");
            }
            case 28 -> {
                double delta = skillDelta(clickType);
                if (delta != 0) {
                    skill.setDamage(roundToHundredth(skill.getDamage() + delta));
                    saveRefreshSkill(player, weapon, holder.getSlotNumber());
                }
            }
            case 29 -> {
                double delta = skillDelta(clickType);
                if (delta != 0) {
                    skill.setRatio(roundToHundredth(skill.getRatio() + delta));
                    saveRefreshSkill(player, weapon, holder.getSlotNumber());
                }
            }
            case 34 -> {
                player.closeInventory();
                startInput(player, InputType.SKILL_MODIFIER, holder.getWeaponId(), holder.getSlotNumber(), -1);
                prompt(player, "modifier를 key:value 형식으로 입력");
            }
            case 36 -> {
                plugin.getGUIManager().openWeaponDetailGUI(player, weapon);
                click(player);
            }
            default -> {
                String modifierKey = holder.getModifierKey(event.getSlot());
                if (modifierKey == null) {
                    return;
                }

                double delta = skillDelta(clickType);
                if (delta != 0) {
                    double current = skill.getModifiers().getOrDefault(modifierKey, 0.0);
                    skill.putModifier(modifierKey, roundToHundredth(Math.max(0, current + delta)));
                    saveRefreshSkill(player, weapon, holder.getSlotNumber());
                }
            }
        }
    }

    private void handlePassiveEdit(Player player, PassiveEditHolder holder, InventoryClickEvent event) {
        WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(holder.getWeaponId());
        if (weapon == null || holder.getPassiveIndex() >= weapon.getPassives().size()) {
            return;
        }

        PassiveSlot passive = weapon.getPassives().get(holder.getPassiveIndex());
        ClickType clickType = event.getClick();

        switch (event.getSlot()) {
            case 10 -> {
                passive.setEnabled(!passive.isEnabled());
                saveRefreshPassive(player, weapon, holder.getPassiveIndex());
            }
            case 11 -> {
                player.closeInventory();
                startInput(player, InputType.PASSIVE_TYPE, holder.getWeaponId(), -1, holder.getPassiveIndex());
                prompt(player, "MythicMobs 스킬 ID");
            }
            case 12 -> {
                player.closeInventory();
                startInput(player, InputType.PASSIVE_NAME, holder.getWeaponId(), -1, holder.getPassiveIndex());
                prompt(player, "표시 이름 (MiniMessage 가능)");
            }
            case 13 -> {
                passive.setTriggerType(passive.getTriggerType().next());
                saveRefreshPassive(player, weapon, holder.getPassiveIndex());
            }
            case 14 -> {
                double delta = skillDelta(clickType);
                if (delta != 0) {
                    passive.setTimer(roundToHundredth(passive.getTimer() + delta));
                    saveRefreshPassive(player, weapon, holder.getPassiveIndex());
                }
            }
            case 15 -> {
                double delta = skillDelta(clickType);
                if (delta != 0) {
                    passive.setCooldown(roundToHundredth(passive.getCooldown() + delta));
                    saveRefreshPassive(player, weapon, holder.getPassiveIndex());
                }
            }
            case 16 -> {
                player.closeInventory();
                startInput(player, InputType.PASSIVE_DESC, holder.getWeaponId(), -1, holder.getPassiveIndex());
                prompt(player, "패시브 설명");
            }
            case 36 -> {
                plugin.getGUIManager().openWeaponDetailGUI(player, weapon);
                click(player);
            }
            default -> {
                String modifierKey = holder.getModifierKey(event.getSlot());
                if (modifierKey == null) {
                    return;
                }

                double delta = skillDelta(clickType);
                if (delta != 0) {
                    double current = passive.getModifiers().getOrDefault(modifierKey, 0.0);
                    passive.putModifier(modifierKey, roundToHundredth(Math.max(0, current + delta)));
                    saveRefreshPassive(player, weapon, holder.getPassiveIndex());
                }
            }
        }
    }

    private void startInput(Player player, InputType type, String weaponId, int slot, int passive) {
        pending.put(player.getUniqueId(), new InputCtx(type, weaponId, slot, passive));
    }

    private void prompt(Player player, String label) {
        player.sendMessage(mm.deserialize("<gray>" + label + "를 입력하세요. <dark_gray>(cancel = 취소)</dark_gray></gray>"));
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        InputCtx ctx = pending.remove(player.getUniqueId());
        if (ctx == null) {
            return;
        }

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if ("cancel".equalsIgnoreCase(input)) {
            player.sendMessage(mm.deserialize("<gray>입력이 취소되었습니다.</gray>"));
            Bukkit.getScheduler().runTask(plugin, () -> reopen(player, ctx));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(ctx.weaponId);
            if (weapon == null) {
                return;
            }

            boolean updated = applyInput(player, weapon, ctx, input);
            if (updated) {
                plugin.getWeaponSkillManager().save(weapon);
                player.sendMessage(mm.deserialize("<green>변경 사항을 저장했습니다.</green>"));
            }
            reopen(player, ctx);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private boolean applyInput(Player player, WeaponSkill weapon, InputCtx ctx, String input) {
        return switch (ctx.type) {
            case SKILL_ID -> applySkillId(weapon, ctx.slot, input);
            case SKILL_NAME -> applySkillName(weapon, ctx.slot, input);
            case SKILL_DESC -> applySkillDescription(weapon, ctx.slot, input);
            case SKILL_MODIFIER -> applySkillModifierInput(player, weapon, ctx.slot, input);
            case PASSIVE_TYPE -> applyPassiveType(weapon, ctx.passive, input);
            case PASSIVE_NAME -> applyPassiveName(weapon, ctx.passive, input);
            case PASSIVE_DESC -> applyPassiveDescription(weapon, ctx.passive, input);
        };
    }

    private boolean applySkillId(WeaponSkill weapon, int slot, String input) {
        SkillSlot skill = weapon.getSkill(slot);
        if (skill == null || input.isBlank()) {
            return false;
        }

        skill.setMythicId(input.trim().toUpperCase());
        return true;
    }

    private boolean applySkillName(WeaponSkill weapon, int slot, String input) {
        SkillSlot skill = weapon.getSkill(slot);
        if (skill == null || input.isBlank()) {
            return false;
        }

        skill.setDisplayName(input.trim());
        return true;
    }

    private boolean applySkillDescription(WeaponSkill weapon, int slot, String input) {
        SkillSlot skill = weapon.getSkill(slot);
        if (skill == null) {
            return false;
        }

        skill.setDescription(input.trim());
        return true;
    }

    private boolean applyPassiveType(WeaponSkill weapon, int passiveIndex, String input) {
        if (passiveIndex < 0 || passiveIndex >= weapon.getPassives().size() || input.isBlank()) {
            return false;
        }

        weapon.getPassives().get(passiveIndex).setType(input.trim().toUpperCase());
        return true;
    }

    private boolean applyPassiveName(WeaponSkill weapon, int passiveIndex, String input) {
        if (passiveIndex < 0 || passiveIndex >= weapon.getPassives().size() || input.isBlank()) {
            return false;
        }

        weapon.getPassives().get(passiveIndex).setDisplayName(input.trim());
        return true;
    }

    private boolean applyPassiveDescription(WeaponSkill weapon, int passiveIndex, String input) {
        if (passiveIndex < 0 || passiveIndex >= weapon.getPassives().size()) {
            return false;
        }

        weapon.getPassives().get(passiveIndex).setDescription(input.trim());
        return true;
    }

    private boolean applySkillModifierInput(Player player, WeaponSkill weapon, int slot, String input) {
        SkillSlot skill = weapon.getSkill(slot);
        if (skill == null) {
            return false;
        }

        String[] split = input.split(":", 2);
        if (split.length != 2) {
            player.sendMessage(mm.deserialize("<red>modifier는 key:value 형식이어야 합니다.</red>"));
            return false;
        }

        ModifierValidator.ValidationResult keyResult = ModifierValidator.validateCustomModifierKey(split[0]);
        if (!keyResult.valid()) {
            player.sendMessage(mm.deserialize("<red>" + keyResult.message() + "</red>"));
            return false;
        }

        ModifierValidator.ValueResult valueResult = ModifierValidator.validateNumericValue(split[1]);
        if (!valueResult.valid()) {
            player.sendMessage(mm.deserialize("<red>" + valueResult.message() + "</red>"));
            return false;
        }

        skill.putModifier(keyResult.normalizedKey(), valueResult.value());
        plugin.logDebug("modifier 저장 " + weapon.getWeaponId() + " slot-" + slot
                + " " + keyResult.normalizedKey() + "=" + valueResult.value());
        return true;
    }

    private void reopen(Player player, InputCtx ctx) {
        WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(ctx.weaponId);
        if (weapon == null) {
            return;
        }

        if (ctx.slot > 0) {
            plugin.getGUIManager().openSkillEditGUI(player, weapon, ctx.slot);
            return;
        }
        if (ctx.passive >= 0) {
            plugin.getGUIManager().openPassiveEditGUI(player, weapon, ctx.passive);
            return;
        }

        plugin.getGUIManager().openWeaponDetailGUI(player, weapon);
    }

    private void saveRefreshSkill(Player player, WeaponSkill weapon, int slot) {
        plugin.getWeaponSkillManager().save(weapon);
        plugin.getGUIManager().openSkillEditGUI(player, weapon, slot);
        click(player);
    }

    private void saveRefreshPassive(Player player, WeaponSkill weapon, int passiveIndex) {
        plugin.getWeaponSkillManager().save(weapon);
        plugin.getCombatManager().refreshPassiveTimers();
        plugin.getGUIManager().openPassiveEditGUI(player, weapon, passiveIndex);
        click(player);
    }

    private double skillDelta(ClickType clickType) {
        return switch (clickType) {
            case LEFT -> 0.1;
            case RIGHT -> -0.1;
            case MIDDLE -> 0.01;
            case SHIFT_LEFT -> 1.0;
            case SHIFT_RIGHT -> -1.0;
            default -> 0;
        };
    }

    private double roundToHundredth(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    private enum InputType {
        SKILL_ID,
        SKILL_NAME,
        SKILL_DESC,
        SKILL_MODIFIER,
        PASSIVE_TYPE,
        PASSIVE_NAME,
        PASSIVE_DESC
    }

    private record InputCtx(InputType type, String weaponId, int slot, int passive) {
    }
}
