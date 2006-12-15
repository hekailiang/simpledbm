/***
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *    Project: www.simpledbm.org
 *    Author : Dibyendu Majumdar
 *    Email  : dibyendu@mazumdar.demon.co.uk
 */
package org.simpledbm.rss.impl.isolation;

import org.simpledbm.rss.api.isolation.LockAdaptor;
import org.simpledbm.rss.api.loc.Location;
import org.simpledbm.rss.api.locking.LockDuration;
import org.simpledbm.rss.api.locking.LockMode;
import org.simpledbm.rss.api.tx.Lockable;
import org.simpledbm.rss.api.tx.Transaction;
import org.simpledbm.rss.api.tx.TransactionException;

public abstract class BaseIsolationPolicy {
	
	protected final LockAdaptor lockAdaptor;
	
	protected BaseIsolationPolicy(LockAdaptor lockAdaptor) {
		this.lockAdaptor = lockAdaptor;
	}
	
	public void lockLocation(Transaction trx, Location location, LockMode mode, LockDuration duration) throws TransactionException {
		trx.acquireLock(location, mode, duration);
	}
	
	public void lockLocationNoWait(Transaction trx, Location location, LockMode mode, LockDuration duration) throws TransactionException {
		trx.acquireLockNowait(location, mode, duration);
	}

	public void lockContainer(Transaction trx, int containerId, LockMode mode, LockDuration duration) throws TransactionException {
		Lockable ci = lockAdaptor.getLockableContainerId(containerId);
		trx.acquireLock(ci, mode, duration);
	}
	
	public void unlockLocationAfterCursorMoved(Transaction trx, Location location) throws TransactionException {
		trx.releaseLock(location);
	}

	public void unlockLocationAfterRead(Transaction trx, Location location) throws TransactionException {
		trx.releaseLock(location);
	}

	public LockMode findLocationLock(Transaction trx, Location location) {
		return trx.hasLock(location);
	}

	public LockMode findContainerLock(Transaction trx, int containerId) {
		Lockable ci = lockAdaptor.getLockableContainerId(containerId);
		return trx.hasLock(ci);
	}
	
}
