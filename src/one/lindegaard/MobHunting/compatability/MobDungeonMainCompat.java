package one.lindegaard.MobHunting.compatability;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

import one.lindegaard.MobHunting.MobHunting;

public class MobDungeonMainCompat implements Listener {
	public MobDungeonMainCompat() {
		Bukkit.getPluginManager().registerEvents(this, MobHunting.getInstance());
		MobHunting.getInstance().getLogger().info(
				"Enabling MobDungeon Compatability"); //$NON-NLS-1$
	}

}
