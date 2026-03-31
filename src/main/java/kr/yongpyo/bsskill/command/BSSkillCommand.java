package kr.yongpyo.bsskill.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.StringArgument;
import kr.yongpyo.bsskill.BSSkill;
import kr.yongpyo.bsskill.model.CombatState;
import kr.yongpyo.bsskill.model.PassiveSlot;
import kr.yongpyo.bsskill.model.SkillSlot;
import kr.yongpyo.bsskill.model.WeaponSkill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 관리자 명령어 등록 클래스입니다.
 * 리로드, GUI 편집기, 상태 확인, 디버그 로그 제어를 한곳에서 제공합니다.
 */
public class BSSkillCommand {

    private final BSSkill plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public BSSkillCommand(BSSkill plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
                new CommandAPICommand("bsskill").withPermission("bsskill.admin")
                .withSubcommand(new CommandAPICommand("editor").withPermission("bsskill.admin")
                        .executesPlayer((player, args) -> {
                            plugin.getGUIManager().openWeaponListGUI(player);
                        }))

                .withSubcommand(new CommandAPICommand("reload").withPermission("bsskill.admin")
                        .executes((sender, args) -> {
                            plugin.reloadConfig();
                            plugin.reloadRuntimeSettings();
                            plugin.getWeaponSkillManager().loadAll();
                            plugin.restartHudTask();
                            sender.sendMessage(mm.deserialize("<green>BSSkill 리로드가 완료되었습니다.</green>"));
                        }))

                .withSubcommand(new CommandAPICommand("list").withPermission("bsskill.admin")
                        .executes((sender, args) -> {
                            var weapons = plugin.getWeaponSkillManager().getAllWeapons();
                            if (weapons.isEmpty()) {
                                sender.sendMessage(mm.deserialize("<gray>등록된 무기가 없습니다.</gray>"));
                                return;
                            }

                            sender.sendMessage(mm.deserialize("<white>무기 목록 <gray>(" + weapons.size() + "개)</gray></white>"));
                            for (WeaponSkill weapon : weapons) {
                                sender.sendMessage(mm.deserialize(
                                        " <gray>-</gray> <white>" + weapon.getWeaponId() + "</white> "
                                                + weapon.getDisplayName()
                                                + " <gray>(" + weapon.getActiveSkillCount() + "개 액티브, "
                                                + weapon.getPassives().size() + "개 패시브)</gray>"));
                            }
                        }))

                .withSubcommand(new CommandAPICommand("info").withPermission("bsskill.admin")
                        .withArguments(new StringArgument("weapon")
                                .replaceSuggestions(ArgumentSuggestions.strings(info ->
                                        plugin.getWeaponSkillManager().getAllWeapons().stream()
                                                .map(WeaponSkill::getWeaponId)
                                                .toArray(String[]::new))))
                        .executes((sender, args) -> {
                            String id = (String) args.get("weapon");
                            WeaponSkill weapon = plugin.getWeaponSkillManager().getWeapon(id);
                            if (weapon == null) {
                                sender.sendMessage(mm.deserialize("<red>" + id + " 무기를 찾지 못했습니다.</red>"));
                                return;
                            }

                            sender.sendMessage(mm.deserialize(
                                    "<white>" + weapon.getDisplayName() + "</white> <gray>(" + weapon.getMmoType() + ")</gray>"));
                            for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
                                SkillSlot skill = weapon.getSkill(i);
                                String state = skill.isValid() ? "<green>ON</green>" : "<red>OFF</red>";
                                sender.sendMessage(mm.deserialize(
                                        " [" + state + "<white>] 슬롯 " + i + " (" + skill.getKeybindLabel() + ") </white>"
                                                + skill.getDisplayName()
                                                + " <gray>| " + skill.getMythicId()
                                                + " | damage:" + skill.getDamage()
                                                + " | cooldown:" + skill.getCooldown() + "초</gray>"
                                                + (!skill.getModifiers().isEmpty()
                                                ? " <gray>| modifiers:" + skill.getModifiers() + "</gray>"
                                                : "")));
                            }

                            for (PassiveSlot passive : weapon.getPassives()) {
                                String state = passive.isEnabled() ? "<green>ON</green>" : "<red>OFF</red>";
                                sender.sendMessage(mm.deserialize(
                                        " [" + state + "<white>] 패시브 </white>" + passive.getDisplayName()
                                                + " <gray>| " + passive.getType()
                                                + " | interval:" + passive.getTimer() + "초"
                                                + " | cooldown:" + passive.getCooldown() + "초</gray>"
                                                + (!passive.getModifiers().isEmpty()
                                                ? " <gray>| modifiers:" + passive.getModifiers() + "</gray>"
                                                : "")));
                            }
                        }))

                .withSubcommand(new CommandAPICommand("toggle").withPermission("bsskill.use")
                        .executesPlayer((player, args) -> {
                            plugin.getCombatManager().handleFKey(player);
                        }))

                .withSubcommand(new CommandAPICommand("save").withPermission("bsskill.admin")
                        .executes((sender, args) -> {
                            plugin.getWeaponSkillManager().saveAll();
                            sender.sendMessage(mm.deserialize("<green>모든 무기 설정을 저장했습니다.</green>"));
                        }))

                .withSubcommand(new CommandAPICommand("debug").withPermission("bsskill.admin")
                        .executesPlayer((player, args) -> {
                            sendDebugReport(player, player);
                        }))

                .withSubcommand(new CommandAPICommand("debuglog").withPermission("bsskill.admin")
                        .executes((sender, args) -> {
                            boolean next = !plugin.getConfig().getBoolean("debug.enabled", false);
                            plugin.getConfig().set("debug.enabled", next);
                            plugin.saveConfig();
                            plugin.reloadRuntimeSettings();
                            sender.sendMessage(mm.deserialize("<yellow>디버그 로그를 "
                                    + (next ? "<green>활성화</green>" : "<red>비활성화</red>")
                                    + "<yellow>했습니다.</yellow>"));
                        }))

                .register();
    }

    private void sendDebugReport(CommandSender viewer, Player target) {
        CombatState state = plugin.getCombatManager().getExistingState(target);
        WeaponSkill weapon = plugin.getCombatManager().getCurrentWeapon(target);

        viewer.sendMessage(mm.deserialize("<gold>--- BSSkill 디버그 ---</gold>"));
        viewer.sendMessage(Component.text("대상: ").append(target.displayName()));
        viewer.sendMessage(mm.deserialize("<gray>디버그 로그:</gray> <white>"
                + (plugin.isDebugMode() ? "활성화" : "비활성화") + "</white>"));

        if (state == null) {
            viewer.sendMessage(mm.deserialize("<gray>전투 상태가 아직 생성되지 않았습니다.</gray>"));
            return;
        }

        viewer.sendMessage(mm.deserialize("<gray>전투 모드:</gray> <white>"
                + (state.isCombatMode() ? "ON" : "OFF") + "</white>"));
        viewer.sendMessage(mm.deserialize("<gray>현재 무기:</gray> <white>"
                + safe(state.getCurrentWeaponId()) + "</white>"));
        viewer.sendMessage(mm.deserialize("<gray>고정 슬롯:</gray> <white>" + state.getWeaponSlot()
                + "</white> <gray>| 원래 슬롯:</gray> <white>" + state.getOriginalSlot() + "</white>"));
        viewer.sendMessage(mm.deserialize("<gray>스왑 여부:</gray> <white>"
                + (state.isSwapped() ? "예" : "아니오") + "</white>"
                + " <gray>| 내부 캐스팅:</gray> <white>"
                + (state.isInternalCasting() ? "예" : "아니오") + "</white>"));

        if (weapon == null && state.getCurrentWeaponId() != null) {
            weapon = plugin.getWeaponSkillManager().getWeapon(state.getCurrentWeaponId());
        }

        if (weapon != null) {
            viewer.sendMessage(mm.deserialize("<gray>무기 설정:</gray> " + weapon.getDisplayName()
                    + " <gray>(" + weapon.getWeaponId() + ")</gray>"));
        }

        for (int i = 1; i <= WeaponSkill.MAX_SLOTS; i++) {
            double remaining = state.getRemainingCooldown(i);
            SkillSlot skill = weapon != null ? weapon.getSkill(i) : null;
            String skillName = skill != null && !skill.getMythicId().isBlank()
                    ? skill.getMythicId()
                    : "비어있음";
            viewer.sendMessage(mm.deserialize("<gray>슬롯 " + i + ":</gray> <white>" + skillName + "</white>"
                    + " <gray>| cooldown:</gray> <white>" + String.format("%.2f", remaining) + "초</white>"));
        }

        if (weapon != null && !weapon.getPassives().isEmpty()) {
            for (PassiveSlot passive : weapon.getPassives()) {
                double remaining = state.getRemainingCooldown(100 + passive.getIndex());
                viewer.sendMessage(mm.deserialize("<gray>패시브 " + passive.getIndex() + ":</gray> <white>"
                        + passive.getType() + "</white> <gray>| cooldown:</gray> <white>"
                        + String.format("%.2f", remaining) + "초</white>"));
            }
        }

        var snapshot = state.getCooldownSnapshot();
        if (snapshot.isEmpty()) {
            viewer.sendMessage(mm.deserialize("<gray>현재 활성화된 쿨타임이 없습니다.</gray>"));
            return;
        }

        viewer.sendMessage(mm.deserialize("<gray>활성 쿨타임 키:</gray> <white>" + snapshot + "</white>"));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "없음" : value;
    }
}
