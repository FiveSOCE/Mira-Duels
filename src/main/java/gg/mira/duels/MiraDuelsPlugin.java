package gg.mira.duels;

import com.mira.core.api.MiraCore;
import com.mira.core.api.MiraCoreProvider;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class MiraDuelsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Challenge> challenges = new HashMap<>();
    private final Map<UUID, Duel> active = new HashMap<>();
    private MiraCore core;
    private File kitsFile;
    private File arenaFile;
    private YamlConfiguration kits;
    private YamlConfiguration arena;

    @Override
    public void onEnable() {
        core = MiraCoreProvider.require();
        kitsFile = new File(getDataFolder(), "kits.yml");
        arenaFile = new File(getDataFolder(), "arena.yml");
        kits = YamlConfiguration.loadConfiguration(kitsFile);
        arena = YamlConfiguration.loadConfiguration(arenaFile);

        register("duel");
        register("duelsadmin");
        getServer().getPluginManager().registerEvents(this, this);
        core.modules().register(this, "MiraDuels");
    }

    private void register(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException(name + " command missing");
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        for (Duel duel : new HashSet<>(active.values())) finish(duel, null, "&cDuel cancelled because MiraDuels disabled.");
        active.clear();
        challenges.clear();
        if (core != null) core.modules().unregister(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            core.messages().send(sender, "&cPlayers only.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("duelsadmin")) return admin(player, args);
        return duelCommand(player, args);
    }

    private boolean duelCommand(Player player, String[] args) {
        if (args.length == 0) {
            core.messages().send(player, "&7/duel <player> [kit] &8| &7/duel accept <player> &8| &7/duel deny <player> &8| &7/duel kits");
            return true;
        }
        if (args[0].equalsIgnoreCase("kits")) {
            Set<String> names = kitNames();
            core.messages().send(player, "&dDuel kits: &f" + (names.isEmpty() ? "None configured" : String.join(", ", names)));
            return true;
        }
        if (args[0].equalsIgnoreCase("accept")) return accept(player, args);
        if (args[0].equalsIgnoreCase("deny")) return deny(player, args);

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) return msg(player, "&cThat player is not online.");
        if (target.equals(player)) return msg(player, "&cYou cannot duel yourself.");
        if (active.containsKey(player.getUniqueId()) || active.containsKey(target.getUniqueId())) return msg(player, "&cOne of you is already in a duel.");
        if (!arenaReady()) return msg(player, "&cThe duel arena is not configured.");

        String kit = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : defaultKit();
        if (kit == null || !kits.isConfigurationSection("kits." + kit)) return msg(player, "&cUnknown duel kit. Use /duel kits.");

        long expires = System.currentTimeMillis() + Math.max(10L, getConfig().getLong("challenge-seconds", 60L)) * 1000L;
        Challenge challenge = new Challenge(player.getUniqueId(), target.getUniqueId(), kit, expires);
        challenges.put(target.getUniqueId(), challenge);
        core.messages().send(player, "&aDuel request sent to &f" + target.getName() + "&a with kit &f" + kit + "&a.");
        core.messages().send(target, "&d" + player.getName() + " &7challenged you to a duel with kit &f" + kit
                + "&7. Use &f/duel accept " + player.getName() + " &7or &f/duel deny " + player.getName() + "&7.");
        return true;
    }

    private boolean accept(Player target, String[] args) {
        Challenge challenge = challenges.get(target.getUniqueId());
        if (challenge == null || challenge.expiresAt() < System.currentTimeMillis()) {
            challenges.remove(target.getUniqueId());
            return msg(target, "&cYou do not have an active duel request.");
        }
        Player challenger = Bukkit.getPlayer(challenge.challenger());
        if (challenger == null || !challenger.isOnline()) {
            challenges.remove(target.getUniqueId());
            return msg(target, "&cThat challenger is no longer online.");
        }
        if (args.length >= 2 && !challenger.getName().equalsIgnoreCase(args[1])) return msg(target, "&cThat is not your current challenger.");
        challenges.remove(target.getUniqueId());
        start(challenger, target, challenge.kit());
        return true;
    }

    private boolean deny(Player target, String[] args) {
        Challenge challenge = challenges.get(target.getUniqueId());
        if (challenge == null) return msg(target, "&cYou do not have an active duel request.");
        Player challenger = Bukkit.getPlayer(challenge.challenger());
        if (args.length >= 2 && challenger != null && !challenger.getName().equalsIgnoreCase(args[1])) return msg(target, "&cThat is not your current challenger.");
        challenges.remove(target.getUniqueId());
        core.messages().send(target, "&7Duel request denied.");
        if (challenger != null) core.messages().send(challenger, "&c" + target.getName() + " denied your duel request.");
        return true;
    }

    private void start(Player one, Player two, String kit) {
        PlayerState stateOne = PlayerState.capture(one);
        PlayerState stateTwo = PlayerState.capture(two);
        Duel duel = new Duel(UUID.randomUUID(), one.getUniqueId(), two.getUniqueId(), kit, stateOne, stateTwo);
        active.put(one.getUniqueId(), duel);
        active.put(two.getUniqueId(), duel);

        prepare(one, kit, location("spawn1"));
        prepare(two, kit, location("spawn2"));
        core.messages().send(one, "&aDuel started against &f" + two.getName() + "&a. Kit: &f" + kit + "&a.");
        core.messages().send(two, "&aDuel started against &f" + one.getName() + "&a. Kit: &f" + kit + "&a.");
    }

    private void prepare(Player player, String kit, Location spawn) {
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);

        ItemStack[] contents = loadItems("kits." + kit + ".contents", 41);
        for (int i = 0; i < Math.min(36, contents.length); i++) player.getInventory().setItem(i, clone(contents[i]));
        if (contents.length >= 40) {
            player.getInventory().setBoots(clone(contents[36]));
            player.getInventory().setLeggings(clone(contents[37]));
            player.getInventory().setChestplate(clone(contents[38]));
            player.getInventory().setHelmet(clone(contents[39]));
        }
        if (contents.length >= 41) player.getInventory().setItemInOffHand(clone(contents[40]));

        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20F);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.teleport(spawn);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDuelPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Duel duel = active.get(victim.getUniqueId());
        if (duel == null) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player player) attacker = player;
        else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) attacker = player;

        UUID opponentId = duel.one().equals(victim.getUniqueId()) ? duel.two() : duel.one();
        if (attacker == null || !attacker.getUniqueId().equals(opponentId)) {
            event.setCancelled(true);
            return;
        }

        // Registered duel opponents are allowed to fight even if normal territory
        // protection would block PvP at the configured duel arena.
        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Duel duel = active.get(victim.getUniqueId());
        if (duel == null) return;

        if (event.getFinalDamage() < victim.getHealth()) return;
        event.setCancelled(true);
        UUID opponentId = duel.one().equals(victim.getUniqueId()) ? duel.two() : duel.one();
        Player winner = Bukkit.getPlayer(opponentId);
        finish(duel, winner, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (active.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player leaving = event.getPlayer();
        Duel duel = active.get(leaving.getUniqueId());
        if (duel == null) return;
        UUID opponentId = duel.one().equals(leaving.getUniqueId()) ? duel.two() : duel.one();
        Player winner = Bukkit.getPlayer(opponentId);
        finish(duel, winner, "&cOpponent disconnected.");
    }

    private void finish(Duel duel, Player winner, String reason) {
        active.remove(duel.one());
        active.remove(duel.two());
        Player one = Bukkit.getPlayer(duel.one());
        Player two = Bukkit.getPlayer(duel.two());

        if (one != null) duel.stateOne().restore(one);
        if (two != null) duel.stateTwo().restore(two);

        if (winner != null) {
            core.messages().send(winner, "&aYou won the duel!");
            Player loser = winner.getUniqueId().equals(duel.one()) ? two : one;
            if (loser != null) core.messages().send(loser, "&cYou lost the duel.");
        }
        if (reason != null) {
            if (one != null) core.messages().send(one, reason);
            if (two != null) core.messages().send(two, reason);
        }
    }

    private boolean admin(Player player, String[] args) {
        if (!player.hasPermission("miraduels.admin")) return msg(player, "&cNo permission.");
        if (args.length < 1) return msg(player, "&7/duelsadmin setkit <name> &8| &7delkit <name> &8| &7setspawn <1|2>");

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setkit" -> {
                if (args.length < 2) return msg(player, "&cUsage: /duelsadmin setkit <name>");
                String id = args[1].toLowerCase(Locale.ROOT);
                List<ItemStack> saved = new ArrayList<>(41);
                for (ItemStack item : player.getInventory().getStorageContents()) saved.add(clone(item));
                saved.add(clone(player.getInventory().getBoots()));
                saved.add(clone(player.getInventory().getLeggings()));
                saved.add(clone(player.getInventory().getChestplate()));
                saved.add(clone(player.getInventory().getHelmet()));
                saved.add(clone(player.getInventory().getItemInOffHand()));
                kits.set("kits." + id + ".contents", saved);
                save(kits, kitsFile);
                return msg(player, "&aSaved duel kit &f" + id + "&a.");
            }
            case "delkit" -> {
                if (args.length < 2) return msg(player, "&cUsage: /duelsadmin delkit <name>");
                kits.set("kits." + args[1].toLowerCase(Locale.ROOT), null);
                save(kits, kitsFile);
                return msg(player, "&aDeleted duel kit.");
            }
            case "setspawn" -> {
                if (args.length < 2 || (!args[1].equals("1") && !args[1].equals("2"))) return msg(player, "&cUsage: /duelsadmin setspawn <1|2>");
                setLocation("spawn" + args[1], player.getLocation());
                save(arena, arenaFile);
                return msg(player, "&aSet duel arena spawn " + args[1] + ".");
            }
            default -> { return msg(player, "&cUnknown admin action."); }
        }
    }

    private boolean arenaReady() { return location("spawn1") != null && location("spawn2") != null; }

    private void setLocation(String path, Location loc) {
        arena.set(path + ".world", loc.getWorld().getName());
        arena.set(path + ".x", loc.getX());
        arena.set(path + ".y", loc.getY());
        arena.set(path + ".z", loc.getZ());
        arena.set(path + ".yaw", loc.getYaw());
        arena.set(path + ".pitch", loc.getPitch());
    }

    private Location location(String path) {
        String worldName = arena.getString(path + ".world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, arena.getDouble(path + ".x"), arena.getDouble(path + ".y"), arena.getDouble(path + ".z"),
                (float) arena.getDouble(path + ".yaw"), (float) arena.getDouble(path + ".pitch"));
    }

    @SuppressWarnings("unchecked")
    private ItemStack[] loadItems(String path, int size) {
        List<?> raw = kits.getList(path, List.of());
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < Math.min(size, raw.size()); i++) if (raw.get(i) instanceof ItemStack item) items[i] = item.clone();
        return items;
    }

    private String defaultKit() {
        Set<String> names = kitNames();
        return names.isEmpty() ? null : names.iterator().next();
    }

    private Set<String> kitNames() {
        var section = kits.getConfigurationSection("kits");
        return section == null ? Set.of() : new TreeSet<>(String.CASE_INSENSITIVE_ORDER) {{ addAll(section.getKeys(false)); }};
    }

    private void save(YamlConfiguration yaml, File file) {
        try { file.getParentFile().mkdirs(); yaml.save(file); }
        catch (IOException ex) { getLogger().severe("Could not save " + file.getName() + ": " + ex.getMessage()); }
    }

    private boolean msg(CommandSender sender, String message) { core.messages().send(sender, message); return true; }
    private static ItemStack clone(ItemStack item) { return item == null ? null : item.clone(); }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("duelsadmin")) {
            if (args.length == 1) return complete(args[0], List.of("setkit","delkit","setspawn"));
            if (args.length == 2 && args[0].equalsIgnoreCase("delkit")) return complete(args[1], kitNames());
            if (args.length == 2 && args[0].equalsIgnoreCase("setspawn")) return complete(args[1], List.of("1","2"));
            return List.of();
        }
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("accept","deny","kits"));
            values.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return complete(args[0], values);
        }
        if (args.length == 2 && Bukkit.getPlayerExact(args[0]) != null) return complete(args[1], kitNames());
        return List.of();
    }

    private static List<String> complete(String prefix, Collection<String> values) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lower)).distinct().sorted().toList();
    }

    private record Challenge(UUID challenger, UUID target, String kit, long expiresAt) {}
    private record Duel(UUID id, UUID one, UUID two, String kit, PlayerState stateOne, PlayerState stateTwo) {}

    private record PlayerState(ItemStack[] storage, ItemStack[] armor, ItemStack offhand, Location location,
                               GameMode gameMode, double health, int food, float saturation, int level, float exp) {
        static PlayerState capture(Player p) {
            return new PlayerState(cloneArray(p.getInventory().getStorageContents()), cloneArray(p.getInventory().getArmorContents()),
                    clone(p.getInventory().getItemInOffHand()), p.getLocation().clone(), p.getGameMode(), p.getHealth(),
                    p.getFoodLevel(), p.getSaturation(), p.getLevel(), p.getExp());
        }
        void restore(Player p) {
            p.getInventory().clear();
            p.getInventory().setStorageContents(cloneArray(storage));
            p.getInventory().setArmorContents(cloneArray(armor));
            p.getInventory().setItemInOffHand(clone(offhand));
            p.setGameMode(gameMode);
            p.setHealth(Math.min(health, p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
            p.setFoodLevel(food);
            p.setSaturation(saturation);
            p.setLevel(level);
            p.setExp(exp);
            if (location != null && location.getWorld() != null) p.teleport(location);
            p.setFireTicks(0);
        }
        private static ItemStack[] cloneArray(ItemStack[] input) {
            ItemStack[] copy = new ItemStack[input.length];
            for (int i = 0; i < input.length; i++) copy[i] = clone(input[i]);
            return copy;
        }
    }
}
