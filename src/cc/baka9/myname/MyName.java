package cc.baka9.myname;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.milkbowl.vault.economy.Economy;

public class MyName extends JavaPlugin {
	public static MyName instance;
	public static Economy economy = null;
	public static BukkitTask task;
	public static List<String> allPlayerNames = new ArrayList<>();

	public void onEnable() {
		instance = this;
		Config.load();
		if (!setupEconomy()) getLogger().info("没有检测到vault前置插件");
		getServer().getPluginManager().registerEvents(new Listener() {

			@EventHandler
			public void onPlayerChat(AsyncPlayerChatEvent e) {
				Player p = e.getPlayer();
				String playerName = p.getName().toLowerCase();
				MyNamePlayer myNamePlayer = Config.getMyNamePlayer(playerName);
				if (myNamePlayer == null) return;
				String chatName = p.getDisplayName().toLowerCase().replace(playerName,
						"§6" + myNamePlayer.getNick() + "§c(§b" + playerName + "§c)" + "§f");
				e.setFormat(e.getFormat().replace("%1$s", "{jobs} " + chatName));
			}

			@EventHandler
			public void onPlayerJoin(PlayerJoinEvent e) {
				Player p = e.getPlayer();
				String playerName = p.getName().toLowerCase();
				MyNamePlayer myNamePlayer = Config.getMyNamePlayer(playerName);
				if (myNamePlayer != null && !myNamePlayer.isOverdue()) {
					Utils.sendMessage(p, Config.message_time.replace("{day}", myNamePlayer.getSurplusDay() + ""));
					return;
				}
				if (!MyName.allPlayerNames.contains(playerName)) MyName.allPlayerNames.add(playerName);

			}
		}, this);
		for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
			allPlayerNames.add(player.getName().toLowerCase());
		}
		task = getServer().getScheduler().runTaskTimer(this, new Runnable() {
			@Override
			public void run() {
				for (MyNamePlayer myNamePlayer : Config.getMyNamePlayers()) {
					if (myNamePlayer.isOverdue()) {
						Config.removeMyNamePlayer(myNamePlayer.getName());
						Config.reload();
					}
				}
			}
		}, 0, 20 * 60);

	}

	@Override
	public void onDisable() {
		HandlerList.unregisterAll(this);
		task.cancel();
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (label.equalsIgnoreCase("setnick")) {
			if (!(sender instanceof Player)) {
				Utils.sendMessage(sender, "控制台不能使用这个指令");
				return true;
			}
			Player p = (Player) sender;
			String pName = p.getName().toLowerCase();
			if (args.length != 1) {
				Utils.sendMessage(sender, Config.message_use);
				return true;
			}
			if (!checkNickLegal(args[0], p, false)) return false;
			String newName = args[0];
			int day = Utils.getInvItemTime(p);
			if (day == 0) {
				Utils.sendMessage(sender, Config.message_nothaveitem);
				return true;
			}
			Config.createMyNamePlayer(pName, newName, day);
			Config.reload();
			Utils.sendMessage(sender, Config.message_ok.replace("{nick}", newName).replace("{time}", day + ""));
			return true;

		}

		if (label.equalsIgnoreCase("renick")) {
			if (!(sender instanceof Player)) {
				Utils.sendMessage(sender, "控制台不能使用这个指令");
				return true;
			}
			Player p = (Player) sender;
			String playerName = p.getName().toLowerCase();
			if (args.length != 1) {
				Utils.sendMessage(sender, Config.message_renickuse.replace("{money}", Config.money + ""));
				return true;
			}
			String newNick = args[0];
			if (!checkNickLegal(args[0], p, true)) return false;

			if (Config.getMyNamePlayer(playerName) == null) {
				Utils.sendMessage(sender, Config.message_set);
				return true;
			}
			if (!(MyName.economy.getBalance(p) >= Config.money)) {
				Utils.sendMessage(sender, Config.message_nothavemoney.replace("{money}", Config.money + ""));
				return true;
			}
			MyNamePlayer myNamePlayer = Config.getMyNamePlayer(playerName);
			myNamePlayer.setNick(newNick);
			Config.saveMyNamePlayer(myNamePlayer);
			Config.reload();
			MyName.economy.withdrawPlayer(p, Config.money);
			Utils.sendMessage(sender,
					Config.message_reok.replace("{nick}", newNick).replace("{money}", Config.money + ""));
			return true;
		}
		return false;
	}

	boolean checkNickLegal(String nick, Player p, boolean isReset) {

		if (nick.length() > Config.maxLength) {
			Utils.sendMessage(p, Config.message_length.replace("{length}", Config.maxLength + ""));
			return false;
		}
		String newName = nick;
		if (newName.contains("&")) {
			Utils.sendMessage(p, Config.message_colour);
			return false;
		}
		if (!isReset && Config.getMyNamePlayer(p.getName()) != null) {
			Utils.sendMessage(p, Config.message_reset.replace("{money}", Config.money + ""));
			return false;
		}
		for (MyNamePlayer myNamePlayer : Config.getMyNamePlayers()) {
			if (myNamePlayer.getNick().equalsIgnoreCase(newName)) {
				Utils.sendMessage(p, Config.message_taken);
				return false;
			}
		}
		if (MyName.allPlayerNames.contains(newName.toLowerCase())) {
			Utils.sendMessage(p, Config.message_taken);
			return false;
		}
		for (String black : Config.getBlackList()) {
			if (newName.toLowerCase().contains(black.toLowerCase())) {
				Utils.sendMessage(p, Config.message_ban);
				return false;
			}
		}
		return true;

	}

	boolean setupEconomy() {
		RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager()
				.getRegistration(net.milkbowl.vault.economy.Economy.class);
		if (economyProvider != null) economy = economyProvider.getProvider();
		return (economy != null);
	}

	static class Config {
		private static List<String> blackList = new ArrayList<>();
		private static List<MyNamePlayer> myNamePlayers = new ArrayList<>();
		public static Double money;
		public static Integer maxLength;
		public static String prefix;
		public static String message_use;
		public static String message_length;
		public static String message_taken;
		public static String message_ban;
		public static String message_ok;
		public static String message_reok;
		public static String message_colour;
		public static String message_renickuse;
		public static String message_reset;
		public static String message_nothaveitem;
		public static String message_nothavemoney;
		public static String message_time;
		public static String message_set;
		public static Pattern loreRegex;
		public static Integer itemId;
		public static Integer maxTime;

		public static List<String> getBlackList() {
			return new ArrayList<>(blackList);
		}

		public static List<MyNamePlayer> getMyNamePlayers() {
			return new ArrayList<>(myNamePlayers);
		}

		public static void load() {
			MyName.instance.saveDefaultConfig();
			FileConfiguration conf = MyName.instance.getConfig();
			for (String str : conf.getStringList("BlackList")) {
				blackList.add(str);
			}
			ConfigurationSection confSection = conf.getConfigurationSection("Names");
			if (confSection != null) {
				Set<String> names = confSection.getKeys(false);
				if (names != null && names.size() > 0) {
					for (String name : names) {
						MyNamePlayer myNamePlayer = new MyNamePlayer(name);
						myNamePlayer.setNick(conf.getString("Names." + name + ".name"));
						String startDateStr = conf.getString("Names." + name + ".date");
						Long startDate = null;
						try {
							startDate = Long.valueOf(startDateStr);
						} catch (Exception e) {
							try {
								startDate = Utils.dateFormat.parse(startDateStr).getTime();
							} catch (ParseException e1) {
								e1.printStackTrace();
							}
						}
						myNamePlayer.setStartDate(startDate);
						myNamePlayer.setTime(conf.getInt("Names." + name + ".time"));
						saveMyNamePlayer(myNamePlayer);
						myNamePlayers.add(myNamePlayer);
					}

				}
			}
			maxLength = conf.getInt("MaxLength");
			prefix = conf.getString("Prefix").replace("&", "§");
			message_use = conf.getString("Message_use").replace("&", "§");
			message_length = conf.getString("Message_length").replace("&", "§");
			message_taken = conf.getString("Message_taken").replace("&", "§");
			message_ban = conf.getString("Message_ban").replace("&", "§");
			message_ok = conf.getString("Message_ok").replace("&", "§");
			message_reok = conf.getString("Message_reok").replace("&", "§");
			message_colour = conf.getString("Message_colour").replace("&", "§");
			message_renickuse = conf.getString("Message_renickuse").replace("&", "§");
			message_reset = conf.getString("Message_reset").replace("&", "§");
			message_nothaveitem = conf.getString("Message_nothaveitem").replace("&", "§");
			message_nothavemoney = conf.getString("Message_nothavemoney").replace("&", "§");
			message_time = conf.getString("Message_time").replace("&", "§");
			message_set = conf.getString("Message_set").replace("&", "§");
			money = conf.getDouble("Money");
			loreRegex = Pattern.compile(conf.getString("LoreRegex").replace("&", "§"));
			itemId = conf.getInt("Item");
			maxTime = conf.getInt("MaxTime");
		}

		public static void createMyNamePlayer(String name, String nick, Integer day) {
			name = name.toLowerCase();
			FileConfiguration conf = MyName.instance.getConfig();
			conf.set("Names." + name + ".name", nick);
			conf.set("Names." + name + ".time", day);
			conf.set("Names." + name + ".date", Utils.dateFormat.format(new Date()));
			MyName.instance.saveConfig();
		}

		public static void saveMyNamePlayer(MyNamePlayer myNamePlayer) {
			String name = myNamePlayer.getName();
			FileConfiguration conf = MyName.instance.getConfig();
			conf.set("Names." + name + ".name", myNamePlayer.getNick());
			conf.set("Names." + name + ".date", myNamePlayer.getStartDate());
			conf.set("Names." + name + ".time", myNamePlayer.getTime());
			MyName.instance.saveConfig();
		}

		public static void removeMyNamePlayer(String name) {
			FileConfiguration conf = MyName.instance.getConfig();
			conf.set("Names." + name.toLowerCase(), null);
			MyName.instance.saveConfig();
		}

		public static MyNamePlayer getMyNamePlayer(String name) {
			MyNamePlayer myNamePlayer = null;
			for (MyNamePlayer obj : getMyNamePlayers()) {
				if (obj.getName().equalsIgnoreCase(name)) {
					myNamePlayer = obj;
					break;
				}
			}
			return myNamePlayer;
		}

		public static void reload() {
			blackList.clear();
			myNamePlayers.clear();
			MyName.instance.reloadConfig();
			load();
		}

	}

	static class Utils {
		public static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");

		@SuppressWarnings("deprecation")
		public static Integer getInvItemTime(Player p) {
			Inventory inv = p.getInventory();
			for (int i = 0; i < inv.getSize(); i++) {
				ItemStack item = inv.getItem(i);
				if (item == null) continue;
				if (item.getTypeId() == Config.itemId) {
					if (item.getAmount() == 1) inv.setItem(i, null);
					item.setAmount(item.getAmount() - 1);
					return Config.maxTime;
				}
				Matcher matcher;
				if ((matcher = Config.loreRegex.matcher(getLore(item).toString())).find()) {
					if (item.getAmount() == 1) inv.setItem(i, null);
					item.setAmount(item.getAmount() - 1);
					return Integer.parseInt(matcher.group(1));
				}
			}
			return 0;
		}

		public static List<String> getLore(ItemStack item) {
			return item.getItemMeta().hasLore() ? item.getItemMeta().getLore() : new ArrayList<String>();

		}

		public static void sendMessage(CommandSender sender, String message) {
			sender.sendMessage(Config.prefix + message);
		}
	}

	static class MyNamePlayer {
		private String name;
		private String nick;
		private Integer time;
		private Long startDate;

		public MyNamePlayer(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public String getNick() {
			return nick;
		}

		public void setNick(String nick) {
			this.nick = nick;
		}

		public Integer getTime() {
			return time;
		}

		public void setTime(Integer time) {
			this.time = time;
		}

		public Long getStartDate() {
			return startDate;
		}

		public void setStartDate(Long startDate) {
			this.startDate = startDate;
		}

		/** 是否过期 */
		public boolean isOverdue() {
			return System.currentTimeMillis() >= this.startDate + (1000L * 60 * 60 * 24 * this.time);
		}

		/** 剩余天数 */
		public int getSurplusDay() {
			long now = System.currentTimeMillis();
			long end = this.startDate + (long) (1000L * 60 * 60 * 24 * this.time);
			return (int) ((end - now) / (1000 * 60 * 60 * 24));
		}

	}
}