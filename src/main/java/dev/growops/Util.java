package dev.growops;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class Util {
    private Util() {
    }

    public static void give(Player p, ItemStack it) {
        if (it == null) {
            return;
        }
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (ItemStack l : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), l);
        }
    }

    public static void consumeMainHand(Player p, int n) {
        ItemStack h = p.getInventory().getItemInMainHand();
        if (h.getAmount() <= n) {
            p.getInventory().setItemInMainHand(null);
        } else {
            h.setAmount(h.getAmount() - n);
            p.getInventory().setItemInMainHand(h);
        }
    }

    public static void bar(Player p, String mini) {
        p.sendActionBar(Text.c(mini));
    }
}
