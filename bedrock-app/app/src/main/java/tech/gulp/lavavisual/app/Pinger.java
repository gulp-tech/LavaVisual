package tech.gulp.lavavisual.app;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;

/** Ping of a server address: how long a TCP connection takes, median of three tries. */
public final class Pinger {
    private Pinger() { }

    /** host, host:port or empty. Bedrock servers answer on 19132, Java ones on 25565; both are tried. */
    public static int[] parse(String address) {
        String value = address == null ? "" : address.trim();
        if (value.isEmpty()) return null;
        int colon = value.lastIndexOf(':');
        if (colon > 0) {
            try {
                return new int[]{Integer.parseInt(value.substring(colon + 1).trim())};
            } catch (NumberFormatException ignored) { }
        }
        return new int[]{25565, 19132};
    }

    public static String host(String address) {
        String value = address == null ? "" : address.trim();
        int colon = value.lastIndexOf(':');
        return colon > 0 && value.indexOf('.') < colon ? value.substring(0, colon) : value;
    }

    /** Milliseconds, or -1 when the address does not answer. */
    public static int measure(String address, int timeoutMs) {
        int[] ports = parse(address);
        if (ports == null) return -1;
        String host = host(address);
        for (int port : ports) {
            int[] tries = new int[3];
            int good = 0;
            for (int i = 0; i < 3; i++) {
                long start = System.nanoTime();
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), timeoutMs);
                    tries[good++] = (int) ((System.nanoTime() - start) / 1_000_000);
                } catch (Exception ignored) { }
            }
            if (good > 0) {
                int[] done = Arrays.copyOf(tries, good);
                Arrays.sort(done);
                return done[good / 2];
            }
        }
        return -1;
    }
}
