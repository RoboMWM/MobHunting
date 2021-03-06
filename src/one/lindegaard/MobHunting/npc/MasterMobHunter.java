package one.lindegaard.MobHunting.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import one.lindegaard.MobHunting.MobHunting;
import one.lindegaard.MobHunting.StatType;
import one.lindegaard.MobHunting.storage.IDataCallback;
import one.lindegaard.MobHunting.storage.StatStore;
import one.lindegaard.MobHunting.storage.TimePeriod;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;

public class MasterMobHunter implements IDataCallback<List<StatStore>> {

	private NPC npc;
	private List<StatStore> stats;

	public MasterMobHunter() {
	}

	public MasterMobHunter(int id, StatType statType, TimePeriod period, int numberOfKills, int rank) {
		npc = CitizensAPI.getNPCRegistry().getById(id);
		npc.getTrait(MasterMobHunterTrait.class).stattype = statType.translateName();
		npc.getTrait(MasterMobHunterTrait.class).period = period.translateName();
		npc.getTrait(MasterMobHunterTrait.class).rank = rank;
		npc.getTrait(MasterMobHunterTrait.class).noOfKills = numberOfKills;
		npc.getTrait(MasterMobHunterTrait.class).signLocations = new ArrayList<Location>();
	}

	public MasterMobHunter(NPC npc) {
		this.npc = npc;
		if (StatType.fromColumnName(npc.getTrait(MasterMobHunterTrait.class).stattype) == null) {
			MobHunting.getInstance().getLogger().warning("NPC ID=" + npc.getId()
					+ " has an invalid StatType. Resetting to " + StatType.KillsTotal.getDBColumn());
			setStatType(StatType.KillsTotal);
		}
		if (TimePeriod.getfromColumnName(npc.getTrait(MasterMobHunterTrait.class).period) == null) {
			MobHunting.getInstance().getLogger().warning("NPC ID=" + npc.getId()
					+ " has an invalid TimePeriod. Resetting to " + TimePeriod.AllTime.getDBColumn());
			setPeriod(TimePeriod.AllTime);
		}
	}

	public int getId() {
		return npc.getId();
	}

	public StatType getStatType() {
		return StatType.fromColumnName(npc.getTrait(MasterMobHunterTrait.class).stattype);
	}

	public void setStatType(StatType statType) {
		npc.getTrait(MasterMobHunterTrait.class).stattype = statType.getDBColumn();
	}

	public TimePeriod getPeriod() {
		return TimePeriod.getfromColumnName(npc.getTrait(MasterMobHunterTrait.class).period);
	}

	public void setPeriod(TimePeriod period) {
		npc.getTrait(MasterMobHunterTrait.class).period = period.getDBColumn();
	}

	public int getNumberOfKills() {
		return npc.getTrait(MasterMobHunterTrait.class).noOfKills;
	}

	public void setNumberOfKills(int numberOfKills) {
		npc.getTrait(MasterMobHunterTrait.class).noOfKills = numberOfKills;
	}

	public int getRank() {
		return npc.getTrait(MasterMobHunterTrait.class).rank;
	}

	public void setRank(int rank) {
		npc.getTrait(MasterMobHunterTrait.class).rank = rank;
	}

	private void setSignLocations(List<Location> signLocations2) {
		npc.getTrait(MasterMobHunterTrait.class).signLocations = signLocations2;
	}

	public List<Location> getSignLocations() {
		return npc.getTrait(MasterMobHunterTrait.class).signLocations;
	}

	public void putLocation(Location location) {
		if (!npc.getTrait(MasterMobHunterTrait.class).signLocations.contains(location)) {
			npc.getTrait(MasterMobHunterTrait.class).signLocations.add(location);
		}
	}

	public void removeLocation(Location location) {
		npc.getTrait(MasterMobHunterTrait.class).signLocations.remove(location);
	}

	private boolean isLoaded(Block block) {
		return (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4));
	}

	// ***********************************************************************************
	// RequestStats / DataCallBack
	// ***********************************************************************************
	public void update() {
		MobHunting.getDataStoreManager().requestStats(getStatType(), getPeriod(), 25, this);
	}

	public List<StatStore> getCurrentStats() {
		if (stats == null)
			return Collections.emptyList();
		return stats;
	}

	@Override
	public void onCompleted(List<StatStore> data) {
		ArrayList<StatStore> altData = new ArrayList<StatStore>(data.size());
		for (StatStore stat : data) {
			if (stat.getAmount() != 0 && stat.getPlayer().getName() != null) {
				altData.add(stat);
			}
		}
		stats = altData;
		refresh();
	}

	@Override
	public void onError(Throwable error) {
		error.printStackTrace();
	}

	public void refresh() {
		if (getRank() < stats.size() + 1) {
			if (getRank() != 0) {
				if (!stats.get(getRank() - 1).getPlayer().getName().equals(npc.getName())) {
					npc.setName(stats.get(getRank() - 1).getPlayer().getName());
				}
				setNumberOfKills(stats.get(getRank() - 1).getAmount());
			}
		} else {
			npc.setName("NO KILLS");
			setNumberOfKills(0);
		}
		updateSigns();
	}

	private void updateSigns() {
		if (getSignLocations().size() > 0) {
			for (Location loc : getSignLocations()) {
				Block sb = loc.getBlock();
				if (isLoaded(sb)) {
					if (MasterMobHunterSign.isSign(sb)) {
						org.bukkit.block.Sign s = (org.bukkit.block.Sign) sb.getState();
						s.setLine(1, (getRank() + ". " + npc.getName()));
						s.setLine(2, (getPeriod().translateNameFriendly()));
						s.setLine(3, (getNumberOfKills() + " " + getStatType().translateName()));
						s.update();
						if (MasterMobHunterSign.isMHSign(sb)) {
							@SuppressWarnings("deprecation")
							OfflinePlayer player = Bukkit.getPlayer(npc.getName());
							if (player != null && player.isOnline())
								MasterMobHunterSign.setPower(sb, MasterMobHunterSign.POWER_FROM_SIGN);
						}
					} else {
						loc.zero();
					}
				}
			}
		}
	}

	// ***************************************************************
	// read & convert to NPC stored data
	// ***************************************************************

	@SuppressWarnings("unchecked")
	public void read(ConfigurationSection section) throws InvalidConfigurationException, IllegalStateException {
		setStatType(StatType.fromColumnName(section.getString("stattype")));
		setPeriod(TimePeriod.parsePeriod(section.getString("period")));
		setNumberOfKills(Integer.valueOf(section.getInt("kills")));
		setRank(Integer.valueOf(section.getInt("rank")));
		if (section.contains("signs")) {
			List<Location> signLocations = new ArrayList<Location>();
			signLocations = (List<Location>) section.get("signs", signLocations);
			setSignLocations(signLocations);
		}
	}

	/**
	 * @return the npc
	 */
	public NPC getNpc() {
		return npc;
	}

	/**
	 * @param npc
	 *            the npc to set
	 */
	public void setNpc(NPC npc) {
		this.npc = npc;
	}

}
