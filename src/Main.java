import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Main {

    static final OutputStream OUT =
            new BufferedOutputStream(
                    new FileOutputStream(FileDescriptor.out), 1 << 16);

    static class Differ {
        final int[] A, B;
        final boolean[] del, ins;
        final int[] vf, vb;
        final int off;

        int sx0, sy0, sx1, sy1;

        Differ(int[] A, int[] B) {
            this.A = A;
            this.B = B;

            del = new boolean[A.length];
            ins = new boolean[B.length];

            off = A.length + B.length + 2;
            vf = new int[2 * off + 2];
            vb = new int[2 * off + 2];
        }

        void run() {
            diff(0, A.length, 0, B.length);
        }

        void diff(int a0, int a1, int b0, int b1) {

            // Remove common prefix
            while (a0 < a1 && b0 < b1 && A[a0] == B[b0]) {
                a0++;
                b0++;
            }

            // Remove common suffix
            while (a0 < a1 && b0 < b1 && A[a1 - 1] == B[b1 - 1]) {
                a1--;
                b1--;
            }

            if (a0 == a1) {
                for (int j = b0; j < b1; j++) {
                    ins[j] = true;
                }
                return;
            }

            if (b0 == b1) {
                for (int i = a0; i < a1; i++) {
                    del[i] = true;
                }
                return;
            }

            middle(a0, a1, b0, b1);

            int lx = sx0, ly = sy0;
            int rx = sx1, ry = sy1;

            diff(a0, a0 + lx, b0, b0 + ly);
            diff(a0 + rx, a1, b0 + ry, b1);
        }

        void middle(int a0, int a1, int b0, int b1) {
            int N = a1 - a0;
            int M = b1 - b0;

            int delta = N - M;
            boolean odd = (delta & 1) != 0;
            int maxD = (N + M + 1) / 2;

            vf[off + 1] = 0;
            vb[off + 1] = 0;

            for (int d = 0; d <= maxD; d++) {

                // Forward search
                for (int k = -d; k <= d; k += 2) {
                    int x;

                    if (k == -d
                            || (k != d
                            && vf[off + k - 1] < vf[off + k + 1])) {
                        x = vf[off + k + 1];
                    } else {
                        x = vf[off + k - 1] + 1;
                    }

                    int y = x - k;
                    int x0 = x, y0 = y;

                    while (x < N
                            && y < M
                            && A[a0 + x] == B[b0 + y]) {
                        x++;
                        y++;
                    }

                    vf[off + k] = x;

                    if (odd
                            && k >= delta - (d - 1)
                            && k <= delta + (d - 1)) {

                        if (x + vb[off + delta - k] >= N) {
                            sx0 = x0;
                            sy0 = y0;
                            sx1 = x;
                            sy1 = y;
                            return;
                        }
                    }
                }

                // Backward search
                for (int k = -d; k <= d; k += 2) {
                    int x;

                    if (k == -d
                            || (k != d
                            && vb[off + k - 1] < vb[off + k + 1])) {
                        x = vb[off + k + 1];
                    } else {
                        x = vb[off + k - 1] + 1;
                    }

                    int y = x - k;
                    int x0 = x, y0 = y;

                    while (x < N
                            && y < M
                            && A[a0 + N - 1 - x]
                            == B[b0 + M - 1 - y]) {
                        x++;
                        y++;
                    }

                    vb[off + k] = x;

                    if (!odd
                            && k >= delta - d
                            && k <= delta + d) {

                        if (x + vf[off + delta - k] >= N) {
                            sx0 = N - x;
                            sy0 = M - y;
                            sx1 = N - x0;
                            sy1 = M - y0;
                            return;
                        }
                    }
                }
            }

            throw new IllegalStateException("middle snake not found");
        }
    }

    public static void main(String[] args) {

        if (args.length < 3) {
            System.err.println("Usage: <lines|highlight> <fileA> <fileB>");
            System.exit(2);
        }

        byte[] dataA;
        byte[] dataB;

        try {
            dataA = Files.readAllBytes(Path.of(args[1]));
            dataB = Files.readAllBytes(Path.of(args[2]));
        } catch (IOException | InvalidPathException | SecurityException e) {
            System.err.println("Error reading input: " + e.getMessage());
            System.exit(2);
            return;
        }

        int[] sa = lineStarts(dataA);
        int[] sb = lineStarts(dataB);

        int na = sa.length - 1;
        int nb = sb.length - 1;

        Map<ByteBuffer, Integer> ids = new HashMap<>();

        int[] idsA = toIds(dataA, sa, ids);
        int[] idsB = toIds(dataB, sb, ids);

        ids = null;

        Differ df = new Differ(idsA, idsB);
        df.run();

        boolean highlight = "highlight".equals(args[0]);

        try {
            int i = 0;
            int j = 0;

            while (i < na || j < nb) {

                if (i < na && j < nb
                        && !df.del[i]
                        && !df.ins[j]) {

                    writeLine(' ', dataA, sa[i], sa[i + 1]);
                    i++;
                    j++;
                    continue;
                }

                // Find one complete change block
                int i0 = i;
                int j0 = j;

                while (i < na && df.del[i]) {
                    i++;
                }

                while (j < nb && df.ins[j]) {
                    j++;
                }

                int delCount = i - i0;
                int insCount = j - j0;

                // Delete lines must come first
                for (int k = 0; k < delCount; k++) {
                    int p = i0 + k;

                    writeLine('-', dataA, sa[p], sa[p + 1]);
                }

                // Then insert lines
                for (int k = 0; k < insCount; k++) {
                    int q = j0 + k;

                    writeLine('+', dataB, sb[q], sb[q + 1]);

                    // Only paired lines get a ? line
                    if (highlight && k < delCount) {
                        int p = i0 + k;

                        highlightPair(
                                dataA, sa[p], sa[p + 1],
                                dataB, sb[q], sb[q + 1]);
                    }
                }
            }

            OUT.flush();

        } catch (IOException e) {
            System.err.println("Error writing output: " + e.getMessage());
            System.exit(2);
        }
    }

    static int[] lineStarts(byte[] data) {
        if (data.length == 0) {
            return new int[]{0};
        }

        int lineCount = 0;

        for (byte b : data) {
            if (b == '\n') {
                lineCount++;
            }
        }

        if (data[data.length - 1] != '\n') {
            lineCount++;
        }

        int[] s = new int[lineCount + 1];
        int c = 0;

        s[0] = 0;

        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                s[++c] = i + 1;
            }
        }

        if (data[data.length - 1] != '\n') {
            s[lineCount] = data.length;
        }

        return s;
    }

    static int[] toIds(
            byte[] data,
            int[] s,
            Map<ByteBuffer, Integer> ids) {

        int n = s.length - 1;
        int[] r = new int[n];

        for (int i = 0; i < n; i++) {
            int start = s[i];
            int end = s[i + 1];

            if (end > start && data[end - 1] == '\n') {
                end--;
            }

            ByteBuffer key =
                    ByteBuffer.wrap(data, start, end - start);

            Integer id = ids.get(key);

            if (id == null) {
                id = ids.size();
                ids.put(key, id);
            }

            r[i] = id;
        }

        return r;
    }

    static void writeLine(
            char prefix,
            byte[] data,
            int start,
            int end) throws IOException {

        OUT.write(prefix);

        int contentEnd = end;

        if (contentEnd > start && data[contentEnd - 1] == '\n') {
            contentEnd--;
        }

        OUT.write(data, start, contentEnd - start);
        OUT.write('\n');
    }

    static void highlightPair(
            byte[] da,
            int a0,
            int a1,
            byte[] db,
            int b0,
            int b1) throws IOException {

        int oldEnd = a1;
        if (oldEnd > a0 && da[oldEnd - 1] == '\n') {
            oldEnd--;
        }

        int newEnd = b1;
        if (newEnd > b0 && db[newEnd - 1] == '\n') {
            newEnd--;
        }

        String oldText =
                new String(
                        da,
                        a0,
                        oldEnd - a0,
                        StandardCharsets.UTF_8);

        String newText =
                new String(
                        db,
                        b0,
                        newEnd - b0,
                        StandardCharsets.UTF_8);

        int[] oldCodePoints = oldText.codePoints().toArray();
        int[] newCodePoints = newText.codePoints().toArray();

        Differ df = new Differ(oldCodePoints, newCodePoints);
        df.run();

        StringBuilder sb = new StringBuilder("? ");

        appendRanges(sb, df.del);

        sb.append(" | ");

        appendRanges(sb, df.ins);

        sb.append('\n');

        OUT.write(
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    static void appendRanges(
            StringBuilder sb,
            boolean[] marks) {

        boolean any = false;
        int i = 0;

        while (i < marks.length) {

            if (!marks[i]) {
                i++;
                continue;
            }

            int start = i;

            while (i < marks.length && marks[i]) {
                i++;
            }

            if (any) {
                sb.append(',');
            }

            sb.append(start)
                    .append('-')
                    .append(i);

            any = true;
        }

        if (!any) {
            sb.append('.');
        }
    }
}