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
package org.simpledbm.common.tools.diagnostics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;

import org.simpledbm.common.api.exception.SimpleDBMException;
import org.simpledbm.common.util.logging.Logger;
import org.simpledbm.common.util.mcat.Message;
import org.simpledbm.common.util.mcat.MessageInstance;
import org.simpledbm.common.util.mcat.MessageType;

/**
 * An efficient thread safe but lock free mechanism to generate trace messages.
 * Uses a ring buffer. Messages are stored in memory so that there is very
 * little performance impact. Each message is tagged with the thread id, and a
 * sequence number. The sequence number wraps around when it reaches 2147483646.
 * <p>
 * Trace messages can be dumped to the log by invoking dump(). The messages are
 * only output if a logger named org.simpledbm.rss.trace has level set to DEBUG.
 * <p>
 * The design of Trace mechanism was inspired by the article in <cite>DDJ April
 * 23, 2007, Multi-threaded Debugging Techniques by Shameem Akhter and Jason
 * Roberts</cite>. This article is an excerpt from the book <cite>Multi-Core
 * Programming</cite> by the same authors.
 * 
 * @author dibyendu majumdar
 * @since 26 July 2008
 */
public class Trace implements TraceBuffer.TraceVisitor {

    final Logger log;

    /**
     * Default message format.
     */
    static String defaultMessage;

    final String[] messages;

    final TraceBuffer traceBuffer;

    /*
     * For performance reasons, we only allow numeric arguments to be supplied
     * as trace message identifiers and arguments. This avoids expensive object
     * allocations. The trace message is inserted at the next position in the
     * traceBuffer array, the next pointer wraps to the beginning of the array
     * when it reaches the end.
     */

    public Trace(TraceBuffer traceBuffer, Logger log, String messageFile) {
        this.log = log;
        this.messages = loadMessages(messageFile);
        this.traceBuffer = traceBuffer;
    }

    private InputStream getResourceAsStream(String name) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream is = null;
        is = cl.getResourceAsStream(name);
        if (is == null) {
            throw new SimpleDBMException(new MessageInstance(new Message('C',
                    'U', MessageType.ERROR, 2, "Unable to load resource {0}"),
                    name));
        }
        return is;
    }

    private String[] loadMessages(String resourceName) {
        InputStream is = getResourceAsStream(resourceName);
        BufferedReader reader = null;
        ArrayList<String> messageList = new ArrayList<String>();
        try {
            reader = new BufferedReader(
                    new InputStreamReader(is));
            String line = reader.readLine();
            while (line != null) {
                messageList.add(line);
                line = reader.readLine();
            }
        } catch (IOException e) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
            try {
                is.close();
            } catch (IOException e) {
            }
        }
        return messageList.toArray(new String[0]);
    }

    public void dump() {
        if (!log.isDebugEnabled()) {
            return;
        }
        traceBuffer.visitall(this);
    }

    /**
     * Dumps the contents of the trace buffer to the logger named
     * org.simpledbm.rss.trace. Messages are output only if this logger has a
     * level of DEBUG or higher.
     */
    public void visit(long tid, int seq, int msg, int d1, int d2, int d3, int d4) {
        if (msg < 0 || msg > messages.length) {
        } else {
            String s = messages[msg];
            log.debug(this.getClass(), "dump", MessageFormat.format(s,
                    tid, seq, d1, d2, d3, d4));
        }
    }
}
