package dev.growops;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GrowOpsCommand implements CommandExecutor, TabCompleter {
    private final GrowOpsPlugin plugin;

    public GrowOpsCommand(GrowOpsPlugin plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String m) {
        s.sendMessage(Text.c(m));
    }

    private Block looking(Player p) {
        return p.getTargetBlockExact(6, FluidCollisionMode.NEVER);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            help(s);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("hud")) {
            if (!(s instanceof Player p)) {
                return true;
            }
            msg(s, plugin.hud().toggle(p) ? "<green>HUD enabled." : "<gray>HUD disabled.");
            return true;
        }
        if (!s.hasPermission("growops.admin")) {
            msg(s, "<red>You don't have permission.");
            return true;
        }
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                msg(s, "<green>Reloaded config, strains and items.");
            }
            case "give" -> give(s, a);
            case "strains" -> {
                for (Strain st : plugin.strains().all()) {
                    msg(s, "<green>" + st.id() + " <gray>- " + st.name() + ", " + st.growMinutes() + " min"
                            + (st.requiresOptimal() ? ", needs tent" : ""));
                }
            }
            case "items" -> msg(s, "<gray>" + String.join(", ", plugin.items().keys()));
            case "fixed" -> fixed(s, a);
            case "lockup" -> lockup(s, a);
            case "op" -> op(s, a);
            case "save" -> {
                plugin.saveAll();
                msg(s, "<green>Saved.");
            }
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        msg(s, "<green>GrowOps <gray>commands:");
        msg(s, "<white>/growops hud <gray>- toggle the pot status readout");
        if (!s.hasPermission("growops.admin")) {
            return;
        }
        msg(s, "<white>/growops give <item> [amount] [player] [strain] <gray>- strain only for bud, weed_bag, ground, joint, brick");
        msg(s, "<white>/growops items | strains | reload | save");
        msg(s, "<white>/growops fixed <pot|tent|waterer|processing|press|lockup> <gray>- place a permanent station where you look (above the block)");
        msg(s, "<white>/growops lockup <add|remove> <player> <gray>- lockup you look at");
        msg(s, "<white>/growops op create|delete|list <id>");
        msg(s, "<white>/growops op door1|door2|switch|light|unlight|perm <id> [permission]");
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 2) {
            msg(s, "<red>/growops give <item> [amount] [player]");
            return;
        }
        String key = a[1].toLowerCase(Locale.ROOT);
        if (!plugin.items().exists(key)) {
            msg(s, "<red>Unknown item. Use /growops items.");
            return;
        }
        int amount = 1;
        if (a.length >= 3) {
            try {
                amount = Math.max(1, Math.min(2304, Integer.parseInt(a[2])));
            } catch (NumberFormatException ex) {
                msg(s, "<red>Amount must be a number.");
                return;
            }
        }
        Player target = a.length >= 4 ? Bukkit.getPlayerExact(a[3]) : (s instanceof Player p ? p : null);
        if (target == null) {
            msg(s, "<red>Player not found.");
            return;
        }
        ItemStack it;
        if (List.of("bud", "weed_bag", "ground", "joint", "brick").contains(key)) {
            String strain = a.length >= 5 ? a[4].toLowerCase(Locale.ROOT)
                    : plugin.strains().all().stream().findFirst().map(Strain::id).orElse("unknown");
            if (plugin.strains().get(strain) == null) {
                msg(s, "<red>Unknown strain. Use /growops strains.");
                return;
            }
            it = plugin.items().product(key, amount, strain, 70);
        } else {
            it = plugin.items().create(key, amount);
        }
        Util.give(target, it);
        msg(s, "<green>Gave " + amount + "x " + key + " to " + target.getName());
    }

    private void fixed(CommandSender s, String[] a) {
        if (!(s instanceof Player p) || a.length < 2) {
            msg(s, "<red>/growops fixed <type> (as a player)");
            return;
        }
        StationType type;
        try {
            type = StationType.valueOf(a[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            msg(s, "<red>Types: pot, tent, waterer, processing, press, lockup");
            return;
        }
        Block b = looking(p);
        if (b == null) {
            msg(s, "<red>Look at a block.");
            return;
        }
        Block at = b.getRelative(org.bukkit.block.BlockFace.UP);
        if (plugin.stations().get(at) != null) {
            msg(s, "<red>There is already a station here.");
            return;
        }
        plugin.stations().create(type, at, null, true);
        msg(s, "<green>Placed a permanent " + type.label + ".");
    }

    private void lockup(CommandSender s, String[] a) {
        if (!(s instanceof Player p) || a.length < 3) {
            msg(s, "<red>/growops lockup <add|remove> <player>");
            return;
        }
        Block b = looking(p);
        Station st = b == null ? null : plugin.stations().get(b);
        if (st == null || st.type != StationType.LOCKUP) {
            msg(s, "<red>Look at a lockup.");
            return;
        }
        var target = Bukkit.getOfflinePlayer(a[2]);
        if (a[1].equalsIgnoreCase("add")) {
            st.members.add(target.getUniqueId());
            msg(s, "<green>" + a[2] + " can open this lockup.");
        } else {
            st.members.remove(target.getUniqueId());
            msg(s, "<gray>" + a[2] + " removed.");
        }
    }

    private void op(CommandSender s, String[] a) {
        if (a.length < 2) {
            msg(s, "<red>/growops op <create|delete|list|door1|door2|switch|light|unlight|perm> ...");
            return;
        }
        String act = a[1].toLowerCase(Locale.ROOT);
        if (act.equals("list")) {
            for (GrowOp op : plugin.ops().all()) {
                msg(s, "<green>" + op.id + " <gray>door:" + (op.hasDoor() ? op.doorVolume() + " blocks" : "unset")
                        + " lights:" + op.lights.size() + " perm:" + (op.permission == null ? "none" : op.permission));
            }
            return;
        }
        if (a.length < 3) {
            msg(s, "<red>Missing grow-op id.");
            return;
        }
        String id = a[2];
        if (act.equals("create")) {
            if (!(s instanceof Player p)) {
                return;
            }
            if (plugin.ops().get(id) != null) {
                msg(s, "<red>That id exists.");
                return;
            }
            plugin.ops().create(id, p.getWorld().getUID());
            msg(s, "<green>Grow-op created. Set door1/door2 (opposite corners of the container door), switch and lights.");
            return;
        }
        if (act.equals("delete")) {
            msg(s, plugin.ops().delete(id) ? "<gray>Deleted." : "<red>Unknown grow-op.");
            return;
        }
        GrowOp op = plugin.ops().get(id);
        if (op == null) {
            msg(s, "<red>Unknown grow-op.");
            return;
        }
        if (act.equals("perm")) {
            op.permission = a.length >= 4 && !a[3].equalsIgnoreCase("none") ? a[3] : null;
            msg(s, "<green>Permission: " + (op.permission == null ? "none (everyone)" : op.permission)
                    + " <gray>(use a LuckPerms group permission for job restriction)");
            return;
        }
        if (!(s instanceof Player p)) {
            return;
        }
        Block b = looking(p);
        if (b == null) {
            msg(s, "<red>Look at a block.");
            return;
        }
        BlockKey k = BlockKey.of(b);
        switch (act) {
            case "door1" -> op.door1 = k;
            case "door2" -> op.door2 = k;
            case "switch" -> op.switchPos = k;
            case "light" -> {
                if (!(b.getBlockData() instanceof org.bukkit.block.data.Lightable)) {
                    msg(s, "<red>That block can't be switched (use redstone lamps, lanterns are not supported).");
                    return;
                }
                op.lights.add(k);
            }
            case "unlight" -> op.lights.remove(k);
            default -> {
                help(s);
                return;
            }
        }
        if (act.startsWith("door") && op.hasDoor() && op.doorVolume() > plugin.getConfig().getInt("growop.max-door-blocks", 300)) {
            msg(s, "<red>The door area is bigger than growop.max-door-blocks.");
        }
        msg(s, "<green>Updated " + act + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.add("hud");
            if (s.hasPermission("growops.admin")) {
                out.addAll(List.of("give", "items", "strains", "reload", "save", "fixed", "lockup", "op"));
            }
        } else if (s.hasPermission("growops.admin")) {
            if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
                out.addAll(plugin.items().keys());
            } else if (a.length == 2 && a[0].equalsIgnoreCase("fixed")) {
                for (StationType t : StationType.values()) {
                    out.add(t.name().toLowerCase(Locale.ROOT));
                }
            } else if (a.length == 2 && a[0].equalsIgnoreCase("lockup")) {
                out.addAll(List.of("add", "remove"));
            } else if (a.length == 2 && a[0].equalsIgnoreCase("op")) {
                out.addAll(List.of("create", "delete", "list", "door1", "door2", "switch", "light", "unlight", "perm"));
            } else if (a.length == 3 && a[0].equalsIgnoreCase("op")) {
                plugin.ops().all().forEach(o -> out.add(o.id));
            } else if (a.length == 4 && a[0].equalsIgnoreCase("give")) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (a.length == 5 && a[0].equalsIgnoreCase("give")) {
                plugin.strains().all().forEach(st -> out.add(st.id()));
            }
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
