/**
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Contributor(s):
 *
 * The Original Software is SimpleDBM (www.simpledbm.org).
 * The Initial Developer of the Original Software is Dibyendu Majumdar.
 *
 * Portions Copyright 2005-2014 Dibyendu Majumdar. All Rights Reserved.
 *
 * The contents of this file are subject to the terms of the
 * Apache License Version 2 (the "APL"). You may not use this
 * file except in compliance with the License. A copy of the
 * APL may be obtained from:
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the APL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the APL, the GPL or the LGPL.
 *
 * Copies of GPL and LGPL may be obtained from:
 * http://www.gnu.org/licenses/license-list.html
 */
package org.simpledbm.rss.api.tx;

import org.simpledbm.rss.api.wal.Lsn;

/**
 * Interface definition for Compensation log records. Compensation log records
 * are used to log Undo operations. For example, if a transaction changed A to B
 * and then aborted, then the change from B to A is recorded as a Compensation
 * log record. It is redo only, which means that a Compensation log record will
 * never be undone.
 * <p>
 * In Compensation log records, the undoNextLsn is set to the lsn of the
 * predecessor of the log record being compensated.
 * 
 * @author Dibyendu Majumdar
 * @since 23-Aug-2005
 */
public interface Compensation extends Redoable {

    /**
     * Sets pointer to the next record that should be undone.
     */
    public Lsn getUndoNextLsn();

    /**
     * Gets pointer to the next record that should be undone.
     */
    public void setUndoNextLsn(Lsn undoNextLsn);

}
