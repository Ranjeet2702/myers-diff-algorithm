import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Main {

    static class Operation {
        String type;
        int aIndex;
        int bIndex;

        Operation(String type, int aIndex, int bIndex) {
            this.type = type;
            this.aIndex = aIndex;
            this.bIndex = bIndex;
        }
    }

    public static void main(String[] args) {

        if (args.length != 3) {
            System.err.println("Usage: java Main <lines|highlight> <fileA> <fileB>");
            System.exit(2);
        }

        String command = args[0];

        if (!command.equals("lines") &&
                !command.equals("highlight") &&
                !command.equals("diff")) {

            System.err.println("Unknown command: " + command);
            System.exit(2);
        }

        byte[] fileA;
        byte[] fileB;

        try {
            fileA = Files.readAllBytes(Path.of(args[1]));
            fileB = Files.readAllBytes(Path.of(args[2]));
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            System.exit(2);
            return;
        }

        List<byte[]> A = splitLines(fileA);
        List<byte[]> B = splitLines(fileB);

        List<Operation> operations = myersDiff(A, B);

        if (command.equals("highlight")) {
            printHighlight(A, B, operations);
        } else {
            printDiff(A, B, operations);
        }
    }

    // ------------------------------------------------------------
    // Split file into lines using raw bytes.
    // Newline byte '\n' is the separator.
    // '\r' is preserved as part of the line.
    // ------------------------------------------------------------

    static List<byte[]> splitLines(byte[] data) {

        List<byte[]> lines = new ArrayList<>();

        int start = 0;

        for (int i = 0; i < data.length; i++) {

            if (data[i] == '\n') {

                lines.add(Arrays.copyOfRange(data, start, i));

                start = i + 1;
            }
        }

        // Add final piece only if it is non-empty.
        if (start < data.length) {
            lines.add(Arrays.copyOfRange(data, start, data.length));
        }

        return lines;
    }

    // ------------------------------------------------------------
    // Myers diff for lines
    // ------------------------------------------------------------

    static List<Operation> myersDiff(List<byte[]> A, List<byte[]> B) {

        int N = A.size();
        int M = B.size();

        int max = N + M;

        List<Operation> result = new ArrayList<>();

        if (max == 0) {
            return result;
        }

        int offset = max;

        int[] V = new int[2 * max + 1];

        V[offset + 1] = 0;

        List<int[]> trace = new ArrayList<>();

        int finalD = 0;

        outer: for (int D = 0; D <= max; D++) {

            for (int k = -D; k <= D; k += 2) {

                int i;

                // Insert
                if (k == -D ||
                        (k != D &&
                                V[k - 1 + offset] < V[k + 1 + offset])) {

                    i = V[k + 1 + offset];

                } else {

                    // Delete
                    i = V[k - 1 + offset] + 1;
                }

                int j = i - k;

                // Follow diagonal while lines are equal.
                while (i < N &&
                        j < M &&
                        Arrays.equals(A.get(i), B.get(j))) {

                    i++;
                    j++;
                }

                V[k + offset] = i;

                if (i >= N && j >= M) {

                    trace.add(V.clone());

                    finalD = D;

                    break outer;
                }
            }

            trace.add(V.clone());
        }

        // --------------------------------------------------------
        // Backtracking
        // --------------------------------------------------------

        int i = N;
        int j = M;

        for (int D = finalD; D > 0; D--) {

            int[] previousV = trace.get(D - 1);

            int k = i - j;

            int previousK;

            if (k == -D ||
                    (k != D &&
                            previousV[k - 1 + offset] < previousV[k + 1 + offset])) {

                previousK = k + 1;

            } else {

                previousK = k - 1;
            }

            int previousI = previousV[previousK + offset];

            int previousJ = previousI - previousK;

            // Walk backwards through equal lines.
            while (i > previousI &&
                    j > previousJ) {

                result.add(
                        new Operation(
                                "KEEP",
                                i - 1,
                                j - 1));

                i--;
                j--;
            }

            // Insert
            if (i == previousI) {

                result.add(
                        new Operation(
                                "INSERT",
                                i,
                                j - 1));

                j--;

            } else {

                // Delete
                result.add(
                        new Operation(
                                "DELETE",
                                i - 1,
                                j));

                i--;
            }
        }

        // Remaining common prefix.
        while (i > 0 && j > 0) {

            result.add(
                    new Operation(
                            "KEEP",
                            i - 1,
                            j - 1));

            i--;
            j--;
        }

        while (i > 0) {

            result.add(
                    new Operation(
                            "DELETE",
                            i - 1,
                            0));

            i--;
        }

        while (j > 0) {

            result.add(
                    new Operation(
                            "INSERT",
                            0,
                            j - 1));

            j--;
        }

        Collections.reverse(result);

        return result;
    }

    // ------------------------------------------------------------
    // Part A output
    // Raw bytes are written directly.
    // This is important because lines may contain invalid UTF-8.
    // ------------------------------------------------------------

    static void printDiff(
            List<byte[]> A,
            List<byte[]> B,
            List<Operation> operations) {

        try {

            for (Operation op : operations) {

                if (op.type.equals("KEEP")) {

                    System.out.write(' ');
                    System.out.write(A.get(op.aIndex));
                    System.out.write('\n');

                } else if (op.type.equals("DELETE")) {

                    System.out.write('-');
                    System.out.write(A.get(op.aIndex));
                    System.out.write('\n');

                } else {

                    System.out.write('+');
                    System.out.write(B.get(op.bIndex));
                    System.out.write('\n');
                }
            }

            System.out.flush();

        } catch (IOException e) {

            System.err.println(
                    "Error writing output: " + e.getMessage());

            System.exit(2);
        }

    }

    // ------------------------------------------------------------
    // Part B
    // ------------------------------------------------------------

    static void printHighlight(
            List<byte[]> A,
            List<byte[]> B,
            List<Operation> operations) {

        int index = 0;

        while (index < operations.size()) {

            Operation op = operations.get(index);

            // ----------------------------------------------------
            // KEEP
            // ----------------------------------------------------

            if (op.type.equals("KEEP")) {

                writeUtf8Line(
                        " ",
                        A.get(op.aIndex));

                index++;
                continue;
            }

            // ----------------------------------------------------
            // Change block
            //
            // All DELETE operations first,
            // then all INSERT operations.
            // ----------------------------------------------------

            List<Operation> deletes = new ArrayList<>();
            List<Operation> inserts = new ArrayList<>();

            while (index < operations.size() &&
                    operations.get(index).type.equals("DELETE")) {

                deletes.add(operations.get(index));

                index++;
            }

            while (index < operations.size() &&
                    operations.get(index).type.equals("INSERT")) {

                inserts.add(operations.get(index));

                index++;
            }

            // Print deletes.
            for (Operation delete : deletes) {

                writeUtf8Line(
                        "-",
                        A.get(delete.aIndex));
            }

            // Print inserts and corresponding ? line.
            int pairs = Math.min(
                    deletes.size(),
                    inserts.size());

            for (int i = 0; i < inserts.size(); i++) {

                Operation insert = inserts.get(i);

                writeUtf8Line(
                        "+",
                        B.get(insert.bIndex));

                // Only paired lines get a ? line.
                if (i < pairs) {

                    Operation delete = deletes.get(i);

                    String oldText = new String(
                            A.get(delete.aIndex),
                            StandardCharsets.UTF_8);

                    String newText = new String(
                            B.get(insert.bIndex),
                            StandardCharsets.UTF_8);

                    printHighlightRanges(
                            oldText,
                            newText);
                }
            }
        }
    }

    // ------------------------------------------------------------
    // Character-level Myers using Unicode code points.
    // ------------------------------------------------------------

    static List<Operation> myersCodePointDiff(
            int[] A,
            int[] B) {

        int N = A.length;
        int M = B.length;

        int max = N + M;

        List<Operation> result = new ArrayList<>();

        if (max == 0) {
            return result;
        }

        int offset = max;

        int[] V = new int[2 * max + 1];

        V[offset + 1] = 0;

        List<int[]> trace = new ArrayList<>();

        int finalD = 0;

        outer: for (int D = 0; D <= max; D++) {

            for (int k = -D; k <= D; k += 2) {

                int i;

                if (k == -D ||
                        (k != D &&
                                V[k - 1 + offset] < V[k + 1 + offset])) {

                    // Insert
                    i = V[k + 1 + offset];

                } else {

                    // Delete
                    i = V[k - 1 + offset] + 1;
                }

                int j = i - k;

                // Matching code points.
                while (i < N &&
                        j < M &&
                        A[i] == B[j]) {

                    i++;
                    j++;
                }

                V[k + offset] = i;

                if (i >= N && j >= M) {

                    trace.add(V.clone());

                    finalD = D;

                    break outer;
                }
            }

            trace.add(V.clone());
        }

        // --------------------------------------------------------
        // Backtracking
        // --------------------------------------------------------

        int i = N;
        int j = M;

        for (int D = finalD; D > 0; D--) {

            int[] previousV = trace.get(D - 1);

            int k = i - j;

            int previousK;

            if (k == -D ||
                    (k != D &&
                            previousV[k - 1 + offset] < previousV[k + 1 + offset])) {

                previousK = k + 1;

            } else {

                previousK = k - 1;
            }

            int previousI = previousV[previousK + offset];

            int previousJ = previousI - previousK;

            // Matching diagonal.
            while (i > previousI &&
                    j > previousJ) {

                result.add(
                        new Operation(
                                "KEEP",
                                i - 1,
                                j - 1));

                i--;
                j--;
            }

            if (i == previousI) {

                // Insert
                result.add(
                        new Operation(
                                "INSERT",
                                i,
                                j - 1));

                j--;

            } else {

                // Delete
                result.add(
                        new Operation(
                                "DELETE",
                                i - 1,
                                j));

                i--;
            }
        }

        while (i > 0 && j > 0) {

            result.add(
                    new Operation(
                            "KEEP",
                            i - 1,
                            j - 1));

            i--;
            j--;
        }

        while (i > 0) {

            result.add(
                    new Operation(
                            "DELETE",
                            i - 1,
                            0));

            i--;
        }

        while (j > 0) {

            result.add(
                    new Operation(
                            "INSERT",
                            0,
                            j - 1));

            j--;
        }

        Collections.reverse(result);

        return result;
    }

    // ------------------------------------------------------------
    // Find changed character ranges.
    // ------------------------------------------------------------

    static void printHighlightRanges(
            String oldText,
            String newText) {

        int[] oldCP = oldText.codePoints().toArray();

        int[] newCP = newText.codePoints().toArray();

        List<Operation> operations = myersCodePointDiff(oldCP, newCP);

        List<int[]> oldRanges = new ArrayList<>();

        List<int[]> newRanges = new ArrayList<>();

        int oldPos = 0;
        int newPos = 0;

        for (Operation op : operations) {

            if (op.type.equals("KEEP")) {

                oldPos++;
                newPos++;

            } else if (op.type.equals("DELETE")) {

                addRange(
                        oldRanges,
                        oldPos,
                        oldPos + 1);

                oldPos++;

            } else {

                addRange(
                        newRanges,
                        newPos,
                        newPos + 1);

                newPos++;
            }
        }

        try {
            String output = "? " +
                    formatRanges(oldRanges) +
                    " | " +
                    formatRanges(newRanges);

            System.out.write(
                    output.getBytes(StandardCharsets.UTF_8));
            System.out.write('\n');
        } catch (IOException e) {
            System.err.println(
                    "Error writing output: " + e.getMessage());
            System.exit(2);
        }
    }

    // ------------------------------------------------------------
    // Add/merge range.
    // Example:
    // 3-5 + 5-7
    // becomes
    // 3-7
    // ------------------------------------------------------------

    static void addRange(
            List<int[]> ranges,
            int start,
            int end) {

        if (start >= end) {
            return;
        }

        if (!ranges.isEmpty()) {

            int[] last = ranges.get(ranges.size() - 1);

            // Touching or overlapping ranges.
            if (start <= last[1]) {

                last[1] = Math.max(last[1], end);

                return;
            }
        }

        ranges.add(
                new int[] { start, end });
    }

    // ------------------------------------------------------------
    // Convert ranges to:
    //
    // .
    // 3-5
    // 3-5,9-12
    // ------------------------------------------------------------

    static String formatRanges(
            List<int[]> ranges) {

        if (ranges.isEmpty()) {
            return ".";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < ranges.size(); i++) {

            if (i > 0) {
                sb.append(",");
            }

            int[] range = ranges.get(i);

            sb.append(range[0])
                    .append("-")
                    .append(range[1]);
        }

        return sb.toString();
    }

    // ------------------------------------------------------------
    // UTF-8 output for Part B.
    // Highlight tests are valid UTF-8.
    // ------------------------------------------------------------

    static void writeUtf8Line(
        String prefix,
        byte[] line) {

        try {
            System.out.write(prefix.getBytes(StandardCharsets.UTF_8));
            System.out.write(line);
            System.out.write('\n');
            System.out.flush();
        } catch (IOException e) {
            System.err.println(
                    "Error writing output: " + e.getMessage());
            System.exit(2);
        }
    }

}