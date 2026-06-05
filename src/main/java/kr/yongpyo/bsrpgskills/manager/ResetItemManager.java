package kr.yongpyo.bsrpgskills.manager;

import kr.yongpyo.bsrpgskills.BSRpgSkills;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 스킬 초기화 아이템의 정의(config.yml)를 읽어 생성/식별하는 매니저입니다.
 * PDC 태그로 식별하므로 이름/모양을 바꿔도 동작합니다.
 */
public class ResetItemManager {

    private final BSRpgSkills plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final NamespacedKey tagKey;

    private Material material = Material.NETHER_STAR;
    private int customModelData = 0;
    private NamespacedKey tooltipStyle = null;
    private String name = "<gradient:red:gold>스킬 초기화 주문서</gradient>";
    private List<String> lore = List.of("<gray>우클릭 시 모든 스킬 레벨 초기화 + 포인트 환불</gray>");
    private boolean consume = true;

    public ResetItemManager(BSRpgSkills plugin) {
        this.plugin = plugin;
        this.tagKey = new NamespacedKey(plugin, "reset_item");
        reload();
    }

    public void reload() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("reset-item");
        if (cfg == null) {
            return;
        }

        Material parsed = Material.matchMaterial(cfg.getString("material", "NETHER_STAR"));
        if (parsed != null) {
            material = parsed;
        }
        customModelData = cfg.getInt("custom-model-data", 0);

        String style = cfg.getString("tooltip-style", "");
        tooltipStyle = (style == null || style.isBlank()) ? null : NamespacedKey.fromString(style);

        name = cfg.getString("name", name);
        if (cfg.isList("lore")) {
            lore = cfg.getStringList("lore");
        }
        consume = cfg.getBoolean("consume", true);
    }

    public ItemStack build(int amount) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        meta.displayName(mm.deserialize(name).decoration(TextDecoration.ITALIC, false));

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(mm.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);

        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        if (tooltipStyle != null) {
            meta.setTooltipStyle(tooltipStyle);
        }

        meta.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isResetItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte flag = meta.getPersistentDataContainer().get(tagKey, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    public boolean isConsume() {
        return consume;
    }
}
