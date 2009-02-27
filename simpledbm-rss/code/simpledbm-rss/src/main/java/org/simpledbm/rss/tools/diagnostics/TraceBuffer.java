package org.simpledbm.rss.tools.diagnostics;

import org.simpledbm.rss.util.WrappingSequencer;

public class TraceBuffer {
	
	public interface TraceVisitor {
		void visit(long tid, int seq, int msg, int d1, int d2, int d3, int d4);
	}
	
	/**
	 * A atomic sequence number. 
	 */
	final WrappingSequencer seq = new WrappingSequencer(
			Integer.MAX_VALUE);

	/**
	 * Default size of the trace buffer.
	 */
	final int SIZE = 5000;

	/**
	 * The trace buffer array where events are stored.
	 */
	final TraceElement[] traceBuffer;
	
	/*
	 * For performance reasons, we only allow numeric arguments to be supplied
	 * as trace message identifiers and arguments. This avoids expensive object allocations.
	 * The trace message is inserted at the next position in the traceBuffer
	 * array, the next pointer wraps to the beginning of the array when it reaches 
	 * the end.
	 */

	public TraceBuffer() {
		traceBuffer = new TraceElement[SIZE];
		for (int i = 0; i < SIZE; i++) {
			traceBuffer[i] = new TraceElement();
		}
	}

	public void event(int msg) {
		int next = seq.getNext();
		int offset = next % traceBuffer.length;
		traceBuffer[offset].init(next, msg);
	}

	public void event(int msg, int d1) {
		int next = seq.getNext();
		int offset = next % traceBuffer.length;
		traceBuffer[offset].init(next, msg, d1);
	}

	public void event(int msg, int d1, int d2) {
		int next = seq.getNext();
		int offset = next % traceBuffer.length;
		traceBuffer[offset].init(next, msg, d1, d2);
	}

	public void event(int msg, int d1, int d2, int d3) {
		int next = seq.getNext();
		int offset = next % traceBuffer.length;
		traceBuffer[offset].init(next, msg, d1, d2, d3);
	}

	public void event(int msg, int d1, int d2, int d3, int d4) {
		int next = seq.getNext();
		int offset = next % traceBuffer.length;
		traceBuffer[offset].init(next, msg, d1, d2, d3, d4);
	}
	
	/**
	 * Dumps the contents of the trace buffer to the logger named
	 * org.simpledbm.rss.trace. Messages are output only if this logger has
	 * a level of DEBUG or higher.
	 */
	public void visitall(TraceVisitor visitor) {
		/*
		 * As the trace buffer can change while we are dumping its contents,
		 * we need some way to decide which messages to output. At present
		 * we simply check the current sequence number, and dump all 
		 * messages with a sequence number less than the one we noted. This
		 * has a problem though - if the sequence number wraps around, and
		 * dump() is invoked, then any messages that have a sequence number
		 * greater than the noted sequence will not get output. To work around
		 * this issue, we also dump messages that have a sequence number
		 * that is much greater than the current max - to be exact, if the difference
		 * is greater than Integer.MAX_VALUE/2.
		 */
//		int max = seq.get();
//		for (int i = 0; i < traceBuffer.length; i++) {
//			TraceElement e = traceBuffer[i];
//			if ((e.seq < max || (e.seq > max && ((e.seq - max) > Integer.MAX_VALUE/2))) && e.msg != -1) {
//				if (e.msg < 0 || e.msg > messages.length) {
//					log.debug(Trace.class.getName(), "dump", MessageFormat.format(defaultMessage,
//							e.tid, e.seq, e.d1, e.d2, e.d3, e.d4, e.msg));
//				}
//				else {
//					String msg = messages[e.msg];
//					log.debug(Trace.class.getName(), "dump", MessageFormat.format(msg,
//							e.tid, e.seq, e.d1, e.d2, e.d3, e.d4));
//				}
//			}
//		}
		for (int i = 0; i < traceBuffer.length; i++) {
			TraceElement e = traceBuffer[i];
			visitor.visit(e.tid, e.seq, e.msg, e.d1, e.d2, e.d3, e.d4);
		}
	}
	

	static final class TraceElement {
		volatile long tid;
		volatile int seq;
		volatile int msg = -1;
		volatile int d1;
		volatile int d2;
		volatile int d3;
		volatile int d4;

		void init(int seq, int msg) {
			this.seq = seq;
			this.tid = Thread.currentThread().getId();
			this.msg = msg;
			this.d1 = this.d2 = this.d3 = this.d4 = 0;
		}

		void init(int seq, int msg, int d1) {
			this.seq = seq;
			this.tid = Thread.currentThread().getId();
			this.msg = msg;
			this.d1 = d1;
			this.d2 = this.d3 = this.d4 = 0;
		}

		void init(int seq, int msg, int d1, int d2) {
			this.seq = seq;
			this.tid = Thread.currentThread().getId();
			this.msg = msg;
			this.d1 = d1;
			this.d2 = d2;
			this.d3 = this.d4 = 0;
		}

		void init(int seq, int msg, int d1, int d2, int d3) {
			this.seq = seq;
			this.tid = Thread.currentThread().getId();
			this.msg = msg;
			this.d1 = d1;
			this.d2 = d2;
			this.d3 = d3;
			this.d4 = 0;
		}

		void init(int seq, int msg, int d1, int d2, int d3, int d4) {
			this.seq = seq;
			this.tid = Thread.currentThread().getId();
			this.msg = msg;
			this.d1 = d1;
			this.d2 = d2;
			this.d3 = d3;
			this.d4 = d4;
		}
	}
}
