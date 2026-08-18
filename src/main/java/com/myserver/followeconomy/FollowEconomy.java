package com.myserver.followeconomy;

import me.gypopo.economyshopgui.api.events.PostTransactionEvent;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.util.Transaction;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class FollowEconomy extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private File followingFile;
    private YamlConfiguration followingConfig;

    /*
     * follower UUID -> players they follow
     */
    private final Map<UUID, Set<UUID>> following = new HashMap<>();

    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");

    @Override
    public void onEnable() {
        saveDefaultConfig();

        loadFollowing();

        getServer().getPluginManager().registerEvents(this, this);

        registerCommand("follow");
        registerCommand("unfollow");
        registerCommand("following");
        registerCommand("followers");

        if (Bukkit.getPluginManager().getPlugin("EconomyShopGUI") != null
                || Bukkit.getPluginManager().getPlugin("EconomyShopGUI-Premium") != null) {

            getLogger().info("EconomyShopGUI detected.");
        } else {
            getLogger().warning("EconomyShopGUI was not detected.");
        }

        getLogger().info("FollowEconomy enabled!");
    }

    @Override
    public void onDisable() {
        saveFollowing();
    }

    private void registerCommand(String name) {
        Command command = getCommand(name);

        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml!");
            return;
        }

        command.setExecutor(this);

        if (name.equalsIgnoreCase("follow") || name.equalsIgnoreCase("unfollow")) {
            command.setTabCompleter(this);
        }
    }

    // =========================================================
    // COMMANDS
    // =========================================================

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cOnly players can use this command."));
            return true;
        }

        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("follow")) {
            return handleFollow(player, args);
        }

        if (commandName.equals("unfollow")) {
            return handleUnfollow(player, args);
        }

        if (commandName.equals("following")) {
            return handleFollowing(player);
        }

        if (commandName.equals("followers")) {
            return handleFollowers(player);
        }

        return false;
    }

    private boolean handleFollow(Player player, String[] args) {

        if (args.length != 1) {
            player.sendMessage(color("&eUsage: &f/follow <player>"));
            return true;
        }

        OfflinePlayer target = findPlayer(args[0]);

        if (target == null) {
            player.sendMessage(color("&cThat player could not be found."));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(color("&cYou cannot follow yourself."));
            return true;
        }

        UUID followerUUID = player.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        Set<UUID> targets = following.computeIfAbsent(
                followerUUID,
                ignored -> new HashSet<>()
        );

        if (!targets.add(targetUUID)) {
            player.sendMessage(color(
                    "&eYou are already following &f"
                            + getPlayerName(target)
                            + "&e."
            ));
            return true;
        }

        saveFollowing();

        player.sendMessage(color(
                "&a✓ You are now following &f"
                        + getPlayerName(target)
                        + "&a."
        ));

        return true;
    }

    private boolean handleUnfollow(Player player, String[] args) {

        if (args.length != 1) {
            player.sendMessage(color("&eUsage: &f/unfollow <player>"));
            return true;
        }

        OfflinePlayer target = findPlayer(args[0]);

        if (target == null) {
            player.sendMessage(color("&cThat player could not be found."));
            return true;
        }

        UUID followerUUID = player.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        Set<UUID> targets = following.get(followerUUID);

        if (targets == null || !targets.remove(targetUUID)) {
            player.sendMessage(color(
                    "&eYou are not following &f"
                            + getPlayerName(target)
                            + "&e."
            ));
            return true;
        }

        if (targets.isEmpty()) {
            following.remove(followerUUID);
        }

        saveFollowing();

        player.sendMessage(color(
                "&a✓ You unfollowed &f"
                        + getPlayerName(target)
                        + "&a."
        ));

        return true;
    }

    private boolean handleFollowing(Player player) {

        Set<UUID> targets = following.getOrDefault(
                player.getUniqueId(),
                Collections.emptySet()
        );

        if (targets.isEmpty()) {
            player.sendMessage(color("&eYou are not following anyone."));
            return true;
        }

        player.sendMessage(color("&6&lYour Following"));

        for (UUID uuid : targets) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);

            player.sendMessage(color(
                    "&7• &f" + getPlayerName(target)
            ));
        }

        return true;
    }

    private boolean handleFollowers(Player player) {

        UUID targetUUID = player.getUniqueId();

        List<UUID> followers = new ArrayList<>();

        for (Map.Entry<UUID, Set<UUID>> entry : following.entrySet()) {

            if (entry.getValue().contains(targetUUID)) {
                followers.add(entry.getKey());
            }
        }

        if (followers.isEmpty()) {
            player.sendMessage(color("&eYou don't have any followers."));
            return true;
        }

        player.sendMessage(color("&6&lYour Followers"));

        for (UUID uuid : followers) {

            OfflinePlayer follower = Bukkit.getOfflinePlayer(uuid);

            player.sendMessage(color(
                    "&7• &f" + getPlayerName(follower)
            ));
        }

        return true;
    }

    // =========================================================
    // ECONOMYSHOPGUI
    // =========================================================

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onPostTransaction(PostTransactionEvent event) {

        Transaction.Result result = event.getTransactionResult();

        if (result != Transaction.Result.SUCCESS
                && result != Transaction.Result.SUCCESS_COMMANDS_EXECUTED) {
            return;
        }

        Player trader = event.getPlayer();

        if (trader == null) {
            return;
        }

        List<Player> onlineFollowers = getOnlineFollowers(trader.getUniqueId());

        if (onlineFollowers.isEmpty()) {
            return;
        }

        Transaction.Type type = event.getTransactionType();

        boolean selling = isSelling(type);

        Map<ShopItem, Integer> items = event.getItems();

        /*
         * EconomyShopGUI supplies multiple items for transactions
         * such as sell-all. Normal buy/sell transactions use one item.
         */
        boolean multipleItems = items != null && items.size() > 1;

        double totalPrice = event.getPrice();

        if (multipleItems && event.getPrices() != null) {
            totalPrice = event.getPrices()
                    .values()
                    .stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
        }

        String message;

        if (multipleItems) {

            if (selling) {
                message = getConfig().getString(
                        "messages.multiple-sell",
                        "&8[&6Follow&8] &e%player% &7sold multiple items for &a$%money%"
                );
            } else {
                message = getConfig().getString(
                        "messages.multiple-buy",
                        "&8[&6Follow&8] &e%player% &7bought multiple items for &a$%money%"
                );
            }

        } else {

            ShopItem shopItem = event.getShopItem();

            String itemName = getItemName(shopItem);

            if (selling) {

                message = getConfig().getString(
                        "messages.single-sell",
                        "&8[&6Follow&8] &e%player% &7sold &f%amount%x %item% &7for &a$%money%"
                );

            } else {

                message = getConfig().getString(
                        "messages.single-buy",
                        "&8[&6Follow&8] &e%player% &7bought &f%amount%x %item% &7for &a$%money%"
                );
            }

            message = message
                    .replace("%item%", itemName)
                    .replace("%amount%", String.valueOf(event.getAmount()));
        }

        message = message
                .replace("%player%", trader.getName())
                .replace("%money%", moneyFormat.format(totalPrice));

        message = color(message);

        for (Player follower : onlineFollowers) {
            follower.sendMessage(message);
        }
    }

    private List<Player> getOnlineFollowers(UUID traderUUID) {

        List<Player> followers = new ArrayList<>();

        for (Map.Entry<UUID, Set<UUID>> entry : following.entrySet()) {

            if (!entry.getValue().contains(traderUUID)) {
                continue;
            }

            Player follower = Bukkit.getPlayer(entry.getKey());

            if (follower != null && follower.isOnline()) {
                followers.add(follower);
            }
        }

        return followers;
    }

    private boolean isSelling(Transaction.Type type) {

        return switch (type) {

            case SELL_GUI_SCREEN,
                 SELL_ALL_COMMAND,
                 SELL_ALL_SCREEN,
                 SELL_SCREEN,
                 QUICK_SELL,
                 SHOPSTAND_SELL_SCREEN,
                 API_SELL,
                 AUTO_SELL_CHEST -> true;

            default -> false;
        };
    }

    // =========================================================
    // PLAYER HELPERS
    // =========================================================

    private OfflinePlayer findPlayer(String name) {

        Player online = Bukkit.getPlayerExact(name);

        if (online != null) {
            return online;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {

            if (offline.getName() != null
                    && offline.getName().equalsIgnoreCase(name)) {

                return offline;
            }
        }

        return null;
    }

    private String getPlayerName(OfflinePlayer player) {

        if (player.getName() != null) {
            return player.getName();
        }

        return player.getUniqueId().toString();
    }

    private String getItemName(ShopItem shopItem) {

        if (shopItem == null || shopItem.getItemToGive() == null) {
            return "item";
        }

        String materialName = shopItem
                .getItemToGive()
                .getType()
                .name()
                .toLowerCase(Locale.ROOT)
                .replace("_", " ");

        return Arrays.stream(materialName.split(" "))
                .map(word -> {
                    if (word.isEmpty()) {
                        return word;
                    }

                    return word.substring(0, 1).toUpperCase(Locale.ROOT)
                            + word.substring(1);
                })
                .collect(Collectors.joining(" "));
    }

    // =========================================================
    // TAB COMPLETION
    // =========================================================

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {

        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (!command.getName().equalsIgnoreCase("follow")
                && !command.getName().equalsIgnoreCase("unfollow")) {

            return Collections.emptyList();
        }

        if (args.length != 1) {
            return Collections.emptyList();
        }

        String input = args[0].toLowerCase(Locale.ROOT);

        Set<String> names = new HashSet<>();

        if (command.getName().equalsIgnoreCase("follow")) {

            for (Player online : Bukkit.getOnlinePlayers()) {

                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    names.add(online.getName());
                }
            }

        } else {

            Set<UUID> targets = following.getOrDefault(
                    player.getUniqueId(),
                    Collections.emptySet()
            );

            for (UUID uuid : targets) {

                OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);

                if (target.getName() != null) {
                    names.add(target.getName());
                }
            }
        }

        return names.stream()
                .filter(name ->
                        name.toLowerCase(Locale.ROOT)
                                .startsWith(input)
                )
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    // =========================================================
    // FOLLOWING SAVE / LOAD
    // =========================================================

    private void loadFollowing() {

        followingFile = new File(
                getDataFolder(),
                "following.yml"
        );

        if (!followingFile.exists()) {

            try {

                if (!getDataFolder().exists()
                        && !getDataFolder().mkdirs()) {

                    getLogger().warning(
                            "Could not create plugin data folder."
                    );
                }

                if (!followingFile.createNewFile()) {
                    getLogger().warning(
                            "Could not create following.yml."
                    );
                }

            } catch (IOException exception) {

                getLogger().severe(
                        "Could not create following.yml: "
                                + exception.getMessage()
                );
            }
        }

        followingConfig =
                YamlConfiguration.loadConfiguration(followingFile);

        if (followingConfig.getConfigurationSection("following") == null) {
            return;
        }

        for (String followerString :
                followingConfig
                        .getConfigurationSection("following")
                        .getKeys(false)) {

            try {

                UUID followerUUID =
                        UUID.fromString(followerString);

                List<String> targetStrings =
                        followingConfig.getStringList(
                                "following." + followerString
                        );

                Set<UUID> targets = new HashSet<>();

                for (String targetString : targetStrings) {

                    try {
                        targets.add(
                                UUID.fromString(targetString)
                        );
                    } catch (IllegalArgumentException ignored) {
                        // Ignore invalid UUID.
                    }
                }

                if (!targets.isEmpty()) {

                    following.put(
                            followerUUID,
                            targets
                    );
                }

            } catch (IllegalArgumentException ignored) {

                getLogger().warning(
                        "Invalid follower UUID in following.yml: "
                                + followerString
                );
            }
        }
    }

    private void saveFollowing() {

        if (followingConfig == null || followingFile == null) {
            return;
        }

        followingConfig.set("following", null);

        for (Map.Entry<UUID, Set<UUID>> entry :
                following.entrySet()) {

            List<String> targets =
                    entry.getValue()
                            .stream()
                            .map(UUID::toString)
                            .collect(Collectors.toList());

            followingConfig.set(
                    "following." + entry.getKey(),
                    targets
            );
        }

        try {

            followingConfig.save(followingFile);

        } catch (IOException exception) {

            getLogger().severe(
                    "Could not save following.yml: "
                            + exception.getMessage()
            );
        }
    }

    // =========================================================
    // CHAT COLOR
    // =========================================================

    private String color(String message) {

        if (message == null) {
            return "";
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
