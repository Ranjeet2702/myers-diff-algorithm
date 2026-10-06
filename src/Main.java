import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Main {

    static class Op {
        char t;
        int a, b;
        Op(char t, int a, int b) {
            this.t = t;
            this.a = a;
            this.b = b;
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) return;

        try {
            byte[] x = Files.readAllBytes(Path.of(args[1]));
            byte[] y = Files.readAllBytes(Path.of(args[2]));

            List<byte[]> A = lines(x), B = lines(y);
            List<Op> ops = diff(A, B);

            if (args[0].equals("highlight"))
                highlight(A, B, ops);
            else
                print(A, B, ops);

        } catch (IOException e) {
            System.err.println("Error reading files: " + e.getMessage());
            System.exit(2);
        }
    }

    static List<byte[]> lines(byte[] x) {
        List<byte[]> r = new ArrayList<>();
        int s = 0;

        for (int i = 0; i < x.length; i++) {
            if (x[i] == '\n') {
                r.add(Arrays.copyOfRange(x, s, i));
                s = i + 1;
            }
        }

        if (s < x.length)
            r.add(Arrays.copyOfRange(x, s, x.length));

        return r;
    }

    static List<Op> diff(List<byte[]> A, List<byte[]> B) {
        int n = A.size(), m = B.size(), max = n + m;
        List<Op> r = new ArrayList<>();

        if (max == 0) return r;

        int off = max;
        int[] v = new int[2 * max + 1];
        v[off + 1] = 0;

        List<int[]> trace = new ArrayList<>();
        int end = 0;

        outer:
        for (int d = 0; d <= max; d++) {

            for (int k = -d; k <= d; k += 2) {
                int i;

                if (k == -d ||
                    (k != d && v[off + k - 1] < v[off + k + 1]))
                    i = v[off + k + 1];
                else
                    i = v[off + k - 1] + 1;

                int j = i - k;

                while (i < n && j < m &&
                       Arrays.equals(A.get(i), B.get(j))) {
                    i++;
                    j++;
                }

                v[off + k] = i;

                if (i >= n && j >= m) {
                    trace.add(save(v, off, d));
                    end = d;
                    break outer;
                }
            }

            trace.add(save(v, off, d));
        }

        int i = n, j = m;

        for (int d = end; d > 0; d--) {
            int k = i - j;
            int[] p = trace.get(d - 1);

            int pk;

            if (k == -d ||
                (k != d && get(p, d - 1, k - 1) <
                           get(p, d - 1, k + 1)))
                pk = k + 1;
            else
                pk = k - 1;

            int pi = get(p, d - 1, pk);
            int pj = pi - pk;

            while (i > pi && j > pj) {
                r.add(new Op(' ', i - 1, j - 1));
                i--;
                j--;
            }

            if (i == pi) {
                r.add(new Op('+', i, j - 1));
                j--;
            } else {
                r.add(new Op('-', i - 1, j));
                i--;
            }
        }

        while (i > 0 && j > 0) {
            r.add(new Op(' ', i - 1, j - 1));
            i--;
            j--;
        }

        Collections.reverse(r);
        return r;
    }

    static int[] save(int[] v, int off, int d) {
        int[] r = new int[d + 1];
        int p = 0;

        for (int k = -d; k <= d; k += 2)
            r[p++] = v[off + k];

        return r;
    }

    static int get(int[] v, int d, int k) {
        return v[(k + d) / 2];
    }

    static void print(List<byte[]> A, List<byte[]> B, List<Op> ops) {
        try {
            for (Op o : ops) {
                System.out.write(o.t);
                System.out.write(o.t == '+' ? B.get(o.b) : A.get(o.a));
                System.out.write('\n');
            }
            System.out.flush();
        } catch (IOException e) {
            System.err.println("Error writing output: " + e.getMessage());
            System.exit(2);
        }
    }

    static void highlight(
            List<byte[]> A,
            List<byte[]> B,
            List<Op> ops) {

        int p = 0;

        while (p < ops.size()) {
            Op o = ops.get(p);

            if (o.t == ' ') {
                write(" ", A.get(o.a));
                p++;
                continue;
            }

            List<Op> del = new ArrayList<>();
            List<Op> add = new ArrayList<>();

            while (p < ops.size() && ops.get(p).t != ' ') {
                o = ops.get(p++);
                if (o.t == '-') del.add(o);
                else add.add(o);
            }

            for (Op d : del)
                write("-", A.get(d.a));

            int pairs = Math.min(del.size(), add.size());

            for (int i = 0; i < add.size(); i++) {
                Op a = add.get(i);
                write("+", B.get(a.b));

                if (i < pairs) {
                    Op d = del.get(i);

                    String old =
                        new String(A.get(d.a), StandardCharsets.UTF_8);
                    String neu =
                        new String(B.get(a.b), StandardCharsets.UTF_8);

                    ranges(old, neu);
                }
            }
        }
    }

    static List<Op> codeDiff(int[] A, int[] B) {
        int n = A.length, m = B.length, max = n + m;
        List<Op> r = new ArrayList<>();

        if (max == 0) return r;

        int off = max;
        int[] v = new int[2 * max + 1];
        v[off + 1] = 0;

        List<int[]> trace = new ArrayList<>();
        int end = 0;

        outer:
        for (int d = 0; d <= max; d++) {

            for (int k = -d; k <= d; k += 2) {
                int i;

                if (k == -d ||
                    (k != d && v[off + k - 1] < v[off + k + 1]))
                    i = v[off + k + 1];
                else
                    i = v[off + k - 1] + 1;

                int j = i - k;

                while (i < n && j < m && A[i] == B[j]) {
                    i++;
                    j++;
                }

                v[off + k] = i;

                if (i >= n && j >= m) {
                    trace.add(save(v, off, d));
                    end = d;
                    break outer;
                }
            }

            trace.add(save(v, off, d));
        }

        int i = n, j = m;

        for (int d = end; d > 0; d--) {
            int k = i - j;
            int[] p = trace.get(d - 1);
            int pk;

            if (k == -d ||
                (k != d && get(p, d - 1, k - 1) <
                           get(p, d - 1, k + 1)))
                pk = k + 1;
            else
                pk = k - 1;

            int pi = get(p, d - 1, pk);
            int pj = pi - pk;

            while (i > pi && j > pj) {
                r.add(new Op(' ', i - 1, j - 1));
                i--;
                j--;
            }

            if (i == pi) {
                r.add(new Op('+', i, j - 1));
                j--;
            } else {
                r.add(new Op('-', i - 1, j));
                i--;
            }
        }

        while (i > 0 && j > 0) {
            r.add(new Op(' ', i - 1, j - 1));
            i--;
            j--;
        }

        Collections.reverse(r);
        return r;
    }

    static void ranges(String oldText, String newText) {
        int[] A = oldText.codePoints().toArray();
        int[] B = newText.codePoints().toArray();

        List<Op> ops = codeDiff(A, B);
        List<int[]> oldR = new ArrayList<>();
        List<int[]> newR = new ArrayList<>();

        int a = 0, b = 0;

        for (Op o : ops) {
            if (o.t == ' ') {
                a++;
                b++;
            } else if (o.t == '-') {
                addRange(oldR, a, a + 1);
                a++;
            } else {
                addRange(newR, b, b + 1);
                b++;
            }
        }

        try {
            String s = "? " + format(oldR) + " | " + format(newR);
            System.out.write(s.getBytes(StandardCharsets.UTF_8));
            System.out.write('\n');
        } catch (IOException e) {
            System.err.println("Error writing highlight: " + e.getMessage());
            System.exit(2);
        }
    }

    static void addRange(List<int[]> r, int s, int e) {
        if (s >= e) return;

        if (!r.isEmpty() && s <= r.get(r.size() - 1)[1]) {
            r.get(r.size() - 1)[1] =
                Math.max(r.get(r.size() - 1)[1], e);
        } else {
            r.add(new int[]{s, e});
        }
    }

    static String format(List<int[]> r) {
        if (r.isEmpty()) return ".";

        StringBuilder s = new StringBuilder();

        for (int i = 0; i < r.size(); i++) {
            if (i > 0) s.append(",");
            s.append(r.get(i)[0])
             .append("-")
             .append(r.get(i)[1]);
        }

        return s.toString();
    }

    static void write(String prefix, byte[] line) {
        try {
            System.out.write(prefix.getBytes(StandardCharsets.UTF_8));
            System.out.write(line);
            System.out.write('\n');
        } catch (IOException e) {
            System.err.println("Error writing output: " + e.getMessage());
            System.exit(2);
        }
    }
}