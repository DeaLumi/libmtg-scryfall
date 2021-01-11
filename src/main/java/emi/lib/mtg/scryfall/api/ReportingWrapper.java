package emi.lib.mtg.scryfall.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.DoubleConsumer;
import java.util.function.LongConsumer;

class ReportingWrapper extends InputStream {
	private final InputStream underlying;
	private final LongConsumer report;
	private volatile long read;
	private long mark;

	public ReportingWrapper(InputStream underlying, LongConsumer report) {
		this.underlying = underlying;
		this.report = report;
		this.read = 0;
		this.mark = -1;
	}

	private int move(int delta) {
		return (int) move((long) delta);
	}

	private long move(long delta) {
		read += delta;
		report.accept(read);
		return delta;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return move(underlying.read(b));
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return move(underlying.read(b, off, len));
	}

	@Override
	public long skip(long n) throws IOException {
		System.err.println("SKIP");
		return move(underlying.skip(n));
	}

	@Override
	public int available() throws IOException {
		return underlying.available();
	}

	@Override
	public void close() throws IOException {
		underlying.close();
	}

	@Override
	public synchronized void mark(int readlimit) {
		System.err.println("MARK");
		if (!markSupported()) return;

		underlying.mark(readlimit);
		mark = read;
	}

	@Override
	public synchronized void reset() throws IOException {
		System.err.println("RESET");
		if (!markSupported()) return;

		underlying.reset();
		move(mark - read);
	}

	@Override
	public boolean markSupported() {
		return underlying.markSupported();
	}

	@Override
	public int read() throws IOException {
		System.err.println("READ");
		int x = underlying.read();
		if (x != -1) move(1);
		return x;
	}
}
