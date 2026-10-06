```java
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Main {

    static class Op {
        char type;       // ' ' = keep, '+' = insert, '-' = delete
        int a, b;

        Op(char type, int a, int b) {
            this.type = type;
            this.a = a;
            this.b = b;
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) return;

        String command = args[0];

        try {
            byte[] fileA = Files.readAllBytes(Path.of(args[1]));
            byte[] fileB = Files.readAllBytes(Path.of(args[2]));

            List<byte[]> A = lines(fileA);
            List<byte[]> B = lines(fileB);

            List<Op> ops = myersLines(A, B);

            if (command.equals("highlight"))
                highlight(A, B, ops);
            else
                printDiff(A, B, ops);

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    // Split file into lines without changing the original bytes.
    static List<byte[]> lines(byte[] data) {
        List<byte[]> result = new ArrayList<>();
        int start = 0;

        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                result.add(Arrays.copyOfRange(data, start, i));
                start = i + 1;
            }
        }

        if (start < data.length)
            result.add(Arrays.copyOfRange(data, start, data.length));

        return result;
    }

    // Myers diff for lines
    static List<Op> myersLines(List<byte[]> A, List<byte[]> B) {
        int n = A.size(), m = B.size();
        int max = n + m;

        List<int[]> trace = new ArrayList<>();
        int[] v = new int[2 * max + 1];
        int off = max;
        int endD = 0;

        if (max == 0) return new ArrayList<>();

        outer:
        for (int d = 0; d <= max; d++) {
            for (int k = -d; k <= d; k += 2) {

                int i = nextI(v, off, k, d);
                int j = i - k;

                while (i < n && j < m &&
                        Arrays.equals(A.get(i), B.get(j))) {
                    i++;
                    j++;
                }

                v[off + k] = i;

                if (i >= n && j >= m) {
                    trace.add(save(v, off, d));
                    endD = d;
                    break outer;
                }
            }

            trace.add(save(v, off, d));
        }

        return backtrack(trace, endD, n, m);
    }

    static int nextI(int[] v, int off, int k, int d) {
        if (k == -d ||
                (k != d && v[off + k - 1] < v[off + k + 1])) {
            return v[off + k + 1];
        }
        return v[off + k - 1] + 1;
    }

    // Store only diagonals that are needed for backtracking.
    static int[] save(int[] v, int off, int d) {
        int[] result = new int[d + 1];

        for (int k = -d, p = 0; k <= d; k += 2)
            result[p++] = v[off + k];

        return result;
    }

    static int get(int[] slice, int d, int k) {
        return slice[(k + d) / 2];
    }

    // Backtracking for line-level diff
    static List<Op> backtrack(
            List<int[]> trace, int endD, int n, int m) {

        List<Op> result = new ArrayList<>();
        int i = n, j = m;

        for (int d = endD; d > 0; d--) {
            int k = i - j;
            int[] prev = trace.get(d - 1);

            int prevK;

            if (k == -d ||
                    (k != d &&
                     get(prev, d - 1, k - 1) <
                     get(prev, d - 1, k + 1))) {
                prevK = k + 1;
            } else {
                prevK = k - 1;
            }

            int prevI = get(prev, d - 1, prevK);
            int prevJ = prevI - prevK;

            while (i > prevI && j > prevJ) {
                result.add(new Op(' ', i - 1, j - 1));
                i--;
                j--;
            }

            if (i == prevI) {
                result.add(new Op('+', i, j - 1));
                j--;
            } else {
                result.add(new Op('-', i - 1, j));
                i--;
            }
        }

        while (i > 0 && j > 0) {
            result.add(new Op(' ', i - 1, j - 1));
            i--;
            j--;
        }

        Collections.reverse(result);
        return result;
    }

    static void printDiff(
            List<byte[]> A,
            List<byte[]> B,
            List<Op> ops) throws IOException {

        for (Op op : ops) {
            System.out.write(op.type);

            if (op.type == '+')
                System.out.write(B.get(op.b));
            else
                System.out.write(A.get(op.a));

            System.out.write('\n');
        }

        System.out.flush();
    }

    // ------------------------------------------------------------
    // Highlight mode
    // ------------------------------------------------------------

    static void highlight(
            List<byte[]> A,
            List<byte[]> B,
            List<Op> ops) {

        for (int i = 0; i < ops.size();) {

            if (ops.get(i).type == ' ') {
                writeLine(" ", A.get(ops.get(i).a));
                i++;
                continue;
            }

            List<Op> deletes = new ArrayList<>();
            List<Op> inserts = new ArrayList<>();

            while (i < ops.size() && ops.get(i).type != ' ') {
                Op op = ops.get(i++);

                if (op.type == '-')
                    deletes.add(op);
                else
                    inserts.add(op);
            }

            for (Op op : deletes)
                writeLine("-", A.get(op.a));

            for (int x = 0; x < inserts.size(); x++) {
                Op add = inserts.get(x);
                writeLine("+", B.get(add.b));

                if (x < deletes.size()) {
                    String oldText =
                            new String(
                                    A.get(deletes.get(x).a),
                                    StandardCharsets.UTF_8);

                    String newText =
                            new String(
                                    B.get(add.b),
                                    StandardCharsets.UTF_8);

                    printRanges(oldText, newText);
                }
            }
        }
    }

    static void writeLine(String prefix, byte[] line) {
        try {
            System.out.write(prefix.getBytes(StandardCharsets.UTF_8));
            System.out.write(line);
            System.out.write('\n');
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    // Myers diff on Unicode code points.
    static List<Op> myersChars(int[] A, int[] B) {
        int n = A.length, m = B.length;
        int max = n + m;

        if (max == 0) return new ArrayList<>();

        int[] v = new int[2 * max + 1];
        int off = max;
        List<int[]> trace = new ArrayList<>();
        int endD = 0;

        outer:
        for (int d = 0; d <= max; d++) {

            for (int k = -d; k <= d; k += 2) {

                int i = nextI(v, off, k, d);
                int j = i - k;

                while (i < n && j < m && A[i] == B[j]) {
                    i++;
                    j++;
                }

                v[off + k] = i;

                if (i >= n && j >= m) {
                    trace.add(save(v, off, d));
                    endD = d;
                    break outer;
                }
            }

            trace.add(save(v, off, d));
        }

        return backtrack(trace, endD, n, m);
    }

    static void printRanges(String oldText, String newText) {
        int[] A = oldText.codePoints().toArray();
        int[] B = newText.codePoints().toArray();

        List<Op> ops = myersChars(A, B);

        List<int[]> oldRanges = new ArrayList<>();
        List<int[]> newRanges = new ArrayList<>();

        int oldPos = 0, newPos = 0;

        for (Op op : ops) {

            if (op.type == ' ') {
                oldPos++;
                newPos++;
            }
            else if (op.type == '-') {
                addRange(oldRanges, oldPos++);
            }
            else {
                addRange(newRanges, newPos++);
            }
        }

        String result =
                "? " + format(oldRanges) +
                " | " + format(newRanges);

        try {
            System.out.write(result.getBytes(StandardCharsets.UTF_8));
            System.out.write('\n');
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    static void addRange(List<int[]> ranges, int pos) {
        if (!ranges.isEmpty()) {
            int[] last = ranges.get(ranges.size() - 1);

            if (pos <= last[1]) {
                last[1] = Math.max(last[1], pos + 1);
                return;
            }
        }

        ranges.add(new int[]{pos, pos + 1});
    }

    static String format(List<int[]> ranges) {
        if (ranges.isEmpty()) return ".";

        StringBuilder s = new StringBuilder();

        for (int i = 0; i < ranges.size(); i++) {
            if (i > 0) s.append(',');

            s.append(ranges.get(i)[0])
             .append('-')
             .append(ranges.get(i)[1]);
        }

        return s.toString();
    }
}
```


// import java.io.IOException;
// import java.nio.charset.StandardCharsets;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.util.ArrayList;
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.List;

// public class Main {

//     static class Operation {
//         char type;
//         int aIndex;
//         int bIndex;

//         Operation(char type, int aIndex, int bIndex) {
//             this.type = type;
//             this.aIndex = aIndex;
//             this.bIndex = bIndex;
//         }
//     }

//     public static void main(String[] args) {

//         if (args.length < 3) {
//             return;
//         }

//         String command = args[0];
//         String fileA = args[1];
//         String fileB = args[2];

//         try {

//             byte[] bytesA = Files.readAllBytes(Path.of(fileA));
//             byte[] bytesB = Files.readAllBytes(Path.of(fileB));

//             List<byte[]> A = splitLines(bytesA);
//             List<byte[]> B = splitLines(bytesB);

//             List<Operation> operations = myersDiff(A, B);

//             if (command.equals("highlight")) {
//                 printHighlight(A, B, operations);
//             } else {
//                 printDiff(A, B, operations);
//             }

//         } catch (IOException e) {

//             System.err.println("Error reading files: " + e.getMessage());
//             System.exit(2);
//         }
//     }

//     // ------------------------------------------------------------
//     // Split file into lines using raw bytes
//     // ------------------------------------------------------------

//     static List<byte[]> splitLines(byte[] data) {

//         List<byte[]> lines = new ArrayList<>();

//         int start = 0;

//         for (int i = 0; i < data.length; i++) {

//             if (data[i] == '\n') {

//                 lines.add(Arrays.copyOfRange(data, start, i));
//                 start = i + 1;
//             }
//         }

//         // Add final piece only if it is non-empty.
//         if (start < data.length) {
//             lines.add(Arrays.copyOfRange(data, start, data.length));
//         }

//         return lines;
//     }

//     // ------------------------------------------------------------
//     // Myers line-level diff
//     // Memory optimized version
//     // ------------------------------------------------------------

//     static List<Operation> myersDiff(List<byte[]> A, List<byte[]> B) {

//         int N = A.size();
//         int M = B.size();
//         int max = N + M;

//         List<Operation> result = new ArrayList<>();

//         if (max == 0) {
//             return result;
//         }

//         int offset = max;

//         int[] V = new int[2 * max + 1];

//         V[offset + 1] = 0;

//         /*
//          * IMPORTANT:
//          *
//          * Do NOT use:
//          *
//          * trace.add(V.clone());
//          *
//          * That stores the complete V array for every D
//          * and causes hidden-test memory failures.
//          *
//          * Instead, we store only the useful diagonals.
//          */
//         List<int[]> trace = new ArrayList<>();

//         int finalD = 0;

//         outer:
//         for (int D = 0; D <= max; D++) {

//             for (int k = -D; k <= D; k += 2) {

//                 int i;

//                 if (k == -D ||
//                         (k != D &&
//                                 V[offset + k - 1]
//                                         < V[offset + k + 1])) {

//                     i = V[offset + k + 1];

//                 } else {

//                     i = V[offset + k - 1] + 1;
//                 }

//                 int j = i - k;

//                 // Follow the matching diagonal.
//                 while (i < N &&
//                         j < M &&
//                         Arrays.equals(A.get(i), B.get(j))) {

//                     i++;
//                     j++;
//                 }

//                 V[offset + k] = i;

//                 // We reached the end.
//                 if (i >= N && j >= M) {

//                     trace.add(saveVSlice(V, offset, D));

//                     finalD = D;

//                     break outer;
//                 }
//             }

//             // Store only useful V values.
//             trace.add(saveVSlice(V, offset, D));
//         }

//         // --------------------------------------------------------
//         // Backtracking
//         // --------------------------------------------------------

//         int i = N;
//         int j = M;

//         for (int D = finalD; D > 0; D--) {

//             int k = i - j;

//             int[] previousV = trace.get(D - 1);

//             int previousK;

//             if (k == -D ||
//                     (k != D &&
//                             getVSlice(previousV, D - 1, k - 1)
//                                     < getVSlice(previousV, D - 1, k + 1))) {

//                 previousK = k + 1;

//             } else {

//                 previousK = k - 1;
//             }

//             int previousI =
//                     getVSlice(previousV, D - 1, previousK);

//             int previousJ = previousI - previousK;

//             // Matching diagonal = KEEP
//             while (i > previousI &&
//                     j > previousJ) {

//                 result.add(new Operation(
//                         ' ',
//                         i - 1,
//                         j - 1
//                 ));

//                 i--;
//                 j--;
//             }

//             // Edit operation
//             if (i == previousI) {

//                 // INSERT
//                 result.add(new Operation(
//                         '+',
//                         i,
//                         j - 1
//                 ));

//                 j--;

//             } else {

//                 // DELETE
//                 result.add(new Operation(
//                         '-',
//                         i - 1,
//                         j
//                 ));

//                 i--;
//             }
//         }

//         // Remaining KEEP operations.
//         while (i > 0 && j > 0) {

//             result.add(new Operation(
//                     ' ',
//                     i - 1,
//                     j - 1
//             ));

//             i--;
//             j--;
//         }

//         Collections.reverse(result);

//         return result;
//     }

//     // ------------------------------------------------------------
//     // Save only useful V diagonals
//     // ------------------------------------------------------------

//     static int[] saveVSlice(int[] V, int offset, int D) {

//         int[] slice = new int[D + 1];

//         int index = 0;

//         for (int k = -D; k <= D; k += 2) {

//             slice[index++] = V[offset + k];
//         }

//         return slice;
//     }

//     // ------------------------------------------------------------
//     // Get V value from compressed slice
//     // ------------------------------------------------------------

//     static int getVSlice(int[] slice, int D, int k) {

//         return slice[(k + D) / 2];
//     }

//     // ------------------------------------------------------------
//     // Part A output
//     // ------------------------------------------------------------

//     static void printDiff(
//             List<byte[]> A,
//             List<byte[]> B,
//             List<Operation> operations) {

//         try {

//             for (Operation op : operations) {

//                 if (op.type == ' ') {

//                     System.out.write(' ');
//                     System.out.write(A.get(op.aIndex));
//                     System.out.write('\n');

//                 } else if (op.type == '-') {

//                     System.out.write('-');
//                     System.out.write(A.get(op.aIndex));
//                     System.out.write('\n');

//                 } else {

//                     System.out.write('+');
//                     System.out.write(B.get(op.bIndex));
//                     System.out.write('\n');
//                 }
//             }

//             System.out.flush();

//         } catch (IOException e) {

//             System.err.println("Error writing output: " + e.getMessage());
//             System.exit(2);
//         }
//     }

//     // ------------------------------------------------------------
//     // Part B
//     // ------------------------------------------------------------

//     static void printHighlight(
//             List<byte[]> A,
//             List<byte[]> B,
//             List<Operation> operations) {

//         int index = 0;

//         while (index < operations.size()) {

//             Operation op = operations.get(index);

//             // KEEP
//             if (op.type == ' ') {

//                 writeUtf8Line(
//                         " ",
//                         A.get(op.aIndex)
//                 );

//                 index++;
//                 continue;
//             }

//             // ----------------------------------------------------
//             // Change block
//             // ----------------------------------------------------

//             List<Operation> deletes = new ArrayList<>();
//             List<Operation> inserts = new ArrayList<>();

//             while (index < operations.size()
//                     && operations.get(index).type != ' ') {

//                 Operation current = operations.get(index);

//                 if (current.type == '-') {
//                     deletes.add(current);
//                 } else {
//                     inserts.add(current);
//                 }

//                 index++;
//             }

//             // ----------------------------------------------------
//             // Print all deletes first
//             // ----------------------------------------------------

//             for (Operation delete : deletes) {

//                 writeUtf8Line(
//                         "-",
//                         A.get(delete.aIndex)
//                 );
//             }

//             // ----------------------------------------------------
//             // Print inserts
//             // ----------------------------------------------------

//             int pairs = Math.min(
//                     deletes.size(),
//                     inserts.size()
//             );

//             for (int i = 0; i < inserts.size(); i++) {

//                 Operation insert = inserts.get(i);

//                 writeUtf8Line(
//                         "+",
//                         B.get(insert.bIndex)
//                 );

//                 // If this insert has a corresponding delete,
//                 // print character-level changes.
//                 if (i < pairs) {

//                     Operation delete = deletes.get(i);

//                     String oldText =
//                             new String(
//                                     A.get(delete.aIndex),
//                                     StandardCharsets.UTF_8
//                             );

//                     String newText =
//                             new String(
//                                     B.get(insert.bIndex),
//                                     StandardCharsets.UTF_8
//                             );

//                     printHighlightRanges(
//                             oldText,
//                             newText
//                     );
//                 }
//             }
//         }
//     }

//     // ------------------------------------------------------------
//     // Character-level Myers diff using Unicode code points
//     // ------------------------------------------------------------

//     static List<Operation> myersCodePointDiff(
//             int[] A,
//             int[] B) {

//         int N = A.length;
//         int M = B.length;
//         int max = N + M;

//         List<Operation> result = new ArrayList<>();

//         if (max == 0) {
//             return result;
//         }

//         int offset = max;

//         int[] V = new int[2 * max + 1];

//         V[offset + 1] = 0;

//         /*
//          * Again, store only useful V slices.
//          */
//         List<int[]> trace = new ArrayList<>();

//         int finalD = 0;

//         outer:
//         for (int D = 0; D <= max; D++) {

//             for (int k = -D; k <= D; k += 2) {

//                 int i;

//                 if (k == -D ||
//                         (k != D &&
//                                 V[offset + k - 1]
//                                         < V[offset + k + 1])) {

//                     i = V[offset + k + 1];

//                 } else {

//                     i = V[offset + k - 1] + 1;
//                 }

//                 int j = i - k;

//                 // Follow matching code points.
//                 while (i < N &&
//                         j < M &&
//                         A[i] == B[j]) {

//                     i++;
//                     j++;
//                 }

//                 V[offset + k] = i;

//                 if (i >= N && j >= M) {

//                     trace.add(saveVSlice(V, offset, D));

//                     finalD = D;

//                     break outer;
//                 }
//             }

//             trace.add(saveVSlice(V, offset, D));
//         }

//         // --------------------------------------------------------
//         // Backtracking
//         // --------------------------------------------------------

//         int i = N;
//         int j = M;

//         for (int D = finalD; D > 0; D--) {

//             int k = i - j;

//             int[] previousV = trace.get(D - 1);

//             int previousK;

//             if (k == -D ||
//                     (k != D &&
//                             getVSlice(previousV, D - 1, k - 1)
//                                     < getVSlice(previousV, D - 1, k + 1))) {

//                 previousK = k + 1;

//             } else {

//                 previousK = k - 1;
//             }

//             int previousI =
//                     getVSlice(
//                             previousV,
//                             D - 1,
//                             previousK
//                     );

//             int previousJ =
//                     previousI - previousK;

//             // KEEP
//             while (i > previousI &&
//                     j > previousJ) {

//                 result.add(new Operation(
//                         ' ',
//                         i - 1,
//                         j - 1
//                 ));

//                 i--;
//                 j--;
//             }

//             // INSERT / DELETE
//             if (i == previousI) {

//                 result.add(new Operation(
//                         '+',
//                         i,
//                         j - 1
//                 ));

//                 j--;

//             } else {

//                 result.add(new Operation(
//                         '-',
//                         i - 1,
//                         j
//                 ));

//                 i--;
//             }
//         }

//         // Remaining KEEP operations.
//         while (i > 0 && j > 0) {

//             result.add(new Operation(
//                     ' ',
//                     i - 1,
//                     j - 1
//             ));

//             i--;
//             j--;
//         }

//         Collections.reverse(result);

//         return result;
//     }

//     // ------------------------------------------------------------
//     // Highlight changed character ranges
//     // ------------------------------------------------------------

//     static void printHighlightRanges(
//             String oldText,
//             String newText) {

//         int[] oldCodePoints =
//                 oldText.codePoints().toArray();

//         int[] newCodePoints =
//                 newText.codePoints().toArray();

//         List<Operation> operations =
//                 myersCodePointDiff(
//                         oldCodePoints,
//                         newCodePoints
//                 );

//         List<int[]> oldRanges = new ArrayList<>();
//         List<int[]> newRanges = new ArrayList<>();

//         int oldPosition = 0;
//         int newPosition = 0;

//         for (Operation op : operations) {

//             if (op.type == ' ') {

//                 oldPosition++;
//                 newPosition++;

//             } else if (op.type == '-') {

//                 addRange(
//                         oldRanges,
//                         oldPosition,
//                         oldPosition + 1
//                 );

//                 oldPosition++;

//             } else {

//                 addRange(
//                         newRanges,
//                         newPosition,
//                         newPosition + 1
//                 );

//                 newPosition++;
//             }
//         }

//         String oldResult =
//                 formatRanges(oldRanges);

//         String newResult =
//                 formatRanges(newRanges);

//         try {

//             String output =
//                     "? "
//                     + oldResult
//                     + " | "
//                     + newResult;

//             System.out.write(
//                     output.getBytes(StandardCharsets.UTF_8)
//             );

//             System.out.write('\n');

//         } catch (IOException e) {

//             System.err.println(
//                     "Error writing highlight: "
//                             + e.getMessage()
//             );

//             System.exit(2);
//         }
//     }

//     // ------------------------------------------------------------
//     // Add / merge ranges
//     // ------------------------------------------------------------

//     static void addRange(
//             List<int[]> ranges,
//             int start,
//             int end) {

//         if (start >= end) {
//             return;
//         }

//         if (!ranges.isEmpty()) {

//             int[] last =
//                     ranges.get(ranges.size() - 1);

//             // Merge touching or overlapping ranges.
//             if (start <= last[1]) {

//                 last[1] =
//                         Math.max(last[1], end);

//                 return;
//             }
//         }

//         ranges.add(
//                 new int[]{start, end}
//         );
//     }

//     // ------------------------------------------------------------
//     // Format ranges
//     // ------------------------------------------------------------

//     static String formatRanges(
//             List<int[]> ranges) {

//         if (ranges.isEmpty()) {
//             return ".";
//         }

//         StringBuilder sb =
//                 new StringBuilder();

//         for (int i = 0; i < ranges.size(); i++) {

//             if (i > 0) {
//                 sb.append(",");
//             }

//             int[] range = ranges.get(i);

//             sb.append(range[0])
//                     .append("-")
//                     .append(range[1]);
//         }

//         return sb.toString();
//     }

//     // ------------------------------------------------------------
//     // Exact UTF-8 output with LF
//     // ------------------------------------------------------------

//     static void writeUtf8Line(
//             String prefix,
//             byte[] line) {

//         try {

//             System.out.write(
//                     prefix.getBytes(StandardCharsets.UTF_8)
//             );

//             System.out.write(line);

//             // IMPORTANT:
//             // Explicit LF instead of println(),
//             // because Windows println() produces CRLF.
//             System.out.write('\n');

//         } catch (IOException e) {

//             System.err.println(
//                     "Error writing output: "
//                             + e.getMessage()
//             );

//             System.exit(2);
//         }
//     }
// }