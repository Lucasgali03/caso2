import java.io.*;
import java.util.*;

public class caso2 {
    public static void main(String[] args) throws Exception {
    if (args.length != 1) {
        System.err.println("Uso: java ReferenceGenerator <configFile>");
        System.exit(1);
    }   
    String cfg = args[0];
    Map<String,String> map = parseConfig(cfg);
    int TP = Integer.parseInt(map.get("TP"));
    int NPROC = Integer.parseInt(map.get("NPROC"));
    String tams = map.get("TAMS");
    String[] parts = tams.split(",");
    if (parts.length != NPROC) {
        throw new RuntimeException("NPROC y cantidad de TAMS no coinciden");
    }
    for (int p = 0; p < NPROC; p++) {
        String[] rc = parts[p].trim().split("x");
        int NF = Integer.parseInt(rc[0]);
        int NC = Integer.parseInt(rc[1]);
        List<Long> dvs = new ArrayList<>();
        long baseA = 0L;
        long baseB = 4L * NF * NC;
        long baseC = 2L * baseB;
        for (int i = 0; i < NF; i++) {
            for (int j = 0; j < NC; j++) {
                long idx = (long)i * NC + j;
                long dvA = baseA + 4L * idx;
                long dvB = baseB + 4L * idx;
                long dvC = baseC + 4L * idx;
                dvs.add(dvA);
                dvs.add(dvB);
                dvs.add(dvC);
            }
        }
        long NR = dvs.size();
        long bytesTotal = 3L * NF * NC * 4L;
        long NP = (bytesTotal + TP - 1) / TP;
        String filename = "proc" + p + ".txt";
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.printf("TP=%d NF=%d NC=%d NR=%d NP=%d\n", TP, NF, NC, NR, NP);
            for (long dv : dvs) pw.println(dv);
        }
        System.out.println("Generado: " + filename + " (NR=" + NR + ", NP=" + NP + ")");
    }
}

    private static Map<String,String> parseConfig(String cfg) throws Exception {
        Map<String,String> m = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(cfg))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] kv;
                if (line.contains("=")) kv = line.split("=",2);
                else kv = line.split("\s+",2);
                if (kv.length == 2) m.put(kv[0].trim(), kv[1].trim());
            }
        }
        return m;
    }
}
