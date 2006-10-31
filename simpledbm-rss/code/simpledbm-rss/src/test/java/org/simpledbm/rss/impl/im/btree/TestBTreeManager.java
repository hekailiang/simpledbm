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
package org.simpledbm.rss.impl.im.btree;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Properties;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.simpledbm.rss.api.bm.BufferAccessBlock;
import org.simpledbm.rss.api.bm.BufferManager;
import org.simpledbm.rss.api.fsm.FreeSpaceManager;
import org.simpledbm.rss.api.im.IndexKey;
import org.simpledbm.rss.api.im.IndexKeyFactory;
import org.simpledbm.rss.api.im.IndexScan;
import org.simpledbm.rss.api.latch.LatchFactory;
import org.simpledbm.rss.api.loc.Location;
import org.simpledbm.rss.api.loc.LocationFactory;
import org.simpledbm.rss.api.locking.LockDuration;
import org.simpledbm.rss.api.locking.LockManager;
import org.simpledbm.rss.api.locking.LockMgrFactory;
import org.simpledbm.rss.api.locking.LockMode;
import org.simpledbm.rss.api.pm.Page;
import org.simpledbm.rss.api.pm.PageFactory;
import org.simpledbm.rss.api.pm.PageId;
import org.simpledbm.rss.api.registry.ObjectRegistry;
import org.simpledbm.rss.api.sp.SlottedPage;
import org.simpledbm.rss.api.sp.SlottedPageManager;
import org.simpledbm.rss.api.st.StorageContainer;
import org.simpledbm.rss.api.st.StorageContainerFactory;
import org.simpledbm.rss.api.st.StorageManager;
import org.simpledbm.rss.api.tx.LoggableFactory;
import org.simpledbm.rss.api.tx.Transaction;
import org.simpledbm.rss.api.tx.TransactionManager;
import org.simpledbm.rss.api.tx.TransactionalModuleRegistry;
import org.simpledbm.rss.api.wal.LogManager;
import org.simpledbm.rss.api.wal.Lsn;
import org.simpledbm.rss.impl.bm.BufferManagerImpl;
import org.simpledbm.rss.impl.fsm.FreeSpaceManagerImpl;
import org.simpledbm.rss.impl.im.btree.BTreeIndexManagerImpl.BTreeCursor;
import org.simpledbm.rss.impl.im.btree.BTreeIndexManagerImpl.BTreeImpl;
import org.simpledbm.rss.impl.im.btree.BTreeIndexManagerImpl.BTreeNode;
import org.simpledbm.rss.impl.im.btree.BTreeIndexManagerImpl.IndexItem;
import org.simpledbm.rss.impl.im.btree.BTreeIndexManagerImpl.LoadPageOperation;
import org.simpledbm.rss.impl.latch.LatchFactoryImpl;
import org.simpledbm.rss.impl.locking.LockManagerFactoryImpl;
import org.simpledbm.rss.impl.pm.PageFactoryImpl;
import org.simpledbm.rss.impl.registry.ObjectRegistryImpl;
import org.simpledbm.rss.impl.sp.SlottedPageManagerImpl;
import org.simpledbm.rss.impl.st.FileStorageContainerFactory;
import org.simpledbm.rss.impl.st.StorageManagerImpl;
import org.simpledbm.rss.impl.tx.LoggableFactoryImpl;
import org.simpledbm.rss.impl.tx.TransactionManagerImpl;
import org.simpledbm.rss.impl.tx.TransactionalModuleRegistryImpl;
import org.simpledbm.rss.impl.wal.LogFactoryImpl;
import org.simpledbm.rss.util.ByteString;

public class TestBTreeManager extends TestCase {

	static final short TYPE_STRINGKEYFACTORY = 25000;
	static final short TYPE_ROWLOCATIONFACTORY = 25001;
	
    boolean doCrashTesting = false;

    public TestBTreeManager() {
        super();
    }

    public TestBTreeManager(String arg0) {
        super(arg0);
    }    
    
    public TestBTreeManager(String arg0, boolean crashTesting) {
        super(arg0);
        doCrashTesting = crashTesting;
    }    
    
	/**
	 * A simple string key.
	 */
	public static class StringKey implements IndexKey {

		static final String MAX_KEY = "<INFINITY>";
		
		ByteString string = new ByteString();
		
		public void setString(String s) {
			parseString(s);
		}
	
		public void setBytes(byte[] bytes) {
			string = new ByteString(bytes);
		}
		
		@Override
		public String toString() {
			if (isMaxKey()) {
				return MAX_KEY;
			}
			return string.toString().trim();
		}
		
		public void parseString(String string) {
			if (MAX_KEY.equals(string)) {
				this.string = new ByteString(new byte[1]);
			}
			else {
				byte data[] = new byte[1024];
				Arrays.fill(data, (byte)' ');
				byte[] srcdata = string.getBytes();
				System.arraycopy(srcdata, 0, data, 0, srcdata.length);
				this.string = new ByteString(data);
			}
		}

		public int getStoredLength() {
			return string.getStoredLength();
		}

		public void retrieve(ByteBuffer bb) {
			string = new ByteString();
			string.retrieve(bb);
		}

		public void store(ByteBuffer bb) {
			string.store(bb);
		}
		
		public boolean isMaxKey() {
			return string.length() == 1 && string.get(0) == 0;
		}
		
		public int compareTo(IndexKey o) {
			StringKey sk = (StringKey) o;
			if (isMaxKey() || sk.isMaxKey()) {
				if (isMaxKey() && sk.isMaxKey()) {
					return 0;
				}
				else if (isMaxKey()) {
					return 1;
				}
				else {
					return -1;
				}
			}
			return string.compareTo(sk.string);
		}

        @Override
        public boolean equals(Object arg0) {
            return compareTo((IndexKey)arg0) == 0;
        }	
	}
	
	public static class StringKeyFactory implements IndexKeyFactory {

		public IndexKey newIndexKey(int id) {
			return new StringKey();
		}

		public IndexKey maxIndexKey(int id) {
			StringKey s = new StringKey();
			s.setBytes(new byte[1]);
			return s;
		}
	}
	
	/**
	 * A simple location.
	 */
	public static class RowLocation implements Location {

		int loc;

		public void parseString(String string) {
			loc = Integer.parseInt(string);
		}

		public void retrieve(ByteBuffer bb) {
			loc = bb.getInt();
		}

		public void store(ByteBuffer bb) {
			bb.putInt(loc);
		}

		public int getStoredLength() {
			return Integer.SIZE/Byte.SIZE;
		}

		public int compareTo(Location o) {
			RowLocation rl = (RowLocation) o;
			return loc - rl.loc;
		}

		@Override
		public boolean equals(Object o) {
			return compareTo((Location)o) == 0;
		}
		
		@Override
		public int hashCode() {
			return loc;
		}

		@Override
		public String toString() {
			return Integer.toString(loc);
		}
	}
	
	public static class RowLocationFactory implements LocationFactory {

		public Location newLocation() {
			return new RowLocation();
		}
		
	}

	private IndexItem generateKey(BTreeImpl btree, String s, int location, int childpage, boolean isLeaf) {
		StringKey key = (StringKey) btree.getNewIndexKey();
		key.setString(s);
		RowLocation loc = (RowLocation) btree.getNewLocation();
		loc.loc = location;
		IndexItem item = new IndexItem(key, loc, childpage, isLeaf, btree.isUnique());
		return item;
	}

	/**
	 * Initialize the test harness. New log is created, and the
	 * test container initialized. The container is allocated an extent of
	 * 64 pages which ought to be large enough for all the test cases.
	 */
	void doInitContainer() throws Exception {

		final BTreeDB db = new BTreeDB(true);
		
		try {
	    	Transaction trx = db.trxmgr.begin();
			db.spacemgr.createContainer(trx, "testctr.dat", 1, 1, 64, db.spmgr.getPageType());
			trx.commit();
		}
		finally {
			db.shutdown();
		}		
	}
	
	/**
	 * Initialize data pages by loading data from specified XML resource.
	 */
	public void doLoadXml(boolean testUnique, String dataFile) throws Exception {

		final BTreeDB db = new BTreeDB(false);
		
		try {
			BTreeIndexManagerImpl.XMLLoader loader = new BTreeIndexManagerImpl.XMLLoader(db.btreeMgr);
			Transaction trx = db.trxmgr.begin();
			try {
				loader.parseResource(dataFile);
				for (LoadPageOperation loadPageOp : loader.getPageOperations()) {
					final PageId pageid = loadPageOp.getPageId();
					final BTreeIndexManagerImpl btreemgr = db.btreeMgr;
					BufferAccessBlock bab = btreemgr.bufmgr.fixExclusive(
							pageid, false, -1, 0);
					try {
						PageId spaceMapPageId = new PageId(pageid
								.getContainerId(), loadPageOp
								.getSpaceMapPageNumber());
						BufferAccessBlock smpBab = btreemgr.bufmgr
								.fixExclusive(spaceMapPageId, false, -1, 0);
						try {
							Lsn lsn = trx.logInsert(bab.getPage(), loadPageOp);
							btreemgr.redo(bab.getPage(), loadPageOp);
							bab.setDirty(lsn);
							btreemgr.redo(smpBab.getPage(), loadPageOp);
							smpBab.setDirty(lsn);
						} finally {
							smpBab.unfix();
						}
					} finally {
						bab.unfix();
					}
				}
			}
			finally {
				// Doesn't matter whether we commit or abort as the changes are
				// logged as redo-only.
				trx.abort();
			}
		}
		finally {
			db.shutdown();
		}		
	}

	/**
	 * Initialize data pages by loading data from specified XML resource.
	 */
	public void doValidateTree(String dataFile) throws Exception {

		final BTreeDB db = new BTreeDB(false);
		try {
			doValidateTree(db, dataFile);
		}
		finally {
			db.shutdown();
		}		
	}

	/**
	 * Initialize data pages by loading data from specified XML resource.
	 */
	public void doValidateTree(final BTreeDB db, String dataFile)
			throws Exception {

		BTreeIndexManagerImpl.XMLLoader loader = new BTreeIndexManagerImpl.XMLLoader(
				db.btreeMgr);
		loader.parseResource(dataFile);
		for (LoadPageOperation loadPageOp : loader.getPageOperations()) {
			final PageId pageid = loadPageOp.getPageId();
			final BTreeIndexManagerImpl btreemgr = db.btreeMgr;
			BufferAccessBlock bab = btreemgr.bufmgr.fixExclusive(pageid, false,
					-1, 0);
			try {
				/*
				 * Log record is being applied to BTree page.
				 */
				SlottedPage r = (SlottedPage) bab.getPage();
				BTreeNode node = new BTreeNode(loadPageOp);
				node.wrap(r);
				System.out.println("Validating page ->");
				// node.dumpAsXml();
				System.out.println("------------------");
				assertEquals(node.header.keyCount, loadPageOp.items.size());
				assertEquals(node.header.leftSibling, loadPageOp.leftSibling);
				assertEquals(node.header.rightSibling, loadPageOp.rightSibling);
				assertEquals(node.header.keyFactoryType, loadPageOp
						.getKeyFactoryType());
				assertEquals(node.header.locationFactoryType, loadPageOp
						.getLocationFactoryType());
				assertEquals(node.isLeaf(), loadPageOp.isLeaf());
				assertEquals(node.isUnique(), loadPageOp.isUnique());
				for (int k = 1, i = 0; k < r.getNumberOfSlots(); k++, i++) {
					if (r.isSlotDeleted(k)) {
						continue;
					}
					IndexItem item = loadPageOp.items.get(i);
					IndexItem item1 = node.getItem(k);
					assertEquals(item, item1);
				}
			} finally {
				bab.unfix();
			}
		}
	}
	
	
	
	/*
	 * Splits page 2 into 2 and 3.
	 */
	void doPageSplit(boolean testLeaf, boolean testUnique) throws Exception {
		/* Create the write ahead log */
		final BTreeDB db = new BTreeDB(false);
		
		try {
			final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);
			
			int pageNumber = 2;
			Transaction trx;
			
			BTreeCursor bcursor = new BTreeCursor();
			bcursor.setQ(db.bufmgr.fixForUpdate(new PageId(1, pageNumber), 0));
			try {
		    	trx = db.trxmgr.begin();
		    	boolean okay = false;
		    	try {
			    	System.out.println("--> SPLITTING PAGE");
					bcursor.searchKey = generateKey(btree, "da", 5, -1, testLeaf);
		    		btree.doSplit(trx, bcursor);
		    	}
		    	finally {
		    		if (okay)
		    			trx.commit();
		    		else {
		    			trx.abort();
		    		}
		    	}
			}
			finally {
				bcursor.unfixQ();
			}
		}
		finally {
			db.shutdown();
		}		
	}
	
	/*
	 * Merges previously split pages 2 and 3.
	 */
	void doRestartAndMerge(boolean testLeaf, boolean testUnique, int l, int r) throws Exception {
		final BTreeDB db = new BTreeDB(false);

		try {
			final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);

	    	System.out.println("--> BEFORE MERGE");
	    	BufferAccessBlock bab = db.bufmgr.fixShared(new PageId(1, l), 0);
	    	try {
	    		BTreeNode node = btree.getBTreeNode();
	    		node.wrap((SlottedPage) bab.getPage());
	    		node.dump();
	    	}
	    	finally {
	    		bab.unfix();
	    	}
	    	bab = db.bufmgr.fixShared(new PageId(1, r), 0);
	    	try {
	    		BTreeNode node = btree.getBTreeNode();
	    		node.wrap((SlottedPage) bab.getPage());
	    		node.dump();
	    	}
	    	finally {
	    		bab.unfix();
	    	}	    	

	    	BTreeCursor bcursor = new BTreeCursor();
			bcursor.setQ(db.bufmgr.fixForUpdate(new PageId(1, l), 0));
			bcursor.setR(db.bufmgr.fixForUpdate(new PageId(1, r), 0));
			try {
		    	Transaction trx = db.trxmgr.begin();
		    	boolean okay = false;
		    	try {
			    	System.out.println("--> MERGING PAGE");
		    		btree.doMerge(trx, bcursor);
		    	}
		    	finally {
		    		if (okay)
		    			trx.commit();
		    		else {
		    			trx.abort();
		    		}
		    	}
			}
			finally {
				bcursor.unfixQ();
			}
		}
		finally {
			db.shutdown();
		}		
	}

	/*
	 * Merges previously split pages 2 and 3.
	 */
	void doRestart() throws Exception {
		final BTreeDB db = new BTreeDB(false);
		try {
	    	System.out.println("RESTART PROCESSING COMPLETED");
		}
		finally {
			db.shutdown();
		}		
	}
	
	
	void doRestartAndLink(boolean testLeaf, boolean testUnique) throws Exception {
		final BTreeDB db = new BTreeDB(false);
		try {
			final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);

			System.out.println("--> BEFORE LINKING CHILD TO PARENT");
	    	BufferAccessBlock bab = db.bufmgr.fixShared(new PageId(1, 2), 0);
	    	try {
	    		BTreeNode node = btree.getBTreeNode();
	    		node.wrap((SlottedPage) bab.getPage());
	    		node.dump();
	    	}
	    	finally {
	    		bab.unfix();
	    	}
	    	bab = db.bufmgr.fixShared(new PageId(1, 3), 0);
	    	try {
	    		BTreeNode node = btree.getBTreeNode();
	    		node.wrap((SlottedPage) bab.getPage());
	    		node.dump();
	    	}
	    	finally {
	    		bab.unfix();
	    	}	    	

	    	// prepareParentPage(pageFactory, bufmgr, btree, 1, 4, testUnique, 2);
	    	
	    	BTreeCursor bcursor = new BTreeCursor();
			bcursor.setQ(db.bufmgr.fixForUpdate(new PageId(1, 2), 0));
			bcursor.setR(db.bufmgr.fixForUpdate(new PageId(1, 3), 0));
			bcursor.setP(db.bufmgr.fixForUpdate(new PageId(1, 4), 0));
			try {
		    	Transaction trx = db.trxmgr.begin();
		    	boolean okay = false;
		    	try {
			    	System.out.println("--> LINKING CHILD TO PARENT");
		    		btree.doLink(trx, bcursor);
		    	}
		    	finally {
		    		if (okay)
		    			trx.commit();
		    		else {
		    			trx.abort();
		    		}
		    	}
			}
			finally {
				bcursor.unfixQ();
				bcursor.unfixR();
				bcursor.unfixP();
			}

			bcursor.setQ(db.bufmgr.fixForUpdate(new PageId(1, 2), 0));
			bcursor.setR(db.bufmgr.fixForUpdate(new PageId(1, 3), 0));
			bcursor.setP(db.bufmgr.fixForUpdate(new PageId(1, 4), 0));
			try {
		    	Transaction trx = db.trxmgr.begin();
		    	boolean okay = false;
		    	try {
			    	System.out.println("--> DE-LINKING CHILD FROM PARENT");
		    		btree.doUnlink(trx, bcursor);
		    	}
		    	finally {
		    		if (okay)
		    			trx.commit();
		    		else {
		    			trx.abort();
		    		}
		    	}
			}
			finally {
				bcursor.unfixQ();
				bcursor.unfixR();
			}
		
		}
		finally {
			db.shutdown();
		}		
	}

	void doRestartLink(boolean testLeaf, boolean testUnique) throws Exception {
		final BTreeDB db = new BTreeDB(false);
		try {
			final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);

			System.out.println("--> BEFORE LINKING CHILD TO PARENT");
	    	BufferAccessBlock bab = db.bufmgr.fixShared(new PageId(1, 2), 0);
	    	try {
	    		BTreeNode node = btree.getBTreeNode();
	    		node.wrap((SlottedPage) bab.getPage());
	    		node.dump();
	    	}
	    	finally {
	    		bab.unfix();
	    	}
	    	bab = db.bufmgr.fixShared(new PageId(1, 3), 0);
	    	try {
	    		BTreeNode node = btree.getBTreeNode();
	    		node.wrap((SlottedPage) bab.getPage());
	    		node.dump();
	    	}
	    	finally {
	    		bab.unfix();
	    	}	    	

	    	// prepareParentPage(pageFactory, bufmgr, btree, 1, 4, testUnique, 2);
	    	
	    	BTreeCursor bcursor = new BTreeCursor();
			bcursor.setQ(db.bufmgr.fixForUpdate(new PageId(1, 2), 0));
			bcursor.setR(db.bufmgr.fixForUpdate(new PageId(1, 3), 0));
			bcursor.setP(db.bufmgr.fixForUpdate(new PageId(1, 4), 0));
			try {
		    	Transaction trx = db.trxmgr.begin();
		    	boolean okay = false;
		    	try {
			    	System.out.println("--> LINKING CHILD TO PARENT");
		    		btree.doLink(trx, bcursor);
		    	}
		    	finally {
		    		if (okay)
		    			trx.commit();
		    		else {
		    			trx.abort();
		    		}
		    	}
			}
			finally {
				bcursor.unfixQ();
				bcursor.unfixR();
				bcursor.unfixP();
			}
		}
		finally {
			db.shutdown();
		}		
	}

	void doRestartDelink(boolean testLeaf, boolean testUnique) throws Exception {
		final BTreeDB db = new BTreeDB(false);
		try {
			final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);

	    	BTreeCursor bcursor = new BTreeCursor();
			bcursor.setQ(db.bufmgr.fixForUpdate(new PageId(1, 2), 0));
			bcursor.setR(db.bufmgr.fixForUpdate(new PageId(1, 3), 0));
			bcursor.setP(db.bufmgr.fixForUpdate(new PageId(1, 4), 0));
			try {
		    	Transaction trx = db.trxmgr.begin();
		    	boolean okay = false;
		    	try {
			    	System.out.println("--> DE-LINKING CHILD FROM PARENT");
		    		btree.doUnlink(trx, bcursor);
		    	}
		    	finally {
		    		if (okay)
		    			trx.commit();
		    		else {
		    			trx.abort();
		    		}
		    	}
			}
			finally {
				bcursor.unfixQ();
				bcursor.unfixR();
			}
		
		}
		finally {
			db.shutdown();
		}		
	}
	
	
	void doRestartAndUnlink(boolean testLeaf, boolean testUnique, int p, int q, int r) throws Exception {
		final BTreeDB db = new BTreeDB(false);
		try {
			final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);

	    	BTreeCursor bcursor = new BTreeCursor();
			bcursor.setQ(db.bufmgr.fixForUpdate(new PageId(1, q), 0));
			bcursor.setR(db.bufmgr.fixForUpdate(new PageId(1, r), 0));
			bcursor.setP(db.bufmgr.fixForUpdate(new PageId(1, p), 0));
			try {
		    	Transaction trx = db.trxmgr.begin();
		    	boolean okay = false;
		    	try {
			    	System.out.println("--> DE-LINKING CHILD FROM PARENT");
		    		btree.doUnlink(trx, bcursor);
		    	}
		    	finally {
		    		if (okay)
		    			trx.commit();
		    		else {
		    			trx.abort();
		    		}
		    	}
			}
			finally {
				bcursor.unfixQ();
				bcursor.unfixR();
			}
		}
		finally {
			db.shutdown();
		}		
	}
	
	
	void doRestartAndRedistribute(boolean testLeaf, boolean testUnique) throws Exception {
		final BTreeDB db = new BTreeDB(false);
		try {
			final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);

	    	System.out.println("--> BEFORE REDISTRIBUTING KEYS");
	    	BufferAccessBlock bab = db.bufmgr.fixShared(new PageId(1, 2), 0);
	    	try {
	    		BTreeNode node = btree.getBTreeNode();
	    		node.wrap((SlottedPage) bab.getPage());
	    		node.dump();
	    	}
	    	finally {
	    		bab.unfix();
	    	}
	    	bab = db.bufmgr.fixShared(new PageId(1, 3), 0);
	    	try {
	    		BTreeNode node = btree.getBTreeNode();
	    		node.wrap((SlottedPage) bab.getPage());
	    		node.dump();
	    	}
	    	finally {
	    		bab.unfix();
	    	}	    	

	    	BTreeCursor bcursor = new BTreeCursor();
			bcursor.setQ(db.bufmgr.fixForUpdate(new PageId(1, 2), 0));
			bcursor.setR(db.bufmgr.fixForUpdate(new PageId(1, 3), 0));
			try {
		    	Transaction trx = db.trxmgr.begin();
		    	boolean okay = false;
		    	try {
					bcursor.searchKey = generateKey(btree, "da", 5, -1, testLeaf);
			    	System.out.println("--> REDISTRIBUTING KEYS");
		    		btree.doRedistribute(trx, bcursor);
		    	}
		    	finally {
		    		if (okay)
		    			trx.commit();
		    		else {
		    			trx.abort();
		    		}
		    	}
			}
			finally {
				bcursor.unfixQ();
			}
		}
		finally {
			db.shutdown();
		}		
	}

	void doRestartAndIncreaseTreeHeight(boolean testLeaf, boolean testUnique) throws Exception {
		final BTreeDB db = new BTreeDB(false);
		try {
			final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);

	    	System.out.println("--> BEFORE INCREASING TREE HEIGHT");
	    	BufferAccessBlock bab = db.bufmgr.fixShared(new PageId(1, 2), 0);
	    	try {
	    		BTreeNode node = btree.getBTreeNode();
	    		node.wrap((SlottedPage) bab.getPage());
	    		node.dump();
	    	}
	    	finally {
	    		bab.unfix();
	    	}
	    	bab = db.bufmgr.fixShared(new PageId(1, 3), 0);
	    	try {
	    		BTreeNode node = btree.getBTreeNode();
	    		node.wrap((SlottedPage) bab.getPage());
	    		node.dump();
	    	}
	    	finally {
	    		bab.unfix();
	    	}	    	

	    	BTreeCursor bcursor = new BTreeCursor();
			bcursor.setQ(db.bufmgr.fixForUpdate(new PageId(1, 2), 0));
			bcursor.setR(db.bufmgr.fixForUpdate(new PageId(1, 3), 0));
			try {
		    	Transaction trx = db.trxmgr.begin();
		    	boolean okay = false;
		    	try {
					bcursor.searchKey = generateKey(btree, "da", 5, -1, testLeaf);
			    	System.out.println("--> INCREASING TREE HEIGHT");
		    		btree.doIncreaseTreeHeight(trx, bcursor);
		    	}
		    	finally {
		    		if (okay)
		    			trx.commit();
		    		else {
		    			trx.abort();
		    		}
		    	}
			}
			finally {
				bcursor.unfixQ();
			}
		}
		finally {
			db.shutdown();
		}		
	}

	void doRestartAndDecreaseTreeHeight(boolean testLeaf, boolean testUnique, int p, int q) throws Exception {
		final BTreeDB db = new BTreeDB(false);
		try {
			final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);

	    	System.out.println("--> BEFORE DECREASING TREE HEIGHT");
	    	BufferAccessBlock bab = db.bufmgr.fixShared(new PageId(1, p), 0);
	    	try {
	    		BTreeNode node = btree.getBTreeNode();
	    		node.wrap((SlottedPage) bab.getPage());
	    		node.dump();
	    	}
	    	finally {
	    		bab.unfix();
	    	}
	    	bab = db.bufmgr.fixShared(new PageId(1, q), 0);
	    	try {
	    		BTreeNode node = btree.getBTreeNode();
	    		node.wrap((SlottedPage) bab.getPage());
	    		node.dump();
	    	}
	    	finally {
	    		bab.unfix();
	    	}	    	

	    	BTreeCursor bcursor = new BTreeCursor();
			bcursor.setP(db.bufmgr.fixForUpdate(new PageId(1, p), 0));
			bcursor.setQ(db.bufmgr.fixForUpdate(new PageId(1, q), 0));
			try {
		    	Transaction trx = db.trxmgr.begin();
		    	boolean okay = false;
		    	try {
					bcursor.searchKey = generateKey(btree, "da", 5, -1, testLeaf);
			    	System.out.println("--> DECREASING TREE HEIGHT");
		    		btree.doDecreaseTreeHeight(trx, bcursor);
		    	}
		    	finally {
		    		if (okay)
		    			trx.commit();
		    		else {
		    			trx.abort();
		    		}
		    	}
			}
			finally {
				bcursor.unfixP();
			}
		}
		finally {
			db.shutdown();
		}		
	}

	void doSingleInsert(boolean testUnique, boolean commit, String k, String loc) throws Exception {
		doSingleInsert(testUnique, commit, k, loc, null, null);
	}
	void doSingleInsert(boolean testUnique, boolean commit, String k, String loc, String commitresult, String abortresult) throws Exception {
		final BTreeDB db = new BTreeDB(false);
		try {
			final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);

			IndexKeyFactory keyFactory = (IndexKeyFactory) db.objectFactory.getInstance(TYPE_STRINGKEYFACTORY);
			LocationFactory locationFactory = (LocationFactory) db.objectFactory.getInstance(TYPE_ROWLOCATIONFACTORY);
			IndexKey key = keyFactory.newIndexKey(1);
			key.parseString(k);
			Location location = locationFactory.newLocation();
			location.parseString(loc);
			Transaction trx = db.trxmgr.begin();
			boolean okay = false;
			try {
				System.out.println("--> INSERTING KEY");
				btree.insert(trx, key, location);
				if (commitresult != null) {
					doValidateTree(db, commitresult);
				}
				okay = true;
			} finally {
				if (okay && commit) {
					System.out.println("--> COMMITTING INSERT");
					trx.commit();
					if (commitresult != null) {
						doValidateTree(db, commitresult);
					}
				}
				else {
					System.out.println("--> ABORTING INSERT");
					trx.abort();
					if (abortresult != null) {
						doValidateTree(db, abortresult);
					}
				}
			}
		} finally {
			db.shutdown();
		}		
	}

	void doDoubleInsert(boolean testUnique, boolean commit1, String k1, String loc1, 
			boolean commit2, String k2, String loc2, String result1, String result2, String result3) throws Exception {
		final BTreeDB db = new BTreeDB(false);
		try {
			final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);

			IndexKeyFactory keyFactory = (IndexKeyFactory) db.objectFactory.getInstance(TYPE_STRINGKEYFACTORY);
			LocationFactory locationFactory = (LocationFactory) db.objectFactory.getInstance(TYPE_ROWLOCATIONFACTORY);
			
			IndexKey key1 = keyFactory.newIndexKey(1);
			key1.parseString(k1);
			Location location1 = locationFactory.newLocation();
			location1.parseString(loc1);
			Transaction trx1 = db.trxmgr.begin();
			boolean okay1 = false;
			try {
				System.out.println("--> INSERTING KEY 1");
				btree.insert(trx1, key1, location1);
				doValidateTree(db, result1);
				okay1 = true;
				
				IndexKey key2 = keyFactory.newIndexKey(1);
				key2.parseString(k2);
				Location location2 = locationFactory.newLocation();
				location2.parseString(loc2);
				Transaction trx2 = db.trxmgr.begin();
				boolean okay2 = false;

				try {
					System.out.println("--> INSERTING KEY 2");
					btree.insert(trx2, key2, location2);
					doValidateTree(db, result2);
					okay2 = true;
				}
				finally {
					if (okay2 && commit2) {
						System.out.println("--> COMMITTING KEY 2");
						trx2.commit();
					}
					else {
						System.out.println("--> ABORTING KEY 2");
						trx2.abort();
					}
				}
			} finally {
				if (okay1 && commit1) {
					System.out.println("--> COMMITTING KEY 1");
					trx1.commit();
				}
				else {
					System.out.println("--> ABORTING KEY 1");
					trx1.abort();
					doValidateTree(db, result3);
				}
			}
		} finally {
			db.shutdown();
		}		
	}

	void doSingleDelete(boolean testUnique, boolean commit, String k, String loc) throws Exception {
		final BTreeDB db = new BTreeDB(false);
		try {
			final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);

			IndexKeyFactory keyFactory = (IndexKeyFactory) db.objectFactory.getInstance(TYPE_STRINGKEYFACTORY);
			LocationFactory locationFactory = (LocationFactory) db.objectFactory.getInstance(TYPE_ROWLOCATIONFACTORY);
			IndexKey key = keyFactory.newIndexKey(1);
			key.parseString(k);
			Location location = locationFactory.newLocation();
			location.parseString(loc);
			Transaction trx = db.trxmgr.begin();
			boolean okay = false;
			try {
				System.out.println("--> DELETING KEY");
				btree.delete(trx, key, location);
				okay = true;
			} finally {
				if (okay && commit)
					trx.commit();
				else {
					trx.abort();
				}
			}
		} finally {
			db.shutdown();
		}		
	}

	boolean t1Failed = false;
	boolean t2Failed = false;
	
	void doDeleteInsertThreads(final boolean testUnique, final boolean commit, final String k, final String loc) throws Exception {
		final BTreeDB db = new BTreeDB(false);
    	final boolean testingUniqueIndex = testUnique;
		try {
			Thread t1 = new Thread(new Runnable() {
				public void run() {
					try {
						final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testingUniqueIndex);
						IndexKeyFactory keyFactory = (IndexKeyFactory) db.objectFactory.getInstance(TYPE_STRINGKEYFACTORY);
						LocationFactory locationFactory = (LocationFactory) db.objectFactory.getInstance(TYPE_ROWLOCATIONFACTORY);
						IndexKey key = keyFactory.newIndexKey(1);
						key.parseString(k);
						Location location = locationFactory.newLocation();
						location.parseString(loc);
						Transaction trx = db.trxmgr.begin();
						boolean okay = false;
						try {
							System.out.println("--> DELETING KEY");
							btree.delete(trx, key, location);
							Thread.sleep(15000);
							okay = true;
						} finally {
							if (okay && commit)
								trx.commit();
							else {
								trx.abort();
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
						t1Failed = true;
					}
				}
			}, "T1");

			Thread t2 = new Thread(new Runnable() {
				public void run() {
					try {
						final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testingUniqueIndex);
						IndexKeyFactory keyFactory = (IndexKeyFactory) db.objectFactory.getInstance(TYPE_STRINGKEYFACTORY);
						LocationFactory locationFactory = (LocationFactory) db.objectFactory.getInstance(TYPE_ROWLOCATIONFACTORY);
						IndexKey key = keyFactory.newIndexKey(1);
						key.parseString(k);
						Location location = locationFactory.newLocation();
						location.parseString(loc);
						Transaction trx = db.trxmgr.begin();
						boolean okay = false;
						try {
							System.out.println("--> INSERTING KEY");
							btree.insert(trx, key, location);
							okay = true;
						} finally {
							if (okay && commit)
								trx.commit();
							else {
								trx.abort();
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
						t2Failed = true;
					}
				}
			}, "T2");
			
			t1Failed = false;
			t2Failed = false;
			t1.start();
			Thread.sleep(1000);
			t2.start();
			t1.join();
			t2.join();
			assertTrue(!t1.isAlive());
			assertTrue(!t2.isAlive());
			
		} finally {
			db.shutdown();
		}		
	}

    void doScanAndDelete(boolean testUnique, boolean commit, String k, String loc) throws Exception {
		final BTreeDB db = new BTreeDB(false);
        try {
            final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);

            IndexKeyFactory keyFactory = (IndexKeyFactory) db.objectFactory.getInstance(TYPE_STRINGKEYFACTORY);
            LocationFactory locationFactory = (LocationFactory) db.objectFactory.getInstance(TYPE_ROWLOCATIONFACTORY);
            IndexKey key = keyFactory.newIndexKey(1);
            key.parseString(k);
            Location location = locationFactory.newLocation();
            location.parseString(loc);
            Transaction trx = db.trxmgr.begin();
            boolean okay = false;
            IndexScan scan = btree.openScan(key, location, LockMode.UPDATE);
            try {
                System.out.println("--> SCANNING TREE");
                while (scan.fetchNext(trx)) {
                	System.out.println("SCAN=" + scan.getCurrentKey() + "," + scan.getCurrentLocation());
                	trx.acquireLock(scan.getCurrentLocation(), LockMode.EXCLUSIVE, LockDuration.MANUAL_DURATION);
                	if (scan.isEof()) {
                		break;
                	}
                	System.out.println("DELETING=" + scan.getCurrentKey() + "," + scan.getCurrentLocation());
                	btree.delete(trx, scan.getCurrentKey(), scan.getCurrentLocation());
                }
            } finally {
                scan.close();
                if (!doCrashTesting) {
					if (okay && commit)
						trx.commit();
					else {
						trx.abort();
					}
				}
            }
        } finally {
			db.shutdown();
        }
    }
	
	
    void doScanTree(boolean testUnique, boolean commit, String k, String loc) throws Exception {
		final BTreeDB db = new BTreeDB(false);
        try {
            final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testUnique);

            IndexKeyFactory keyFactory = (IndexKeyFactory) db.objectFactory.getInstance(TYPE_STRINGKEYFACTORY);
            LocationFactory locationFactory = (LocationFactory) db.objectFactory.getInstance(TYPE_ROWLOCATIONFACTORY);
            IndexKey key = keyFactory.newIndexKey(1);
            key.parseString(k);
            Location location = locationFactory.newLocation();
            location.parseString(loc);
            Transaction trx = db.trxmgr.begin();
            boolean okay = false;
            IndexScan scan = btree.openScan(key, location, LockMode.SHARED);
            try {
                System.out.println("--> SCANNING TREE");
                while (scan.fetchNext(trx)) {
                	System.out.println("SCAN NEXT=" + scan.getCurrentKey() + "," + scan.getCurrentLocation());
                }
            } finally {
                scan.close();
                if (okay && commit)
                    trx.commit();
                else {
                    trx.abort();
                }
            }
        } finally {
			db.shutdown();
        }
    }

    void doDeleteAndScanThreads(final boolean testUnique, final boolean commit, final String k, final String loc) throws Exception {
		final BTreeDB db = new BTreeDB(false);
        final boolean testingUniqueIndex = testUnique;
        try {
            Thread t1 = new Thread(new Runnable() {
                public void run() {
                    try {
                        final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testingUniqueIndex);
                        IndexKeyFactory keyFactory = (IndexKeyFactory) db.objectFactory.getInstance(TYPE_STRINGKEYFACTORY);
                        LocationFactory locationFactory = (LocationFactory) db.objectFactory.getInstance(TYPE_ROWLOCATIONFACTORY);
                        IndexKey key = keyFactory.newIndexKey(1);
                        key.parseString(k);
                        Location location = locationFactory.newLocation();
                        location.parseString(loc);
                        Transaction trx = db.trxmgr.begin();
                        boolean okay = false;
                        try {
                            System.out.println("--> DELETING KEY [" + k + "," + loc + "]");
                            btree.delete(trx, key, location);
                            Thread.sleep(15000);
                            okay = true;
                        } finally {
                            if (okay && commit) {
                                System.out.println("--> COMMITTING DELETE OF KEY [" + k + "," + loc + "]");
                                trx.commit();
                            }
                            else {
                                System.out.println("--> ABORTING DELETE OF KEY [" + k + "," + loc + "]");
                                trx.abort();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        t1Failed = true;
                    }
                }
            }, "T1");

            Thread t2 = new Thread(new Runnable() {
                public void run() {
                    try {
                        final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testingUniqueIndex);
                        IndexKeyFactory keyFactory = (IndexKeyFactory) db.objectFactory.getInstance(TYPE_STRINGKEYFACTORY);
                        LocationFactory locationFactory = (LocationFactory) db.objectFactory.getInstance(TYPE_ROWLOCATIONFACTORY);
                        IndexKey key = keyFactory.newIndexKey(1);
                        key.parseString("a1");
                        Location location = locationFactory.newLocation();
                        location.parseString("10");
                        Transaction trx = db.trxmgr.begin();
                        boolean okay = false;
                        IndexScan scan = btree.openScan(key, location, LockMode.UPDATE);
                        try {
                            System.out.println("--> SCANNING TREE");
                            while (scan.fetchNext(trx)) {
                                System.out.println("SCAN=" + scan.getCurrentKey() + "," + scan.getCurrentLocation());
                                if (scan.isEof()) {
                                    break;
                                }
                            }
                        } finally {
                            scan.close();
                            if (okay && commit)
                                trx.commit();
                            else {
                                trx.abort();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        t2Failed = true;
                    }
                }
            }, "T2");
            
            t1Failed = false;
            t2Failed = false;
            t1.start();
            Thread.sleep(100);
            t2.start();
            t1.join();
            t2.join();
            assertTrue(!t1.isAlive());
            assertTrue(!t2.isAlive());
        } finally {
			db.shutdown();
        }       
    }

    void doScanAndDeleteThreads(final boolean testUnique, final boolean commit, final String k, final String loc) throws Exception {
		final BTreeDB db = new BTreeDB(false);
        final boolean testingUniqueIndex = testUnique;
        try {
            Thread t1 = new Thread(new Runnable() {
                public void run() {
                    try {
                        final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testingUniqueIndex);
                        IndexKeyFactory keyFactory = (IndexKeyFactory) db.objectFactory.getInstance(TYPE_STRINGKEYFACTORY);
                        LocationFactory locationFactory = (LocationFactory) db.objectFactory.getInstance(TYPE_ROWLOCATIONFACTORY);
                        IndexKey key = keyFactory.newIndexKey(1);
                        key.parseString(k);
                        Location location = locationFactory.newLocation();
                        location.parseString(loc);
                        Transaction trx = db.trxmgr.begin();
                        boolean okay = false;
                        try {
                            System.out.println("--> DELETING KEY [" + k + "," + loc + "]");
                            trx.acquireLock(location, LockMode.EXCLUSIVE, LockDuration.MANUAL_DURATION);
                            btree.delete(trx, key, location);
                            okay = true;
                        } finally {
                            if (okay && commit) {
                                System.out.println("--> COMMITTING DELETE OF KEY [" + k + "," + loc + "]");
                                trx.commit();
                            }
                            else {
                                System.out.println("--> ABORTING DELETE OF KEY [" + k + "," + loc + "]");
                                trx.abort();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        t1Failed = true;
                    }
                }
            }, "T1");

            Thread t2 = new Thread(new Runnable() {
                public void run() {
                    try {
                        final BTreeImpl btree = db.btreeMgr.getBTreeImpl(1, TYPE_STRINGKEYFACTORY, TYPE_ROWLOCATIONFACTORY, testingUniqueIndex);
                        IndexKeyFactory keyFactory = (IndexKeyFactory) db.objectFactory.getInstance(TYPE_STRINGKEYFACTORY);
                        LocationFactory locationFactory = (LocationFactory) db.objectFactory.getInstance(TYPE_ROWLOCATIONFACTORY);
                        IndexKey key = keyFactory.newIndexKey(1);
                        key.parseString("a1");
                        Location location = locationFactory.newLocation();
                        location.parseString("10");
                        IndexKey delkey = keyFactory.newIndexKey(1);
                        delkey.parseString(k);
                        Transaction trx = db.trxmgr.begin();
                        boolean okay = false;
                        IndexScan scan = btree.openScan(key, location, LockMode.UPDATE);
                        try {
                            System.out.println("--> SCANNING TREE");
                            while (scan.fetchNext(trx)) {
                                System.out.println("SCAN=" + scan.getCurrentKey() + "," + scan.getCurrentLocation());
                                System.out.println("Comparing " + scan.getCurrentKey() + " with " + delkey);
                                if (scan.getCurrentKey().equals(delkey)) {
                                    System.out.println("--> SCAN Sleeping for 15 seconds");
                                    Thread.sleep(15000);
                                    System.out.println("--> SCAN Sleep completed");
                                }
                                if (scan.isEof()) {
                                    break;
                                }
                            }
                        } finally {
                            scan.close();
                            if (okay && commit)
                                trx.commit();
                            else {
                                trx.abort();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        t2Failed = true;
                    }
                }
            }, "T2");
            
            t1Failed = false;
            t2Failed = false;
            t2.start();
            Thread.sleep(1000);
            t1.start();
            t1.join();
            t2.join();
            assertTrue(!t1.isAlive());
            assertTrue(!t2.isAlive());
        } finally {
			db.shutdown();
        }       
    }
    
	public void testPageSplitLeafUnique() throws Exception {
		doInitContainer();
		doLoadXml(true, "org/simpledbm/rss/impl/im/btree/data1ul.xml");
		doPageSplit(true, true);
		doValidateTree("org/simpledbm/rss/impl/im/btree/testPageSplitLeafUnique.xml");
	}

//	public void testPageSplitNonLeafUnique() throws Exception {
//		BTreeNode.TESTING_MODE = 2;
//		doInitContainer();
//		doTestXml(true, "org/simpledbm/rss/impl/im/btree/data1unl.xml");
//		doPageSplit(false, true);
//	}
	
	public void testPageSplitNonLeafUnique2() throws Exception {
		doInitContainer();
		doLoadXml(true, "org/simpledbm/rss/impl/im/btree/data1unl.xml");
		doPageSplit(false, true);
		doValidateTree("org/simpledbm/rss/impl/im/btree/testPageSplitNonLeafUnique2.xml");
	}

	public void testRestartAndMerge() throws Exception {
		doRestartAndMerge(false, true, 2, 3);
		doValidateTree("org/simpledbm/rss/impl/im/btree/testRestartAndMerge.xml");
	}

	public void testPageSplitLeafUnique2() throws Exception {
		doInitContainer();
		doLoadXml(true, "org/simpledbm/rss/impl/im/btree/data1ul.xml");
		doPageSplit(true, true);
		doValidateTree("org/simpledbm/rss/impl/im/btree/testPageSplitLeafUnique2.xml");
	}

	/*
	public void testRestartAndLink() throws Exception {
    	doLoadXml(true, "org/simpledbm/rss/impl/im/btree/data2unl.xml");
		doRestartAndLink(false, true);
	}
	*/
	
	public void testRestartLink() throws Exception {
    	doLoadXml(true, "org/simpledbm/rss/impl/im/btree/data2unl.xml");
		doRestartLink(false, true);
		doValidateTree("org/simpledbm/rss/impl/im/btree/testRestartLink.xml");
	}

	public void testRestartDelink() throws Exception {
		doRestartDelink(false, true);
		doValidateTree("org/simpledbm/rss/impl/im/btree/testRestartDelink.xml");
	}
	
	public void testRestartAndLinkAgain() throws Exception {
    	System.out.println("--> PREPARING PARENT");
    	doLoadXml(true, "org/simpledbm/rss/impl/im/btree/data2unl.xml");
		doRestartAndLink(false, true);
	}

	public void testRestartAndRedistribute() throws Exception {
		doRestartAndRedistribute(false, true);
		doValidateTree("org/simpledbm/rss/impl/im/btree/testRestartAndRedistribute.xml");
	}

	public void testRestartAndIncreaseTreeHeight() throws Exception {
		doRestartAndIncreaseTreeHeight(false, true);
		doValidateTree("org/simpledbm/rss/impl/im/btree/testRestartAndIncreaseTreeHeight.xml");
	}

	public void testRestartAndUnlink() throws Exception {
		doRestartAndUnlink(false, true, 2, 5, 3);
	}

	public void testRestartAndMergeAgain() throws Exception {
		doRestartAndMerge(false, true, 5, 3);
	}

	public void testRestartAndDecreaseTreeHeight() throws Exception {
		doRestartAndDecreaseTreeHeight(false, true, 2, 5);
		doValidateTree("org/simpledbm/rss/impl/im/btree/testRestartAndDecreaseTreeHeight.xml");
	}

	public void testSimpleInsertAbort() throws Exception {
		doInitContainer();
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data3nul.xml");
		doSingleInsert(false, false, "a", "1", "org/simpledbm/rss/impl/im/btree/testSimpleInsertAbort_1.xml", "org/simpledbm/rss/impl/im/btree/testSimpleInsertAbort_2.xml");
	}
	
	public void testSimpleInsertCommit() throws Exception {
		doInitContainer();
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data3nul.xml");
		doSingleInsert(false, true, "a", "1", "org/simpledbm/rss/impl/im/btree/testSimpleInsertAbort_1.xml", "org/simpledbm/rss/impl/im/btree/testSimpleInsertAbort_1.xml");
	}
	
	public void testInsertSplitRootAbort() throws Exception {
		doInitContainer();
		// Generate a tree with just the root node which should be full
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data4nul.xml");
		// Following should split the root node
		doSingleInsert(false, true, "da", "8", "org/simpledbm/rss/impl/im/btree/testInsertSplitRootAbort_1.xml", null);
		// Following should cause tree height increase
		doSingleInsert(false, false, "b1", "9", "org/simpledbm/rss/impl/im/btree/testInsertSplitRootAbort_2.xml", "org/simpledbm/rss/impl/im/btree/testInsertSplitRootAbort_3.xml");
	}
	
	public void testInsertSplitRootCommit() throws Exception {
		doInitContainer();
		// Generate a tree with just the root node which should be full
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data4nul.xml");
		// Following should split the root node
		doSingleInsert(false, true, "da", "8", "org/simpledbm/rss/impl/im/btree/testInsertSplitRootAbort_1.xml", "org/simpledbm/rss/impl/im/btree/testInsertSplitRootAbort_1.xml");
		// Following should cause tree height increase
		doSingleInsert(false, true, "b1", "9", "org/simpledbm/rss/impl/im/btree/testInsertSplitRootAbort_2.xml", "org/simpledbm/rss/impl/im/btree/testInsertSplitRootAbort_2.xml");
	}

	public void testInsertSplitRootAbortLogical() throws Exception {
		doInitContainer();
		// Generate a tree with just the root node which should be full
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data5nul.xml");
		// This should trigger logical undo as page will be split by
		// second insert
		doDoubleInsert(false, false, "g", "7", true, "da", "8",
				"org/simpledbm/rss/impl/im/btree/testInsertSplitRootAbortLogical_1.xml",
				"org/simpledbm/rss/impl/im/btree/testInsertSplitRootAbortLogical_2.xml",
				"org/simpledbm/rss/impl/im/btree/testInsertSplitRootAbortLogical_3.xml");
	}
	
	public void testInsertUnderflowFig13() throws Exception {
		doInitContainer();
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data6nul.xml");
		doSingleInsert(false, true, "a0", "1");
	}
	
	public void testInsertUnderflowFig14() throws Exception {
		doInitContainer();
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data6nul.xml");
		doSingleInsert(false, true, "c19", "1");
	}

	public void testInsertUnderflowFig5() throws Exception {
		doInitContainer();
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data7nul.xml");
		doSingleInsert(false, true, "k3", "1");
	}
	
	public void testInsertUnderflowFig19() throws Exception {
		doInitContainer();
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data6nul.xml");
		doSingleInsert(false, true, "k3", "1");
	}
	
	public void testInsertUnderflowFig15() throws Exception {
		doInitContainer();
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data6nul.xml");
		doSingleInsert(false, true, "g19", "1");
	}

	// TODO test next key loc across a page
	public void testInsertNextKeyInNextPage() throws Exception {
		doInitContainer();
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data7nul.xml");
		doSingleInsert(false, true, "b4", "1");
	}

	public void testInsertUnderflowFig17() throws Exception {
		doInitContainer();
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data8nul.xml");
		doSingleInsert(false, true, "c0", "1");
	}

	public void testDelete1() throws Exception {
		doInitContainer();
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data6nul.xml");
		doSingleDelete(false, true, "a1", "10");
	}
	
	public void testDeleteInsert1() throws Exception {
		doInitContainer();
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data6nul.xml");
		doDeleteInsertThreads(false, true, "a1", "10");
	}
	
    public void testDeleteInsert2() throws Exception {
		doInitContainer();
		doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data6nul.xml");
		doDeleteInsertThreads(false, false, "a1", "10");
	}
	
    public void testScan1() throws Exception {
        doInitContainer();
        doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data6nul.xml");
        doScanTree(false, false, "a1", "10");
    }

	public void testScan2() throws Exception {
        doInitContainer();
        doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data6nul.xml");
        doScanAndDelete(false, false, "a1", "10");
        doScanTree(false, false, "a1", "10");
        doDeleteAndScanThreads(false, true, "f3", "63");
    }

    public void testScan3() throws Exception {
        doInitContainer();
        doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data6nul.xml");
        doDeleteAndScanThreads(false, false, "f3", "63");
    }
	
    public void testScan4() throws Exception {
        doInitContainer();
        doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data6nul.xml");
        doScanAndDeleteThreads(false, false, "f3", "63");
    }

	public void testScanDeleteCrash() throws Exception {
        doInitContainer();
        doLoadXml(false, "org/simpledbm/rss/impl/im/btree/data6nul.xml");
        doScanAndDelete(false, false, "a1", "10");
    }

	public void testScanAfterCrash() throws Exception {
        doScanTree(false, false, "a1", "10");
    }
	
    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(new TestBTreeManager("testPageSplitLeafUnique"));
        suite.addTest(new TestBTreeManager("testPageSplitNonLeafUnique2"));
        suite.addTest(new TestBTreeManager("testRestartAndMerge"));
        suite.addTest(new TestBTreeManager("testPageSplitLeafUnique2"));
//        suite.addTest(new TestBTreeManager("testRestartAndLink"));
        suite.addTest(new TestBTreeManager("testRestartLink"));
        suite.addTest(new TestBTreeManager("testRestartDelink"));
        suite.addTest(new TestBTreeManager("testRestartAndLinkAgain"));
        suite.addTest(new TestBTreeManager("testRestartAndRedistribute"));
        suite.addTest(new TestBTreeManager("testRestartAndIncreaseTreeHeight"));
        suite.addTest(new TestBTreeManager("testRestartAndUnlink"));
        suite.addTest(new TestBTreeManager("testRestartAndMergeAgain"));
        suite.addTest(new TestBTreeManager("testRestartAndDecreaseTreeHeight"));
        suite.addTest(new TestBTreeManager("testSimpleInsertAbort"));
        suite.addTest(new TestBTreeManager("testSimpleInsertCommit"));
        suite.addTest(new TestBTreeManager("testInsertSplitRootAbort"));
        suite.addTest(new TestBTreeManager("testInsertSplitRootCommit"));
        suite.addTest(new TestBTreeManager("testInsertSplitRootAbortLogical"));
        suite.addTest(new TestBTreeManager("testInsertUnderflowFig13"));
        suite.addTest(new TestBTreeManager("testInsertUnderflowFig14"));
        suite.addTest(new TestBTreeManager("testInsertUnderflowFig5"));
        suite.addTest(new TestBTreeManager("testInsertUnderflowFig19"));
        suite.addTest(new TestBTreeManager("testInsertUnderflowFig15"));
        suite.addTest(new TestBTreeManager("testInsertNextKeyInNextPage"));
        suite.addTest(new TestBTreeManager("testInsertUnderflowFig17"));
        suite.addTest(new TestBTreeManager("testDelete1"));
        suite.addTest(new TestBTreeManager("testDeleteInsert1"));
        suite.addTest(new TestBTreeManager("testDeleteInsert2"));
        suite.addTest(new TestBTreeManager("testScan1"));
        suite.addTest(new TestBTreeManager("testScan2"));
        suite.addTest(new TestBTreeManager("testScan3"));
        suite.addTest(new TestBTreeManager("testScan4"));
        suite.addTest(new TestBTreeManager("testScanDeleteCrash", true));
        suite.addTest(new TestBTreeManager("testScanAfterCrash", true));
        return suite;
    }

    public static class BTreeDB {
        final LogFactoryImpl logFactory;
        final ObjectRegistry objectFactory;
        final StorageContainerFactory storageFactory;
        final StorageManager storageManager;
        final LatchFactory latchFactory;
        final PageFactory pageFactory;
        final SlottedPageManager spmgr;
        final LockMgrFactory lockmgrFactory;
        final LockManager lockmgr;
        final LogManager logmgr;
        final BufferManager bufmgr;
        final LoggableFactory loggableFactory;
        final TransactionalModuleRegistry moduleRegistry;
		final TransactionManager trxmgr;
        final FreeSpaceManager spacemgr;
        final BTreeIndexManagerImpl btreeMgr;

        public BTreeDB(boolean create) throws Exception {

    		Properties properties = new Properties();
    		properties.setProperty("log.ctl.1", "ctl.a");
    		properties.setProperty("log.ctl.2", "ctl.b");
    		properties.setProperty("log.groups.1.path", ".");
    		properties.setProperty("log.archive.path", ".");
    		properties.setProperty("log.group.files", "3");
    		properties.setProperty("log.file.size", "16384");
    		properties.setProperty("log.buffer.size", "16384");
    		properties.setProperty("log.buffer.limit", "4");
    		properties.setProperty("log.flush.interval", "5");
    		properties.setProperty("storage.basePath", "testdata/TestBTreeManager");

    		logFactory = new LogFactoryImpl();
        	if (create) {
        		logFactory.createLog(properties);
        	}
			objectFactory = new ObjectRegistryImpl();
			storageFactory = new FileStorageContainerFactory(properties);
			storageManager = new StorageManagerImpl();
			latchFactory = new LatchFactoryImpl();
			pageFactory = new PageFactoryImpl(objectFactory, storageManager, latchFactory);
			spmgr = new SlottedPageManagerImpl(objectFactory);
			lockmgrFactory = new LockManagerFactoryImpl();
			lockmgr = lockmgrFactory.create(null);
			logmgr = logFactory.getLog(properties);
			bufmgr = new BufferManagerImpl(logmgr, pageFactory, 5, 11);
			loggableFactory = new LoggableFactoryImpl(objectFactory);
			moduleRegistry = new TransactionalModuleRegistryImpl();
			trxmgr = new TransactionManagerImpl(logmgr, storageFactory, storageManager, bufmgr, lockmgr, loggableFactory, latchFactory, objectFactory, moduleRegistry);
			spacemgr = new FreeSpaceManagerImpl(objectFactory, pageFactory, logmgr, bufmgr, storageManager, storageFactory, loggableFactory, trxmgr, moduleRegistry);
			btreeMgr = new BTreeIndexManagerImpl(objectFactory, loggableFactory, spacemgr, bufmgr, spmgr, moduleRegistry);

	    	objectFactory.register(TYPE_STRINGKEYFACTORY, StringKeyFactory.class.getName());
			objectFactory.register(TYPE_ROWLOCATIONFACTORY, RowLocationFactory.class.getName());

			logmgr.start();
			bufmgr.start();

			if (create) {
				StorageContainer sc = storageFactory.create("dual");
				storageManager.register(0, sc);
				Page page = pageFactory.getInstance(pageFactory
						.getRawPageType(), new PageId(0, 0));
				pageFactory.store(page);
			}
	    	trxmgr.start();
        }
        
        public void shutdown() {
        	trxmgr.shutdown();
        	bufmgr.shutdown();
			logmgr.shutdown();
			storageManager.shutdown();
        }
    }
    
}