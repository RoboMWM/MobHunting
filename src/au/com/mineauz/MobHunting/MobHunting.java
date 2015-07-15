package au.com.mineauz.MobHunting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.mcstats.Metrics;

import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;

import de.Keyle.MyPet.api.entity.MyPetEntity;
import au.com.mineauz.MobHunting.achievements.*;
import au.com.mineauz.MobHunting.commands.CheckGrindingCommand;
import au.com.mineauz.MobHunting.commands.ClearGrindingCommand;
import au.com.mineauz.MobHunting.commands.CommandDispatcher;
import au.com.mineauz.MobHunting.commands.LeaderboardCommand;
import au.com.mineauz.MobHunting.commands.ListAchievementsCommand;
import au.com.mineauz.MobHunting.commands.ReloadCommand;
import au.com.mineauz.MobHunting.commands.SelectCommand;
import au.com.mineauz.MobHunting.commands.TopCommand;
import au.com.mineauz.MobHunting.commands.WhitelistAreaCommand;
import au.com.mineauz.MobHunting.compatability.CitizensCompat;
import au.com.mineauz.MobHunting.compatability.CompatibilityManager;
import au.com.mineauz.MobHunting.compatability.MinigamesCompat;
import au.com.mineauz.MobHunting.compatability.MobArenaCompat;
import au.com.mineauz.MobHunting.compatability.MobArenaHelper;
import au.com.mineauz.MobHunting.compatability.MyPetCompat;
import au.com.mineauz.MobHunting.compatability.MythicMobsCompat;
import au.com.mineauz.MobHunting.compatability.PVPArenaCompat;
import au.com.mineauz.MobHunting.compatability.PVPArenaHelper;
import au.com.mineauz.MobHunting.compatability.WorldEditCompat;
import au.com.mineauz.MobHunting.compatability.WorldGuardCompat;
import au.com.mineauz.MobHunting.events.MobHuntEnableCheckEvent;
import au.com.mineauz.MobHunting.events.MobHuntKillEvent;
import au.com.mineauz.MobHunting.leaderboard.LeaderboardManager;
import au.com.mineauz.MobHunting.modifier.*;
import au.com.mineauz.MobHunting.storage.DataStore;
import au.com.mineauz.MobHunting.storage.DataStoreException;
import au.com.mineauz.MobHunting.storage.DataStoreManager;
import au.com.mineauz.MobHunting.storage.MySQLDataStore;
import au.com.mineauz.MobHunting.storage.SQLiteDataStore;
import au.com.mineauz.MobHunting.util.Misc;
import au.com.mineauz.MobHunting.util.Update;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;

public class MobHunting extends JavaPlugin implements Listener {

	// Constants
	public final static String pluginName = "mobhunting";
	public final static String tablePrefix = "mh_";

	private Economy mEconomy;
	public static MobHunting instance;

	private WeakHashMap<LivingEntity, DamageInformation> mDamageHistory = new WeakHashMap<LivingEntity, DamageInformation>();
	private Config mConfig;

	private AchievementManager mAchievements = new AchievementManager();
	public static double cDampnerRange = 15;

	private Set<IModifier> mModifiers = new HashSet<IModifier>();

	private ArrayList<Area> mKnownGrindingSpots = new ArrayList<Area>();
	private HashMap<UUID, LinkedList<Area>> mWhitelistedAreas = new HashMap<UUID, LinkedList<Area>>();

	private ParticleManager mParticles = new ParticleManager();
	private Random mRand = new Random();

	private DataStore mStore;
	private DataStoreManager mStoreManager;

	private LeaderboardManager mLeaderboards;

	private boolean mInitialized = false;

	// Update object
	private Update updateCheck = null;

	@Override
	public void onLoad() {

	}

	private boolean versionCheck() {
		String version = Bukkit.getBukkitVersion();
		if (version == null)
			return true; // custom bukkit, whatever

		// String[] parts = version.split("\\-");
		// String[] verPart = parts[0].split("\\.");
		// int major = Integer.valueOf(verPart[0]);
		// int minor = Integer.valueOf(verPart[1]);
		// int revision = 0;
		// if(verPart.length == 3)
		// revision = Integer.valueOf(verPart[2]);

		// if(major >= 1 && minor >= 7 && revision >= 8)
		// return true;
		//
		// getLogger().severe("This version of MobHunting is for Bukkit 1.7.8 and up. Please update your bukkit.");
		// return false;
		return true;
	}

	@Override
	public void onEnable() {
		mInitialized = false;

		if (!versionCheck()) {
			instance = null;
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		instance = this;

		// Move the old data folder
		File oldData = new File(getDataFolder().getParentFile(), "Mob Hunting"); //$NON-NLS-1$
		if (oldData.exists()) {
			try {
				Files.move(oldData.toPath(), getDataFolder().toPath(),
						StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		mConfig = new Config(new File(getDataFolder(), "config.yml"));

		if (mConfig.loadConfig())
			mConfig.saveConfig();
		else
			throw new RuntimeException(Messages.getString(pluginName
					+ ".config.fail"));

		Messages.exportDefaultLanguages();

		RegisteredServiceProvider<Economy> economyProvider = getServer()
				.getServicesManager().getRegistration(Economy.class);
		if (economyProvider == null) {
			instance = null;
			getLogger().severe(Messages.getString(pluginName + ".hook.econ"));
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		mEconomy = economyProvider.getProvider();

		if (!loadWhitelist())
			throw new RuntimeException();

		if (mConfig.databaseType.equalsIgnoreCase("mysql"))
			mStore = new MySQLDataStore();
		else
			mStore = new SQLiteDataStore();

		try {
			mStore.initialize();
		} catch (DataStoreException e) {
			e.printStackTrace();

			try {
				mStore.shutdown();
			} catch (DataStoreException e1) {
				e1.printStackTrace();
			}
			setEnabled(false);
			return;
		}

		mStoreManager = new DataStoreManager(mStore);

		// Handle compatability stuff
		CompatibilityManager.register(MinigamesCompat.class, "Minigames");
		CompatibilityManager.register(MyPetCompat.class, "MyPet");
		CompatibilityManager.register(WorldEditCompat.class, "WorldEdit");
		CompatibilityManager.register(WorldGuardCompat.class, "WorldGuard");
		CompatibilityManager.register(MobArenaCompat.class, "MobArena");
		CompatibilityManager.register(PVPArenaCompat.class, "PVPArena");
		CompatibilityManager.register(MythicMobsCompat.class, "MythicMobs");
		CompatibilityManager.register(CitizensCompat.class, "Citizens");
		// CompatibilityManager.register(HeroesCompat.class, "Heroes");
		// CompatibilityManager.register(MobDungeonMainCompat.class,
		// "MobDungeon");
		// CompatibilityManager.register(WarCompat.class, "War");

		CommandDispatcher cmd = new CommandDispatcher("mobhunt",
				Messages.getString("mobhunting.command.base.description")
						+ getDescription().getVersion());
		getCommand("mobhunt").setExecutor(cmd);
		getCommand("mobhunt").setTabCompleter(cmd);

		cmd.registerCommand(new ReloadCommand());
		cmd.registerCommand(new ListAchievementsCommand());
		cmd.registerCommand(new CheckGrindingCommand());
		cmd.registerCommand(new TopCommand());
		cmd.registerCommand(new LeaderboardCommand());
		cmd.registerCommand(new ClearGrindingCommand());
		cmd.registerCommand(new WhitelistAreaCommand());

		if (getServer().getPluginManager().isPluginEnabled("WorldEdit")) {
			cmd.registerCommand(new SelectCommand());
		}

		registerAchievements();
		registerModifiers();

		getServer().getPluginManager().registerEvents(this, this);

		if (mAchievements.upgradeAchievements())
			mStoreManager.waitForUpdates();

		for (Player player : Bukkit.getOnlinePlayers())
			mAchievements.load(player);

		mLeaderboards = new LeaderboardManager();
		mLeaderboards.initialize();

		mInitialized = true;

		try {
			Metrics metrics = new Metrics(this);
			metrics.start();
			debug("Metrics started");
		} catch (IOException e) {
			debug("Failed to start Metrics!");
			// Failed to submit the stats :-(
		}

		// Check for updates asynchronously
		if (instance.mConfig.updateCheck) {
			checkUpdates();
			new BukkitRunnable() {
				int count = 0;

				@Override
				public void run() {
					if (count++ > 10) {
						instance.getLogger()
								.info("["
										+ pluginName
										+ "]No updates found. (No response from server after 10s)");
						this.cancel();
					} else {
						// Wait for the response
						if (updateCheck != null) {
							if (updateCheck.isSuccess()) {
								checkUpdatesNotify(null);
							} else {
								instance.getLogger().info(
										"[" + pluginName + "]No update.");
							}
							this.cancel();
						}
					}
				}
			}.runTaskTimer(instance, 0L, 20L); // Check status every second
		}

	}

	@Override
	public void onDisable() {
		if (!mInitialized)
			return;

		mLeaderboards.shutdown();

		mAchievements = new AchievementManager();
		mModifiers.clear();

		try {
			mStoreManager.shutdown();
			mStore.shutdown();
		} catch (DataStoreException e) {
			e.printStackTrace();
		}
	}

	private void registerAchievements() {
		mAchievements.registerAchievement(new AxeMurderer());
		mAchievements.registerAchievement(new CreeperBoxing());
		mAchievements.registerAchievement(new Electrifying());
		mAchievements.registerAchievement(new RecordHungry());
		mAchievements.registerAchievement(new InFighting());
		mAchievements.registerAchievement(new ByTheBook());
		mAchievements.registerAchievement(new Creepercide());
		mAchievements.registerAchievement(new TheHuntBegins());
		mAchievements.registerAchievement(new ItsMagic());
		mAchievements.registerAchievement(new FancyPants());
		mAchievements.registerAchievement(new MasterSniper());
		mAchievements.registerAchievement(new WolfKillAchievement());

		for (ExtendedMobType type : ExtendedMobType.values()) {
			mAchievements.registerAchievement(new BasicHuntAchievement(type));
			mAchievements.registerAchievement(new SecondHuntAchievement(type));
			mAchievements.registerAchievement(new ThirdHuntAchievement(type));
			mAchievements.registerAchievement(new FourthHuntAchievement(type));
			mAchievements.registerAchievement(new FifthHuntAchievement(type));
			mAchievements.registerAchievement(new SixthHuntAchievement(type));
			mAchievements.registerAchievement(new SeventhHuntAchievement(type));
		}

		mAchievements.initialize();
	}

	private void registerModifiers() {
		mModifiers.add(new BrawlerBonus());
		mModifiers.add(new ProSniperBonus());
		mModifiers.add(new SniperBonus());
		mModifiers.add(new ReturnToSenderBonus());
		mModifiers.add(new ShoveBonus());
		mModifiers.add(new SneakyBonus());
		mModifiers.add(new FriendleFireBonus());
		mModifiers.add(new BonusMobBonus());
		mModifiers.add(new CriticalModifier());

		mModifiers.add(new FlyingPenalty());
		mModifiers.add(new GrindingPenalty());

		// Check if horses exist
		try {
			Class.forName("org.bukkit.entity.Horse");
			mModifiers.add(new MountedBonus());
		} catch (ClassNotFoundException e) {
		}
	}

	void registerKnownGrindingSpot(Area newArea) {
		for (Area area : mKnownGrindingSpots) {
			if (newArea.center.getWorld().equals(area.center.getWorld())) {
				double dist = newArea.center.distance(area.center);

				double remaining = dist;
				remaining -= area.range;
				remaining -= newArea.range;

				if (remaining < 0) {
					if (dist > area.range)
						area.range = dist;

					area.count += newArea.count;

					return;
				}
			}
		}

		mKnownGrindingSpots.add(newArea);
	}

	public Area getGrindingArea(Location location) {
		for (Area area : mKnownGrindingSpots) {
			if (area.center.getWorld().equals(location.getWorld())) {
				if (area.center.distance(location) < area.range)
					return area;
			}
		}

		return null;
	}

	public void clearGrindingArea(Location location) {
		Iterator<Area> it = mKnownGrindingSpots.iterator();
		while (it.hasNext()) {
			Area area = it.next();

			if (area.center.getWorld().equals(location.getWorld())) {
				if (area.center.distance(location) < area.range)
					it.remove();
			}
		}
	}

	public static Economy getEconomy() {
		return instance.mEconomy;
	}

	public static Config config() {
		return instance.mConfig;
	}

	public void registerModifier(IModifier modifier) {
		mModifiers.add(modifier);
	}

	public HuntData getHuntData(Player player) {
		HuntData data = null;
		if (!player.hasMetadata("MobHuntData")) {
			data = new HuntData();
			player.setMetadata("MobHuntData",
					new FixedMetadataValue(this, data));
		} else {
			if (!(player.getMetadata("MobHuntData").get(0).value() instanceof HuntData)) {
				player.getMetadata("MobHuntData").get(0).invalidate();
				player.setMetadata("MobHuntData", new FixedMetadataValue(this,
						new HuntData()));
			}

			data = (HuntData) player.getMetadata("MobHuntData").get(0).value();
		}

		return data;
	}

	public static boolean isHuntEnabled(Player player) {
		if (!player.hasMetadata("MH:enabled")) {
			debug("KillBlocked %s: Player doesnt have MH:enabled",
					player.getName());
			return false;
		}

		List<MetadataValue> values = player.getMetadata("MH:enabled");

		// Use the first value that matches the required type
		boolean enabled = false;
		for (MetadataValue value : values) {
			if (value.value() instanceof Boolean)
				enabled = value.asBoolean();
		}

		if (enabled && !player.hasPermission("mobhunting.enable")) {
			debug("KillBlocked %s: Player doesnt have permission",
					player.getName());
			return false;
		}

		if (!enabled) {
			debug("KillBlocked %s: MH:enabled is false", player.getName());
			return false;
		}

		MobHuntEnableCheckEvent event = new MobHuntEnableCheckEvent(player);
		Bukkit.getPluginManager().callEvent(event);

		if (!event.isEnabled())
			debug("KillBlocked %s: Plugin cancelled check", player.getName());
		return event.isEnabled();
	}

	public static boolean isHuntEnabledInWorld(World world) {
		for (String worldName : config().disabledInWorlds) {
			if (world.getName().equalsIgnoreCase(worldName))
				return false;
		}

		return true;
	}

	public static void setHuntEnabled(Player player, boolean enabled) {
		player.setMetadata("MH:enabled", new FixedMetadataValue(instance,
				enabled));
	}

	private boolean saveWhitelist() {
		YamlConfiguration whitelist = new YamlConfiguration();
		File file = new File(getDataFolder(), "whitelist.yml");

		for (Entry<UUID, LinkedList<Area>> entry : mWhitelistedAreas.entrySet()) {
			ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();
			for (Area area : entry.getValue()) {
				HashMap<String, Object> map = new HashMap<String, Object>();
				map.put("Center", Misc.toMap(area.center));
				map.put("Radius", area.range);
				list.add(map);
			}

			whitelist.set(entry.getKey().toString(), list);
		}

		try {
			whitelist.save(file);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	private boolean loadWhitelist() {
		YamlConfiguration whitelist = new YamlConfiguration();
		File file = new File(getDataFolder(), "whitelist.yml");

		if (!file.exists())
			return true;

		try {
			whitelist.load(file);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
			return false;
		}

		mWhitelistedAreas.clear();

		for (String worldId : whitelist.getKeys(false)) {
			UUID world = UUID.fromString(worldId);
			List<Map<String, Object>> list = (List<Map<String, Object>>) whitelist
					.getList(worldId);
			LinkedList<Area> areas = new LinkedList<Area>();

			if (list == null)
				continue;

			for (Map<String, Object> map : list) {
				Area area = new Area();
				area.center = Misc.fromMap((Map<String, Object>) map
						.get("Center"));
				area.range = (Double) map.get("Radius");
				areas.add(area);
			}

			mWhitelistedAreas.put(world, areas);
		}

		return true;
	}

	public static boolean isWhitelisted(Location location) {
		LinkedList<Area> areas = instance.mWhitelistedAreas.get(location
				.getWorld().getUID());

		if (areas == null)
			return false;

		for (Area area : areas) {
			if (area.center.distance(location) < area.range)
				return true;
		}

		return false;
	}

	public void whitelistArea(Area newArea) {
		LinkedList<Area> areas = mWhitelistedAreas.get(newArea.center
				.getWorld().getUID());

		if (areas == null) {
			areas = new LinkedList<Area>();
			mWhitelistedAreas.put(newArea.center.getWorld().getUID(), areas);
		}

		for (Area area : areas) {
			if (newArea.center.getWorld().equals(area.center.getWorld())) {
				double dist = newArea.center.distance(area.center);

				double remaining = dist;
				remaining -= area.range;
				remaining -= newArea.range;

				if (remaining < 0) {
					if (dist > area.range)
						area.range = dist;

					area.count += newArea.count;

					return;
				}
			}
		}

		areas.add(newArea);

		saveWhitelist();
	}

	public void unWhitelistArea(Location location) {
		LinkedList<Area> areas = mWhitelistedAreas.get(location.getWorld()
				.getUID());

		if (areas == null)
			return;

		Iterator<Area> it = areas.iterator();
		while (it.hasNext()) {
			Area area = it.next();

			if (area.center.getWorld().equals(location.getWorld())) {
				if (area.center.distance(location) < area.range)
					it.remove();
			}
		}

		if (areas.isEmpty())
			mWhitelistedAreas.remove(location.getWorld().getUID());

		saveWhitelist();
	}

	public static void debug(String text, Object... args) {
		if (instance.mConfig.killDebug)
			instance.getLogger().info("[Debug] " + String.format(text, args));
	}

	@EventHandler
	private void onWorldLoad(WorldLoadEvent event) {
		List<Area> areas = mWhitelistedAreas.get(event.getWorld().getUID());
		if (areas != null) {
			for (Area area : areas)
				area.center.setWorld(event.getWorld());
		}
	}

	@EventHandler
	private void onWorldUnLoad(WorldUnloadEvent event) {
		List<Area> areas = mWhitelistedAreas.get(event.getWorld().getUID());
		if (areas != null) {
			for (Area area : areas)
				area.center.setWorld(null);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onPlayerDeath(PlayerDeathEvent event) {
		if (!isHuntEnabledInWorld(event.getEntity().getWorld())
				|| !isHuntEnabled(event.getEntity()))
			return;

		HuntData data = getHuntData(event.getEntity());
		if (data.getKillstreakLevel() != 0)
			event.getEntity()
					.sendMessage(
							ChatColor.RED
									+ ""
									+ ChatColor.ITALIC
									+ Messages
											.getString("mobhunting.killstreak.ended"));
		data.killStreak = 0;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onPlayerDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player))
			return;

		if (!isHuntEnabledInWorld(event.getEntity().getWorld())
				|| !isHuntEnabled((Player) event.getEntity()))
			return;

		Player player = (Player) event.getEntity();
		HuntData data = getHuntData(player);
		if (data.getKillstreakLevel() != 0)
			player.sendMessage(ChatColor.RED + "" + ChatColor.ITALIC
					+ Messages.getString("mobhunting.killstreak.ended"));
		data.killStreak = 0;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onSkeletonShoot(ProjectileLaunchEvent event) {
		// TODO: can Skeleton use other weapons the Arrow?
		if (!(event.getEntity() instanceof Arrow)
				|| !(event.getEntity().getShooter() instanceof Skeleton)
				|| !isHuntEnabledInWorld(event.getEntity().getWorld()))
			return;

		Skeleton shooter = (Skeleton) event.getEntity().getShooter();

		if (shooter.getTarget() instanceof Player
				&& isHuntEnabled((Player) shooter.getTarget())
				&& ((Player) shooter.getTarget()).getGameMode() != GameMode.CREATIVE) {
			DamageInformation info = null;
			info = mDamageHistory.get(shooter);

			if (info == null)
				info = new DamageInformation();

			info.time = System.currentTimeMillis();

			info.attacker = (Player) shooter.getTarget();

			info.attackerPosition = shooter.getTarget().getLocation().clone();
			mDamageHistory.put(shooter, info);
		}
	}

	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onMobDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof LivingEntity)
				|| !isHuntEnabledInWorld(event.getEntity().getWorld()))
			return;

		if (WorldGuardCompat.isWorldGuardSupported()) {
			if ((event.getDamager() instanceof Player)
					|| (MyPetCompat.isMyPetSupported() && event.getDamager() instanceof MyPetEntity)) {
				RegionManager regionManager = WorldGuardCompat
						.getWorldGuardPlugin().getRegionManager(
								event.getDamager().getWorld());
				ApplicableRegionSet set = regionManager
						.getApplicableRegions(event.getDamager().getLocation());
				if (set != null) {
					if (!set.allows(DefaultFlag.MOB_DAMAGE)) {
						debug("KillBlocked: %s is hiding in WG region with MOB_DAMAGE %s",
								event.getDamager().getName(),
								set.allows(DefaultFlag.MOB_DAMAGE));
						return;
					}
				}
			}
		}

		DamageInformation info = null;
		info = mDamageHistory.get(event.getEntity());
		if (info == null)
			info = new DamageInformation();

		info.time = System.currentTimeMillis();

		Player cause = null;
		ItemStack weapon = null;

		if (event.getDamager() instanceof Player)
			cause = (Player) event.getDamager();

		boolean projectile = false;
		if (event.getDamager() instanceof Projectile) {
			if (((Projectile) event.getDamager()).getShooter() instanceof Player)
				cause = (Player) ((Projectile) event.getDamager()).getShooter();

			if (event.getDamager() instanceof ThrownPotion)
				weapon = ((ThrownPotion) event.getDamager()).getItem();

			info.mele = false;
			projectile = true;
		} else
			info.mele = true;

		if (event.getDamager() instanceof Wolf
				&& ((Wolf) event.getDamager()).isTamed()
				&& ((Wolf) event.getDamager()).getOwner() instanceof Player) {
			cause = (Player) ((Wolf) event.getDamager()).getOwner();

			info.mele = false;
			info.wolfAssist = true;
		}

		if (weapon == null && cause != null)
			weapon = cause.getItemInHand();

		if (weapon != null)
			info.weapon = weapon;

		// Take note that a weapon has been used at all
		if (info.weapon != null
				&& (Misc.isSword(info.weapon) || Misc.isAxe(info.weapon)
						|| Misc.isPick(info.weapon) || projectile))
			info.usedWeapon = true;

		if (cause != null) {
			if (cause != info.attacker) {
				info.assister = info.attacker;
				info.lastAssistTime = info.lastAttackTime;
			}

			info.lastAttackTime = System.currentTimeMillis();

			info.attacker = cause;
			if (cause.isFlying() && !cause.isInsideVehicle())
				info.wasFlying = true;

			info.attackerPosition = cause.getLocation().clone();
			mDamageHistory.put((LivingEntity) event.getEntity(), info);
		}
	}

	@SuppressWarnings({ "deprecation", "unused" })
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onMobDeath(EntityDeathEvent event) {
		LivingEntity killed = event.getEntity();
		Player killer = killed.getKiller();

		if (!isHuntEnabledInWorld(killed.getWorld())) {
			debug("KillBlocked %s(%d): Mobhunting disabled in world %s",
					killed.getType(), killed.getEntityId(), killed.getWorld()
							.getName());
			return;
		}

		if (WorldGuardCompat.isWorldGuardSupported()) {
			if (killer instanceof Player
					|| (MyPetCompat.isMyPetSupported() && killer instanceof MyPetEntity)) {
				RegionManager regionManager = WorldGuardCompat
						.getWorldGuardPlugin().getRegionManager(
								killer.getWorld());
				ApplicableRegionSet set = regionManager
						.getApplicableRegions(killer.getLocation());
				if (set.size() > 0) {
					debug("Found %s worldguard regions: flag is %s",
							set.size(), set.allows(DefaultFlag.MOB_DAMAGE));
					if (killer instanceof Player
							&& !set.allows(DefaultFlag.MOB_DAMAGE)) {
						debug("KillBlocked: %s is hiding in WG region with MOB_DAMAGE %s",
								killer.getName(),
								set.allows(DefaultFlag.MOB_DAMAGE));
						return;
					}
				}
			}
		}

		if (killed instanceof Player) {
			if (MobArenaHelper.isPlayingMobArena((Player) killed)) {
				debug("KillBlocked: %s was killed while playing MobArena.",
						killed.getName());
				return;
			} else if (PVPArenaHelper.isPlayingPVPArena((Player) killed)) {
				debug("KillBlocked: %s was killed while playing PvpArena.",
						killed.getName());
				return;
			} else if (killer instanceof Player && !mConfig.pvpAllowed) {
				debug("KillBlocked: PVP not allowed. %s killed %s.",
						killer.getName(), killed.getName());
				return;
			}
		}

		if (MythicMobsCompat.isMythicMobsSupported()) {
			if (killed.hasMetadata("MH:MythicMob"))
				if (killer instanceof Player)
					debug("%s killed a MythicMob", killer.getName());
		}

		if (CitizensCompat.isCitizensSupported()
				&& CitizensCompat.isNPC(killed)) {
			NPCRegistry registry = CitizensAPI.getNPCRegistry();
			NPC npc = registry.getNPC(killed);

			debug("A Citizens was killed - NPC is Sentry=%S ",
					npc.hasTrait(CitizensAPI.getTraitFactory().getTraitClass(
							"Sentry")));
		}

		if (killer instanceof Player) {
			if (MobArenaHelper.isPlayingMobArena(killer)
					&& !mConfig.mobarenaGetRewards) {
				debug("KillBlocked: %s is currently playing MobArena.",
						killer.getName());
				return;
			} else if (PVPArenaHelper.isPlayingPVPArena(killer)
					&& !mConfig.pvparenaGetRewards) {
				debug("KillBlocked: %s is currently playing PvpArena.",
						killer.getName());
				return;
			}
		}

		if (getBaseKillPrize(event.getEntity()) == 0
				&& getKillConsoleCmd(killed).equals("")) {
			debug("KillBlocked %s(%d): There is no reward for this Mob/Player",
					killed.getType(), killed.getEntityId());
			return;
		}

		if (killed.hasMetadata("MH:blocked")) {
			debug("KillBlocked %s(%d): Mob has MH:blocked meta (probably spawned from a mob spawner)",
					killed.getType(), killed.getEntityId());
			return;
		}

		if (killer == null || killer.getGameMode() == GameMode.CREATIVE
				|| !isHuntEnabled(killer)) {
			if (killer != null && killer.getGameMode() == GameMode.CREATIVE)
				debug("KillBlocked %s: In creative mode", killer.getName());
			return;
		}

		DamageInformation info = null;
		if (killed instanceof LivingEntity
				&& mDamageHistory.containsKey((LivingEntity) killed)) {
			info = mDamageHistory.get(killed);

			if (System.currentTimeMillis() - info.time > mConfig.assistTimeout * 1000)
				info = null;
			else if (killer == null)
				killer = info.attacker;
		}

		EntityDamageByEntityEvent lastDamageCause = null;

		if (killed.getLastDamageCause() instanceof EntityDamageByEntityEvent)
			lastDamageCause = (EntityDamageByEntityEvent) killed
					.getLastDamageCause();

		if (info == null) {
			info = new DamageInformation();
			info.time = System.currentTimeMillis();
			info.lastAttackTime = info.time;
			info.attacker = killer;
			info.attackerPosition = killer.getLocation();
			info.usedWeapon = true;
		}

		if ((System.currentTimeMillis() - info.lastAttackTime) > mConfig.killTimeout * 1000) {
			debug("KillBlocked %s: Last damage was too long ago",
					killer.getName());
			return;
		}

		if (info.weapon == null)
			info.weapon = new ItemStack(Material.AIR);

		HuntData data = getHuntData(killer);

		Misc.handleKillstreak(killer);

		// Record kills that are still within a small area
		Location loc = killed.getLocation();

		Area detectedGrindingArea = getGrindingArea(loc);

		if (detectedGrindingArea == null)
			detectedGrindingArea = data.getGrindingArea(loc);

		// Slimes are except from grinding due to their splitting nature
		if (!(event.getEntity() instanceof Slime)
				&& mConfig.penaltyGrindingEnable
				&& !killed.hasMetadata("MH:reinforcement")
				&& !isWhitelisted(killed.getLocation())) {
			if (detectedGrindingArea != null) {
				data.lastKillAreaCenter = null;
				data.dampenedKills = detectedGrindingArea.count++;

				if (data.dampenedKills == 20)
					registerKnownGrindingSpot(detectedGrindingArea);
			} else {
				if (data.lastKillAreaCenter != null) {
					if (loc.getWorld().equals(
							data.lastKillAreaCenter.getWorld())) {
						if (loc.distance(data.lastKillAreaCenter) < cDampnerRange) {
							data.dampenedKills++;
							if (data.dampenedKills == 10)
								data.recordGrindingArea();
						} else {
							data.lastKillAreaCenter = loc.clone();
							data.dampenedKills = 0;
						}
					} else {
						data.lastKillAreaCenter = loc.clone();
						data.dampenedKills = 0;
					}
				} else {
					data.lastKillAreaCenter = loc.clone();
					data.dampenedKills = 0;
				}
			}

			if (data.dampenedKills > 14) {
				if (data.getKillstreakLevel() != 0)
					killer.sendMessage(ChatColor.RED
							+ Messages.getString("mobhunting.killstreak.lost"));
				data.killStreak = 0;
			}
		}

		double cash = getBaseKillPrize(killed);
		double multiplier = 1.0;

		// Apply the modifiers
		ArrayList<String> modifiers = new ArrayList<String>();
		for (IModifier mod : mModifiers) {
			if (mod.doesApply(killed, killer, data, info, lastDamageCause)) {
				double amt = mod.getMultiplier(killed, killer, data, info,
						lastDamageCause);

				if (amt != 1.0) {
					modifiers.add(mod.getName());
					multiplier *= amt;
				}
			}
		}

		multiplier *= data.getKillstreakMultiplier();

		String extraString = "";

		// Only display the multiplier if its not 1
		if (Math.abs(multiplier - 1) > 0.05)
			extraString += String.format("x%.1f", multiplier);

		// Add on modifiers
		for (String modifier : modifiers)
			extraString += ChatColor.WHITE + " * " + modifier;

		cash *= multiplier;

		if (cash >= 0.01) {
			MobHuntKillEvent event2 = new MobHuntKillEvent(data, info, killed,
					killer);
			Bukkit.getPluginManager().callEvent(event2);

			if (event2.isCancelled()) {
				debug("KillBlocked %s: MobHuntKillEvent was cancelled",
						killer.getName());
				return;
			}

			if (killed instanceof Player && killer instanceof Player) {
				mEconomy.withdrawPlayer((Player) killed, cash);
				killed.sendMessage(ChatColor.GREEN
						+ ""
						+ ChatColor.ITALIC
						+ Messages.getString("mobhunting.moneylost",
								mEconomy.format(cash)));
				debug("%s lost %s", killed.getName(), mEconomy.format(cash));
			}
			if (info.assister == null) {
				mEconomy.depositPlayer(killer, cash);
				debug("%s got a reward (%s)", killer.getName(),
						mEconomy.format(cash));
			} else {
				cash = cash / 2;
				mEconomy.depositPlayer(killer, cash);
				onAssist(info.assister, killer, killed, info.lastAssistTime);
				debug("%s got a ½ reward (%s)", killer.getName(),
						mEconomy.format(cash));
			}

			getDataStore().recordKill(killer,
					ExtendedMobType.fromEntity(killed),
					killed.hasMetadata("MH:hasBonus"));

			if (extraString.trim().isEmpty()) {
				killer.sendMessage(ChatColor.GREEN
						+ ""
						+ ChatColor.ITALIC
						+ Messages.getString("mobhunting.moneygain", "prize",
								mEconomy.format(cash)));
			} else
				killer.sendMessage(ChatColor.GREEN
						+ ""
						+ ChatColor.ITALIC
						+ Messages.getString("mobhunting.moneygain.bonuses",
								"prize", mEconomy.format(cash), "bonuses",
								extraString.trim()));
		} else
			debug("KillBlocked %s: Gained money was less than 1 cent (grinding or penalties) (%s)",
					killer.getName(), extraString);

		// Run console commands as a reward
		if (data.dampenedKills < 10) {
			if (!getKillConsoleCmd(killed).equals("")) {
				if (mRand.nextInt(getCmdRunProbabilityBase(killed)) < getCmdRunProbability(killed)) {
					String worldname = killer.getWorld().getName();
					String prizeCommand = getKillConsoleCmd(killed)
							.replaceAll("\\{player\\}", killer.getName())
							.replaceAll("\\{killed_player\\}", killed.getName())
							.replaceAll("\\{world\\}", worldname);
					if (!getKillConsoleCmd(killed).equals("")) {
						String str = prizeCommand;
						do {
							if (str.contains("|")) {
								int n = str.indexOf("|");
								Bukkit.getServer().dispatchCommand(
										Bukkit.getServer().getConsoleSender(),
										str.substring(0, n));
								str = str.substring(n + 1, str.length())
										.toString();
							}
						} while (str.contains("|"));
						Bukkit.getServer().dispatchCommand(
								Bukkit.getServer().getConsoleSender(), str);
					}
					// send a message to the player
					if (!getKillRewardDescription(killed).equals("")) {
						killer.sendMessage(ChatColor.GREEN
								+ ""
								+ ChatColor.ITALIC
								+ getKillRewardDescription(killed)
										.replaceAll("\\{player\\}",
												killer.getName())
										.replaceAll("\\{killed_player\\}",
												killed.getName())
										.replaceAll("\\{world\\}", worldname));
					}
				}
			}
		}
	}

	private void onAssist(Player player, Player killer, LivingEntity killed,
			long time) {
		if (!mConfig.enableAssists
				|| (System.currentTimeMillis() - time) > mConfig.assistTimeout * 1000)
			return;

		double multiplier = mConfig.assistMultiplier;
		double ks = 1.0;
		if (mConfig.assistAllowKillstreak)
			ks = Misc.handleKillstreak(player);

		multiplier *= ks;
		double cash = 0;
		if (killed instanceof Player)
			cash = getBaseKillPrize(killed) * multiplier / 2;
		else
			cash = getBaseKillPrize(killed) * multiplier;

		if (cash >= 0.01) {
			getDataStore().recordAssist(player, killer,
					ExtendedMobType.fromEntity(killed),
					killed.hasMetadata("MH:hasBonus"));
			mEconomy.depositPlayer(player, cash);
			debug("%s got a on assist reward (%s)", player.getName(),
					mEconomy.format(cash));

			if (ks != 1.0)
				player.sendMessage(ChatColor.GREEN
						+ ""
						+ ChatColor.ITALIC
						+ Messages.getString("mobhunting.moneygain.assist",
								"prize", mEconomy.format(cash)));
			else
				player.sendMessage(ChatColor.GREEN
						+ ""
						+ ChatColor.ITALIC
						+ Messages.getString(
								"mobhunting.moneygain.assist.bonuses", "prize",
								mEconomy.format(cash), "bonuses",
								String.format("x%.1f", ks)));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onPlayerJoin(PlayerJoinEvent event) {
		setHuntEnabled(event.getPlayer(), true);
	}

	public DamageInformation getDamageInformation(LivingEntity entity) {
		return mDamageHistory.get(entity);
	}

	/**
	 * Return the reward money for a given mob
	 * 
	 * @param mob
	 * @return value
	 */
	public double getBaseKillPrize(LivingEntity mob) {
		if (MythicMobsCompat.isMythicMobsSupported()
				&& mob.hasMetadata("MH:MythicMob")) {
			List<MetadataValue> data = mob.getMetadata("MH:MythicMob");
			MetadataValue value = data.get(0);
			return getPrice(((MobRewardData) value.value()).getRewardPrize());

		} else if (CitizensCompat.isCitizensSupported()
				&& CitizensCompat.isNPC(mob)) {
			NPCRegistry registry = CitizensAPI.getNPCRegistry();
			NPC npc = registry.getNPC(mob);
			if (CitizensCompat.isSentry(mob)) {
				return getPrice(CitizensCompat.getNPCData()
						.get(String.valueOf(npc.getId())).getRewardPrize());
			} else
				return 0;
		} else {
			if (mob instanceof Player) {
				if (mConfig.pvpKillPrize.endsWith("%")) {
					double prize = Math.floor(Double
							.valueOf(mConfig.pvpKillPrize.substring(0,
									mConfig.pvpKillPrize.length() - 1))
							* mEconomy.getBalance((Player) mob) / 100);
					return prize;
				} else if (mConfig.pvpKillPrize.contains(":")) {
					String[] str1 = mConfig.pvpKillPrize.split(":");
					double prize2 = (mRand.nextDouble()
							* (Double.valueOf(str1[1]) - Double
									.valueOf(str1[0])) + Double
							.valueOf(str1[0]));
					return Double.valueOf(prize2);
				} else
					return Double.valueOf(mConfig.pvpKillPrize);
			} else if (mob instanceof Blaze)
				return getPrice(mConfig.blazePrize);
			else if (mob instanceof Creeper)
				return getPrice(mConfig.creeperPrize);
			else if (mob instanceof Silverfish)
				return getPrice(mConfig.silverfishPrize);
			else if (mob instanceof Enderman)
				return getPrice(mConfig.endermanPrize);
			else if (mob instanceof Giant)
				return getPrice(mConfig.giantPrize);
			else if (mob instanceof Skeleton) {
				switch (((Skeleton) mob).getSkeletonType()) {
				case NORMAL:
					return getPrice(mConfig.skeletonPrize);
				case WITHER:
					return getPrice(mConfig.witherSkeletonPrize);
				}
			} else if (mob instanceof CaveSpider)
				return getPrice(mConfig.caveSpiderPrize);
			else if (mob instanceof Spider)
				return getPrice(mConfig.spiderPrize);
			else if (mob instanceof Witch)
				return getPrice(mConfig.witchPrize);
			else if (mob instanceof PigZombie)
				// PigZombie is a subclass of Zombie. PigZombie must be checked
				// before Zombie
				if (((PigZombie) mob).isBaby())
					return getPrice(mConfig.zombiePigmanPrize) * 1.2;
				else
					return getPrice(mConfig.zombiePigmanPrize);
			else if (mob instanceof Zombie)
				if (((Zombie) mob).isBaby())
					return getPrice(mConfig.zombiePrize) * 1.2;
				else
					return getPrice(mConfig.zombiePrize);
			else if (mob instanceof Ghast)
				return getPrice(mConfig.ghastPrize);
			else if (mob instanceof Slime)
				return getPrice(mConfig.slimeTinyPrize)
						* ((Slime) mob).getSize();
			else if (mob instanceof EnderDragon)
				return getPrice(mConfig.enderdragonPrize);
			else if (mob instanceof Wither)
				return getPrice(mConfig.witherPrize);
			else if (mob instanceof IronGolem)
				return getPrice(mConfig.ironGolemPrize);
			else if (mob instanceof MagmaCube)
				return getPrice(mConfig.magmaCubePrize);

			// Test if Minecraft 1.8 Mob Classes exists
			try {
				@SuppressWarnings({ "rawtypes", "unused" })
				Class cls = Class.forName("org.bukkit.entity.Guardian");
				if (mob instanceof Guardian)
					return getPrice(mConfig.guardianPrize);
				else if (mob instanceof Endermite)
					return getPrice(mConfig.endermitePrize);
				// if (mob instanceof Rabbit)
				// debug("RabbitType=" + ((Rabbit) mob).getRabbitType());
				if (mob instanceof Rabbit
						&& (((Rabbit) mob).getRabbitType()) == Rabbit.Type.THE_KILLER_BUNNY)
					return getPrice(mConfig.killerrabbitPrize);
			} catch (ClassNotFoundException e) {
				// This is not MC 1.8
			}
		}
		return 0;
	}

	private double getPrice(String str) {
		if (str.contains(":")) {
			String[] str1 = str.split(":");
			double prize = (mRand.nextDouble()
					* (Double.valueOf(str1[1]) - Double.valueOf(str1[0])) + Double
					.valueOf(str1[0]));
			return prize;
		} else
			return Double.valueOf(str);
	}

	/**
	 * Get the command to be run when the player kills a Mob.
	 * 
	 * @param mob
	 * @return a number of commands to be run in the console. Each command must
	 *         be separeted by a "|"
	 */
	public String getKillConsoleCmd(LivingEntity mob) {
		if (MythicMobsCompat.isMythicMobsSupported()
				&& mob.hasMetadata("MH:MythicMob")) {
			List<MetadataValue> data = mob.getMetadata("MH:MythicMob");
			MetadataValue value = data.get(0);
			return ((MobRewardData) value.value()).getConsoleRunCommand();

		} else if (CitizensCompat.isCitizensSupported()
				&& CitizensCompat.isNPC(mob)) {
			NPCRegistry registry = CitizensAPI.getNPCRegistry();
			NPC npc = registry.getNPC(mob);
			if (CitizensCompat.isSentry(mob)) {
				return CitizensCompat.getNPCData()
						.get(String.valueOf(npc.getId()))
						.getConsoleRunCommand();
			} else
				return "";
		} else {
			if (mob instanceof Player)
				return mConfig.pvpKillCmd;
			else if (mob instanceof Blaze)
				return mConfig.blazeCmd;
			else if (mob instanceof Creeper)
				return mConfig.creeperCmd;
			else if (mob instanceof Silverfish)
				return mConfig.silverfishCmd;
			else if (mob instanceof Enderman)
				return mConfig.endermanCmd;
			else if (mob instanceof Giant)
				return mConfig.giantCmd;
			else if (mob instanceof Skeleton) {
				switch (((Skeleton) mob).getSkeletonType()) {
				case NORMAL:
					return mConfig.skeletonCmd;
				case WITHER:
					return mConfig.witherSkeletonCmd;
				}
			} else if (mob instanceof CaveSpider)
				return mConfig.caveSpiderCmd;
			else if (mob instanceof Spider)
				return mConfig.spiderCmd;
			else if (mob instanceof Witch)
				return mConfig.witchCmd;
			else if (mob instanceof PigZombie)
				// PigZombie is a subclass of Zombie. PigZombie must be checked
				// before Zombie
				return mConfig.zombiePigmanCmd;
			else if (mob instanceof Zombie)
				return mConfig.zombieCmd;
			else if (mob instanceof Ghast)
				return mConfig.ghastCmd;
			else if (mob instanceof Slime)
				return mConfig.slimeCmd;
			else if (mob instanceof EnderDragon)
				return mConfig.enderdragonCmd;
			else if (mob instanceof Wither)
				return mConfig.witherCmd;
			else if (mob instanceof IronGolem)
				return mConfig.ironGolemCmd;
			else if (mob instanceof MagmaCube)
				return mConfig.magmaCubeCmd;

			// Test if Minecraft 1.8 Mob Classes exists
			try {
				@SuppressWarnings({ "rawtypes", "unused" })
				Class cls = Class.forName("org.bukkit.entity.Guardian");
				if (mob instanceof Guardian)
					return mConfig.guardianCmd;
				else if (mob instanceof Endermite)
					return mConfig.endermiteCmd;
				else if (mob instanceof Rabbit
						&& (((Rabbit) mob).getRabbitType()) == Rabbit.Type.THE_KILLER_BUNNY)
					return mConfig.killerrabbitCmd;
			} catch (ClassNotFoundException e) {
				// This is not MC 1.8
			}
		}
		return "";
	}

	/**
	 * Get the text to be send to the player describing the reward
	 * 
	 * @param mob
	 * @return String
	 */
	public String getKillRewardDescription(LivingEntity mob) {
		if (MythicMobsCompat.isMythicMobsSupported()
				&& mob.hasMetadata("MH:MythicMob")) {
			List<MetadataValue> data = mob.getMetadata("MH:MythicMob");
			MetadataValue value = data.get(0);
			return ((MobRewardData) value.value()).getRewardDescription();

		} else if (CitizensCompat.isCitizensSupported()
				&& CitizensCompat.isNPC(mob)) {
			NPCRegistry registry = CitizensAPI.getNPCRegistry();
			NPC npc = registry.getNPC(mob);
			if (CitizensCompat.isSentry(mob)) {
				return CitizensCompat.getNPCData()
						.get(String.valueOf(npc.getId()))
						.getRewardDescription();
			} else
				return "";
		} else {
			if (mob instanceof Player)
				return mConfig.pvpKillCmdDesc;
			else if (mob instanceof Blaze)
				return mConfig.blazeCmdDesc;
			else if (mob instanceof Creeper)
				return mConfig.creeperCmdDesc;
			else if (mob instanceof Silverfish)
				return mConfig.silverfishCmdDesc;
			else if (mob instanceof Enderman)
				return mConfig.endermanCmdDesc;
			else if (mob instanceof Giant)
				return mConfig.giantCmdDesc;
			else if (mob instanceof Skeleton) {
				switch (((Skeleton) mob).getSkeletonType()) {
				case NORMAL:
					return mConfig.skeletonCmdDesc;
				case WITHER:
					return mConfig.witherSkeletonCmdDesc;
				}
			} else if (mob instanceof CaveSpider)
				return mConfig.caveSpiderCmdDesc;
			else if (mob instanceof Spider)
				return mConfig.spiderCmdDesc;
			else if (mob instanceof Witch)
				return mConfig.witchCmdDesc;
			else if (mob instanceof PigZombie)
				// PigZombie is a subclass of Zombie. PigZombie must be checked
				// before Zombie
				return mConfig.zombiePigmanCmdDesc;
			else if (mob instanceof Zombie)
				return mConfig.zombieCmdDesc;
			else if (mob instanceof Ghast)
				return mConfig.ghastCmdDesc;
			else if (mob instanceof Slime)
				return mConfig.slimeCmdDesc;
			else if (mob instanceof EnderDragon)
				return mConfig.enderdragonCmdDesc;
			else if (mob instanceof Wither)
				return mConfig.witherCmdDesc;
			else if (mob instanceof IronGolem)
				return mConfig.ironGolemCmdDesc;
			else if (mob instanceof MagmaCube)
				return mConfig.magmaCubeCmdDesc;

			// Test if Minecraft 1.8 Mob Classes exists
			try {
				@SuppressWarnings({ "rawtypes", "unused" })
				Class cls = Class.forName("org.bukkit.entity.Guardian");
				if (mob instanceof Guardian)
					return mConfig.guardianCmdDesc;
				else if (mob instanceof Endermite)
					return mConfig.endermiteCmdDesc;
				else if (mob instanceof Rabbit
						&& (((Rabbit) mob).getRabbitType()) == Rabbit.Type.THE_KILLER_BUNNY)
					return mConfig.killerrabbitCmdDesc;

			} catch (ClassNotFoundException e) {
				// This is not MC 1.8
			}
		}
		// getLogger().warning("Warning: Missing text in getKillRewardDescription(mob="
		// + mob.getName() + "), please report to developer");
		return "";
	}

	public int getCmdRunProbability(LivingEntity mob) {
		if (MythicMobsCompat.isMythicMobsSupported()
				&& mob.hasMetadata("MH:MythicMob")) {
			List<MetadataValue> data = mob.getMetadata("MH:MythicMob");
			MetadataValue value = data.get(0);
			return ((MobRewardData) value.value()).getPropability();

		} else if (CitizensCompat.isCitizensSupported()
				&& CitizensCompat.isNPC(mob)) {
			NPCRegistry registry = CitizensAPI.getNPCRegistry();
			NPC npc = registry.getNPC(mob);
			if (CitizensCompat.isSentry(mob)) {
				return CitizensCompat.getNPCData()
						.get(String.valueOf(npc.getId())).getPropability();
			} else
				return 100;
		} else {
			if (mob instanceof Player)
				return 100;
			else if (mob instanceof Blaze)
				return mConfig.blazeFrequency;
			else if (mob instanceof Creeper)
				return mConfig.creeperFrequency;
			else if (mob instanceof Silverfish)
				return mConfig.silverfishFrequency;
			else if (mob instanceof Enderman)
				return mConfig.endermanFrequency;
			else if (mob instanceof Giant)
				return mConfig.giantFrequency;
			else if (mob instanceof Skeleton) {
				switch (((Skeleton) mob).getSkeletonType()) {
				case NORMAL:
					return mConfig.skeletonFrequency;
				case WITHER:
					return mConfig.witherSkeletonFrequency;
				}
			} else if (mob instanceof CaveSpider)
				return mConfig.caveSpiderFrequency;
			else if (mob instanceof Spider)
				return mConfig.spiderFrequency;
			else if (mob instanceof Witch)
				return mConfig.witchFrequency;
			else if (mob instanceof PigZombie)
				// PigZombie is a subclass of Zombie. PigZombie must be checked
				// before Zombie
				return mConfig.zombiePigmanFrequency;
			else if (mob instanceof Zombie)
				return mConfig.zombieFrequency;
			else if (mob instanceof Ghast)
				return mConfig.ghastFrequency;
			else if (mob instanceof Slime)
				return mConfig.slimeFrequency;
			else if (mob instanceof EnderDragon)
				return mConfig.enderdragonFrequency;
			else if (mob instanceof Wither)
				return mConfig.witherFrequency;
			else if (mob instanceof IronGolem)
				return mConfig.ironGolemFrequency;
			else if (mob instanceof MagmaCube)
				return mConfig.magmaCubeFrequency;

			// Test if Minecraft 1.8 Mob Classes exists
			try {
				@SuppressWarnings({ "rawtypes", "unused" })
				Class cls = Class.forName("org.bukkit.entity.Guardian");
				if (mob instanceof Guardian)
					return mConfig.guardianFrequency;
				else if (mob instanceof Endermite)
					return mConfig.endermiteFrequency;
				else if (mob instanceof Rabbit
						&& (((Rabbit) mob).getRabbitType()) == Rabbit.Type.THE_KILLER_BUNNY)
					return mConfig.killerrabbitFrequency;

			} catch (ClassNotFoundException e) {
				// This is not MC 1.8
			}
		}
		// getLogger().warning("Warning: Missing text in getCmdRunProbability(mob="
		// + mob.getName() + "), please report to developer");
		return 100;
	}

	public int getCmdRunProbabilityBase(LivingEntity mob) {
		if (MythicMobsCompat.isMythicMobsSupported()
				&& mob.hasMetadata("MH:MythicMob")) {
			List<MetadataValue> data = mob.getMetadata("MH:MythicMob");
			MetadataValue value = data.get(0);
			return ((MobRewardData) value.value()).getPropabilityBase();

		} else if (CitizensCompat.isCitizensSupported()
				&& CitizensCompat.isNPC(mob)) {
			NPCRegistry registry = CitizensAPI.getNPCRegistry();
			NPC npc = registry.getNPC(mob);
			if (CitizensCompat.isSentry(mob)) {
				return CitizensCompat.getNPCData()
						.get(String.valueOf(npc.getId())).getPropabilityBase();
			} else
				return 100;
		} else {
			if (mob instanceof Player)
				return 100;
			else if (mob instanceof Blaze)
				return mConfig.blazeFrequencyBase;
			else if (mob instanceof Creeper)
				return mConfig.creeperFrequencyBase;
			else if (mob instanceof Silverfish)
				return mConfig.silverfishFrequencyBase;
			else if (mob instanceof Enderman)
				return mConfig.endermanFrequencyBase;
			else if (mob instanceof Giant)
				return mConfig.giantFrequencyBase;
			else if (mob instanceof Skeleton) {
				switch (((Skeleton) mob).getSkeletonType()) {
				case NORMAL:
					return mConfig.skeletonFrequencyBase;
				case WITHER:
					return mConfig.witherSkeletonFrequencyBase;
				}
			} else if (mob instanceof CaveSpider)
				return mConfig.caveSpiderFrequencyBase;
			else if (mob instanceof Spider)
				return mConfig.spiderFrequencyBase;
			else if (mob instanceof Witch)
				return mConfig.witchFrequencyBase;
			else if (mob instanceof PigZombie)
				// PigZombie is a subclass of Zombie. PigZombie must be checked
				// before Zombie
				return mConfig.zombiePigmanFrequencyBase;
			else if (mob instanceof Zombie)
				return mConfig.zombieFrequencyBase;
			else if (mob instanceof Ghast)
				return mConfig.ghastFrequencyBase;
			else if (mob instanceof Slime)
				return mConfig.slimeFrequencyBase;
			else if (mob instanceof EnderDragon)
				return mConfig.enderdragonFrequencyBase;
			else if (mob instanceof Wither)
				return mConfig.witherFrequencyBase;
			else if (mob instanceof IronGolem)
				return mConfig.ironGolemFrequencyBase;
			else if (mob instanceof MagmaCube)
				return mConfig.magmaCubeFrequencyBase;

			// Test if Minecraft 1.8 Mob Classes exists
			try {
				@SuppressWarnings({ "rawtypes", "unused" })
				Class cls = Class.forName("org.bukkit.entity.Guardian");
				if (mob instanceof Guardian)
					return mConfig.guardianFrequencyBase;
				else if (mob instanceof Endermite)
					return mConfig.endermiteFrequencyBase;
				else if (mob instanceof Rabbit
						&& (((Rabbit) mob).getRabbitType()) == Rabbit.Type.THE_KILLER_BUNNY)
					return mConfig.killerrabbitFrequencyBase;

			} catch (ClassNotFoundException e) {
				// This is not MC 1.8
			}
		}
		// getLogger().warning("Warning: Missing text in getCmdRunProbability(mob="
		// + mob.getName() + "), please report to developer");
		return 100;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void bonusMobSpawn(CreatureSpawnEvent event) {
		if (!isHuntEnabledInWorld(event.getLocation().getWorld())
				|| (getBaseKillPrize(event.getEntity()) <= 0 && getKillConsoleCmd(
						event.getEntity()).equals(""))
				|| event.getSpawnReason() != SpawnReason.NATURAL)
			return;

		if (event.getEntityType() == EntityType.ENDER_DRAGON)
			return;

		if (mRand.nextDouble() * 100 < mConfig.bonusMobChance) {
			mParticles
					.attachEffect(event.getEntity(), Effect.MOBSPAWNER_FLAMES);
			if (mRand.nextBoolean())
				event.getEntity().addPotionEffect(
						new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE,
								Integer.MAX_VALUE, 3));
			else
				event.getEntity().addPotionEffect(
						new PotionEffect(PotionEffectType.SPEED,
								Integer.MAX_VALUE, 2));

			event.getEntity().setMetadata("MH:hasBonus",
					new FixedMetadataValue(this, true));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void spawnerMobSpawn(CreatureSpawnEvent event) {
		if (!isHuntEnabledInWorld(event.getLocation().getWorld())
				|| (getBaseKillPrize(event.getEntity()) <= 0)
				&& getKillConsoleCmd(event.getEntity()).equals(""))
			return;

		if (event.getSpawnReason() != SpawnReason.SPAWNER
				&& event.getSpawnReason() != SpawnReason.SPAWNER_EGG)
			return;

		event.getEntity().setMetadata("MH:blocked",
				new FixedMetadataValue(this, true));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void reinforcementMobSpawn(CreatureSpawnEvent event) {
		if (!isHuntEnabledInWorld(event.getLocation().getWorld())
				|| (getBaseKillPrize(event.getEntity()) <= 0)
				&& getKillConsoleCmd(event.getEntity()).equals(""))
			return;

		if (event.getSpawnReason() == SpawnReason.REINFORCEMENTS)
			event.getEntity().setMetadata("MH:reinforcement",
					new FixedMetadataValue(this, true));
	}

	public AchievementManager getAchievements() {
		return mAchievements;
	}

	public DataStoreManager getDataStore() {
		return mStoreManager;
	}

	public LeaderboardManager getLeaderboards() {
		return mLeaderboards;
	}

	// ***************************************************************************
	// UPDATECHECK - Check if there is a new version available at
	// https://api.curseforge.com/servermods/files?projectIds=63718
	// ***************************************************************************
	/**
	 * @return the updateCheck
	 */
	public Update getUpdateCheck() {
		return updateCheck;
	}

	/**
	 * Checks to see if there are any plugin updates Called when reloading
	 * settings too
	 */
	public void checkUpdates() {
		// Version checker
		getLogger().info("Checking for new updates...");
		getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
			@Override
			public void run() {
				updateCheck = new Update(63718); // MobHunting
				if (!updateCheck.isSuccess()) {
					updateCheck = null;
				}
			}
		});
	}

	public void checkUpdatesNotify(Player p) {
		boolean update = false;
		final String pluginVersion = instance.getDescription().getVersion();
		// Check to see if the latest file is newer that this one
		String[] split = instance.getUpdateCheck().getVersionName().split(" V");
		// Only do this if the format is what we expect
		if (split.length == 2) {
			// Need to escape the period in the regex expression
			String[] updateVer = split[1].split("\\.");
			// CHeck the version #'s
			String[] pluginVer = pluginVersion.split("\\.");
			// Run through major, minor, sub
			for (int i = 0; i < Math.max(updateVer.length, pluginVer.length); i++) {
				try {
					int updateCheck = 0;
					if (i < updateVer.length) {
						updateCheck = Integer.valueOf(updateVer[i]);
					}
					int pluginCheck = 0;
					if (i < pluginVer.length) {
						pluginCheck = Integer.valueOf(pluginVer[i]);
					}
					// " plugin is " + pluginCheck);
					if (updateCheck < pluginCheck) {
						// getLogger().info("["+pluginName+"]DEBUG: plugin is newer!");
						// plugin is newer
						update = false;
						break;
					} else if (updateCheck > pluginCheck) {
						update = true;
						break;
					}
				} catch (Exception e) {
					getLogger().warning(
							"Could not determine update's version # ");
					getLogger().warning("Plugin version: " + pluginVersion);
					getLogger().warning(
							"Update version: "
									+ instance.getUpdateCheck()
											.getVersionName());
					return;
				}
			}
		}
		// Show the results
		if (p != null) {
			if (!update) {
				return;
			} else {
				// Player login
				p.sendMessage(ChatColor.GOLD
						+ instance.getUpdateCheck().getVersionName()
						+ " is available! You are running " + pluginVersion);
				p.sendMessage(ChatColor.RED
						+ "Update at: http://dev.bukkit.org/server-mods/mobhunting/");
			}
		} else {
			// Console
			if (!update) {
				getLogger().info("No updates available.");
				return;
			} else {
				getLogger().info(
						"Version " + instance.getUpdateCheck().getVersionName()
								+ " is available! You are running "
								+ pluginVersion);
				getLogger()
						.info("Update at: http://dev.bukkit.org/server-mods/mobhunting/");
			}
		}
	}

}
