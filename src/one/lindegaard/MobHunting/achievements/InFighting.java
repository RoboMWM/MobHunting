package one.lindegaard.MobHunting.achievements;

import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import one.lindegaard.MobHunting.DamageInformation;
import one.lindegaard.MobHunting.Messages;
import one.lindegaard.MobHunting.MobHunting;

public class InFighting implements Achievement, Listener {

	@Override
	public String getName() {
		return Messages.getString("achievements.infighting.name");
	}

	@Override
	public String getID() {
		return "infighting";
	}

	@Override
	public String getDescription() {
		return Messages.getString("achievements.infighting.description");
	}

	@Override
	public double getPrize() {
		return MobHunting.getConfigManager().specialInfighting;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onDeath(EntityDeathEvent event) {
		if (!(event.getEntity() instanceof Skeleton)
				|| !MobHunting.getMobHuntingManager().isHuntEnabledInWorld(event.getEntity().getWorld()))
			return;

		Skeleton killed = (Skeleton) event.getEntity();

		if (!(killed.getLastDamageCause() instanceof EntityDamageByEntityEvent))
			return;

		EntityDamageByEntityEvent damage = (EntityDamageByEntityEvent) killed.getLastDamageCause();

		if (damage.getDamager() instanceof Arrow && ((Arrow) damage.getDamager()).getShooter() instanceof Skeleton) {
			Skeleton skele = (Skeleton) ((Arrow) damage.getDamager()).getShooter();

			if (killed.getTarget() == skele && skele.getTarget() == killed) {
				DamageInformation a, b;
				a = MobHunting.getDamageInformation(killed);
				b = MobHunting.getDamageInformation(skele);

				Player initiator = null;
				if (a != null)
					initiator = a.attacker;

				if (b != null && initiator == null)
					initiator = b.attacker;

				if (initiator != null && MobHunting.getMobHuntingManager().isHuntEnabled(initiator))
					MobHunting.getAchievements().awardAchievement(this, initiator);
			}
		}
	}

	@Override
	public String getPrizeCmd() {
		return MobHunting.getConfigManager().specialInfightingCmd;
	}

	@Override
	public String getPrizeCmdDescription() {
		return MobHunting.getConfigManager().specialInfightingCmdDesc;
	}
	
	@Override
	public ItemStack getSymbol() {
		return new ItemStack(Material.IRON_SWORD);
	}
}
