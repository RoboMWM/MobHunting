package one.lindegaard.MobHunting.storage;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import one.lindegaard.MobHunting.ExtendedMobType;
import one.lindegaard.MobHunting.MobHunting;
import one.lindegaard.MobHunting.StatType;
import one.lindegaard.MobHunting.achievements.Achievement;
import one.lindegaard.MobHunting.achievements.ProgressAchievement;
import one.lindegaard.MobHunting.bounty.Bounty;
import one.lindegaard.MobHunting.bounty.BountyStatus;
import one.lindegaard.MobHunting.storage.asynch.AchievementRetrieverTask;
import one.lindegaard.MobHunting.storage.asynch.DataStoreTask;
import one.lindegaard.MobHunting.storage.asynch.DeleteTask;
import one.lindegaard.MobHunting.storage.asynch.StatRetrieverTask;
import one.lindegaard.MobHunting.storage.asynch.StoreTask;
import one.lindegaard.MobHunting.storage.asynch.AchievementRetrieverTask.Mode;
import one.lindegaard.MobHunting.storage.asynch.BountyRetrieverTask;
import one.lindegaard.MobHunting.storage.asynch.BountyRetrieverTask.BountyMode;
import one.lindegaard.MobHunting.storage.asynch.DataDeleteTask;

public class DataStoreManager {
	// Accessed on multiple threads
	private HashSet<Object> mWaiting = new HashSet<Object>();

	// Accessed only from these threads
	private IDataStore mStore;
	private boolean mExit = false;

	// Accessed only from store thread
	private StoreThread mStoreThread;

	// Accessed only from retrieve thread
	private TaskThread mTaskThread;

	// Accessed only from delete thread
	// private DeleteThread mDeleteThread;
	private HashSet<Object> mWaitingDelete = new HashSet<Object>();

	public DataStoreManager(IDataStore store) {
		mStore = store;

		mTaskThread = new TaskThread();
		mStoreThread = new StoreThread(MobHunting.getConfigManager().savePeriod);
		// mDeleteThread = new
		// DeleteThread(MobHunting.getConfigManager().savePeriod);
	}

	// **************************************************************************************
	// PlayerStats
	// **************************************************************************************
	public void recordKill(OfflinePlayer player, ExtendedMobType type, boolean bonusMob) {
		synchronized (mWaiting) {
			mWaiting.add(new StatStore(StatType.fromMobType(type, true), player));
			mWaiting.add(new StatStore(StatType.KillsTotal, player));

			if (bonusMob)
				mWaiting.add(new StatStore(StatType.fromMobType(ExtendedMobType.BonusMob, true), player));
		}
	}

	public void recordAssist(OfflinePlayer player, OfflinePlayer killer, ExtendedMobType type, boolean bonusMob) {
		synchronized (mWaiting) {
			mWaiting.add(new StatStore(StatType.fromMobType(type, false), player));
			mWaiting.add(new StatStore(StatType.AssistsTotal, player));

			if (bonusMob)
				mWaiting.add(new StatStore(StatType.fromMobType(ExtendedMobType.BonusMob, false), player));
		}
	}

	public void requestStats(StatType type, TimePeriod period, int count, IDataCallback<List<StatStore>> callback) {
		mTaskThread.addTask(new StatRetrieverTask(type, period, count, mWaiting), callback);
	}

	// **************************************************************************************
	// Achievements
	// **************************************************************************************
	public void recordAchievement(OfflinePlayer player, Achievement achievement) {
		synchronized (mWaiting) {
			mWaiting.add(new AchievementStore(achievement.getID(), player, -1));
			mWaiting.add(new StatStore(StatType.AchievementCount, player));
		}
	}

	public void recordAchievementProgress(OfflinePlayer player, ProgressAchievement achievement, int progress) {
		synchronized (mWaiting) {
			mWaiting.add(new AchievementStore(achievement.getID(), player, progress));
		}
	}

	public void requestAllAchievements(OfflinePlayer player, IDataCallback<Set<AchievementStore>> callback) {
		mTaskThread.addTask(new AchievementRetrieverTask(Mode.All, player, mWaiting), callback);
	}

	public void requestCompletedAchievements(OfflinePlayer player, IDataCallback<Set<AchievementStore>> callback) {
		mTaskThread.addTask(new AchievementRetrieverTask(Mode.Completed, player, mWaiting), callback);
	}

	public void requestInProgressAchievements(OfflinePlayer player, IDataCallback<Set<AchievementStore>> callback) {
		mTaskThread.addTask(new AchievementRetrieverTask(Mode.InProgress, player, mWaiting), callback);
	}

	// *****************************************************************************
	// Bounties
	// *****************************************************************************
	public void insertBounty(Bounty bounty) {
		synchronized (mWaiting) {
			mWaiting.add(new Bounty(bounty));
		}
	}

	public void deleteBounty(Bounty bounty) {
		synchronized (mWaitingDelete) {
			mWaitingDelete.add(new Bounty(bounty));
		}
	}

	public void cancelBounty(Bounty bounty) {
		bounty.setStatus(BountyStatus.canceled);
		synchronized (mWaiting) {
			mWaiting.add(new Bounty(bounty));
		}
	}

	public void updateBounty(Bounty bounty) {
		synchronized (mWaiting) {
			mWaiting.add(new Bounty(bounty));
		}
	}

	public void requestBounties(BountyMode mode, OfflinePlayer player, IDataCallback<Set<Bounty>> callback) {
		mTaskThread.addTask(new BountyRetrieverTask(mode, player, mWaiting), callback);
	}

	// *****************************************************************************
	// PlayerSettings
	// *****************************************************************************
	/**
	 * Gets an offline player using the last known name. WARNING: This does a
	 * database lookup directly. This will block waiting for a reply
	 */
	public OfflinePlayer getPlayerByName(String name) {
		try {
			return mStore.getPlayerByName(name);
		} catch (UserNotFoundException e) {
			return null;
		} catch (DataStoreException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * @param player
	 * @return Get PlayerSettings for player. If player does not exist in
	 *         Database, a new record will be created.
	 * @throws SQLException
	 */
	public PlayerSettings getPlayerSettings(Player player) {
		try {
			return mStore.getPlayerSettings(player);
		} catch (UserNotFoundException e) {
			MobHunting.debug("Saving new PlayerSettings for %s to database.", player.getName());
			PlayerSettings ps = new PlayerSettings(player, MobHunting.getConfigManager().learningMode, false);
			try {
				mStore.insertPlayerSettings(ps);
			} catch (DataStoreException e1) {
				e1.printStackTrace();
			}
			return ps;
		} catch (DataStoreException e) {
			e.printStackTrace();
			return null;
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Update the playerSettings in the Database
	 * 
	 * @param player
	 * @param learning_mode
	 * @param muted
	 */
	public void updatePlayerSettings(Player player, boolean learning_mode, boolean muted) {
		Set<PlayerSettings> ps = new HashSet<PlayerSettings>();
		ps.add(new PlayerSettings(player, learning_mode, muted));
		try {
			mStore.updatePlayerSettings(ps);
		} catch (DataStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public int getPlayerId(OfflinePlayer offlinePlayer) throws UserNotFoundException {
		try {
			return mStore.getPlayerId(offlinePlayer);
		} catch (SQLException | DataStoreException e) {
			if (MobHunting.getConfigManager().killDebug)
				e.printStackTrace();
		}
		throw new UserNotFoundException(
				"[MobHunting] User " + offlinePlayer.getName() + " is not present in MobHunting database");
	}

	// *****************************************************************************
	// Common
	// *****************************************************************************
	public void flush() {
		MobHunting.debug("Flushing waiting data to database...");
		mTaskThread.addTask(new StoreTask(mWaiting), null);
		mTaskThread.addDeleteTask(new DeleteTask(mWaitingDelete));
	}

	public void shutdown() {
		mExit = true;
		flush();
		mTaskThread.setWriteOnlyMode(true);

		try {
			// mDeleteThread.interrupt();
			mStoreThread.interrupt();
			mTaskThread.waitForEmptyQueue();
			mTaskThread.interrupt();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void waitForUpdates() {
		flush();
		try {
			mTaskThread.waitForEmptyQueue();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private class StoreThread extends Thread {
		private int mSaveInterval;

		public StoreThread(int interval) {
			super("MH StoreThread");
			start();
			mSaveInterval = interval;
		}

		@Override
		public void run() {
			try {
				while (true) {
					synchronized (this) {
						if (mExit) {
							break;
						}
					}

					mTaskThread.addStoreTask(new StoreTask(mWaiting));
					mTaskThread.addDeleteTask(new DeleteTask(mWaitingDelete));

					Thread.sleep(mSaveInterval * 50);
				}
			} catch (InterruptedException e) {

			}
		}
	}

	private static class Task {
		public Task(DataStoreTask<?> task, IDataCallback<?> callback) {
			this.storeTask = task;
			this.deleteTask = null;
			this.callback = callback;
		}

		public Task(DataDeleteTask<?> deleteTask, IDataCallback<?> callback) {
			this.storeTask = null;
			this.deleteTask = deleteTask;
			this.callback = callback;
		}

		public Task(DataStoreTask<?> storeTask) {
			this.storeTask = storeTask;
			this.deleteTask = null;
			this.callback = null;
		}

		public DataStoreTask<?> storeTask;

		public DataDeleteTask<?> deleteTask;

		public IDataCallback<?> callback;
	}

	private static class CallbackCaller implements Runnable {
		private IDataCallback<Object> mCallback;
		private Object mObj;
		private boolean mSuccess;

		public CallbackCaller(IDataCallback<Object> callback, Object obj, boolean success) {
			mCallback = callback;
			mObj = obj;
			mSuccess = success;
		}

		@Override
		public void run() {
			if (mSuccess)
				mCallback.onCompleted(mObj);
			else
				mCallback.onError((Throwable) mObj);
		}

	}

	private class TaskThread extends Thread {
		private BlockingQueue<Task> mQueue;
		private boolean mWritesOnly = false;

		private Object mSignal = new Object();

		public TaskThread() {
			super("MH TaskThread");

			mQueue = new LinkedBlockingQueue<Task>();

			start();
		}

		public void waitForEmptyQueue() throws InterruptedException {
			if (mQueue.isEmpty())
				return;

			synchronized (mSignal) {
				mSignal.wait();
			}
		}

		public void setWriteOnlyMode(boolean writes) {
			mWritesOnly = writes;
		}

		public <T> void addTask(DataStoreTask<T> storeTask, IDataCallback<T> callback) {
			try {
				mQueue.put(new Task(storeTask, callback));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public <T> void addDeleteTask(DataDeleteTask<T> deleteTask) {
			try {
				mQueue.put(new Task(deleteTask, null));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public <T> void addStoreTask(DataStoreTask<T> storeTask) {
			try {
				mQueue.put(new Task(storeTask));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		public void run() {
			try {
				while (true) {
					if (mQueue.isEmpty()) {
						synchronized (mSignal) {
							mSignal.notifyAll();
						}
					}

					Task task = mQueue.take();

					if (mWritesOnly && ((task.storeTask == null && task.deleteTask.readOnly())
							|| (task.deleteTask == null && task.storeTask.readOnly()))) {
						continue;
					}

					try {

						Object result;

						if (task.storeTask != null) {
							result = task.storeTask.run(mStore);
						} else {
							result = task.deleteTask.run(mStore);
						}

						if (task.callback != null)
							Bukkit.getScheduler().runTask(MobHunting.getInstance(),
									new CallbackCaller((IDataCallback<Object>) task.callback, result, true));
					} catch (DataStoreException | DataDeleteException e) {
						MobHunting.debug("DataStoreManager: TaskThread.run() failed!!!!!!!");
						if (task.callback != null)
							Bukkit.getScheduler().runTask(MobHunting.getInstance(),
									new CallbackCaller((IDataCallback<Object>) task.callback, e, false));
						else
							e.printStackTrace();
					}
				}
			} catch (InterruptedException e) {
				System.out.println("[MobHunting] MH TaskThread was interrupted");
			}
		}

	}

}
