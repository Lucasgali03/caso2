import java.io.*;
import java.util.*;

public class MainSimulador {
    private static final String CARPETA_CASOS = "casos";

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        System.out.println("=== SIMULADOR DE MEMORIA VIRTUAL ===");
        System.out.println("1. Generar archivos de referencias (proc<i>.txt)");
        System.out.println("2. Ejecutar simulador de memoria");
        System.out.print("Opción: ");
        int opcion = sc.nextInt();
        sc.nextLine(); // limpiar buffer

        if (opcion == 1) {
            System.out.print("Ingrese el archivo de configuración (ej: config.txt): ");
            String configFile = sc.nextLine().trim();
            ReferenceGenerator.generar(configFile);
        } else if (opcion == 2) {
            System.out.print("Ingrese el número total de marcos de memoria: ");
            int totalFrames = sc.nextInt();
            Simulator.ejecutarSimulador(totalFrames);
        } else {
            System.out.println("Opción inválida.");
        }
    }
}

/* =======================
 * OPCIÓN 1: GENERADOR
 * ======================= */
class ReferenceGenerator {
    public static void generar(String configFile) throws Exception {
        // Crear carpeta casos si no existe
        File carpeta = new File("casos");
        if (!carpeta.exists()) carpeta.mkdir();

        Map<String, String> map = parseConfig(configFile);
        int TP = Integer.parseInt(map.get("TP"));
        int NPROC = Integer.parseInt(map.get("NPROC"));
        String[] parts = map.get("TAMS").split(",");

        for (int p = 0; p < NPROC; p++) {
            String[] rc = parts[p].trim().split("x");
            int NF = Integer.parseInt(rc[0]);
            int NC = Integer.parseInt(rc[1]);

            List<Long> dvs = new ArrayList<>();
            long baseA = 0, baseB = 4L * NF * NC, baseC = 2L * baseB;

            for (int i = 0; i < NF; i++) {
                for (int j = 0; j < NC; j++) {
                    long idx = (long) i * NC + j;
                    dvs.add(baseA + 4L * idx); // A
                    dvs.add(baseB + 4L * idx); // B
                    dvs.add(baseC + 4L * idx); // C
                }
            }

            long NR = dvs.size();
            long bytesTotal = 3L * NF * NC * 4L;
            long NP = (bytesTotal + TP - 1) / TP;

            String filename = "casos/proc" + p + ".txt";
            try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
                pw.printf("TP=%d NF=%d NC=%d NR=%d NP=%d\n", TP, NF, NC, NR, NP);
                for (long dv : dvs) pw.println(dv);
            }
            System.out.println("Generado: " + filename + " (NR=" + NR + ", NP=" + NP + ")");
        }
    }

    static Map<String, String> parseConfig(String cfg) throws Exception {
        Map<String, String> m = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(cfg))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] kv = line.split("=", 2);
                if (kv.length == 2) m.put(kv[0].trim(), kv[1].trim());
            }
        }
        return m;
    }
}

/* =======================
 * OPCIÓN 2: SIMULADOR
 * ======================= */
class Simulator {
    static long timeCounter = 1L;

    static class Frame {
        int id, ownerPid, vpage;
        long lastUsed;
        Frame(int id, int ownerPid) {
            this.id = id; this.ownerPid = ownerPid;
            this.vpage = -1; this.lastUsed = 0L;
        }
    }

    static class Proceso {
        int pid, NF, NC, TP, NP;
        List<Integer> dvs = new ArrayList<>();
        int[] pageTable;
        int currentIndex = 0;
        List<Integer> assignedFrames = new ArrayList<>();
        long references = 0, pageFaults = 0, swapAccesses = 0;
        Proceso(int pid, int NF, int NC, int TP, int NP) {
            this.pid = pid; this.NF = NF; this.NC = NC; this.TP = TP; this.NP = NP;
            this.pageTable = new int[NP];
            Arrays.fill(this.pageTable, -1);
        }
        boolean finished() { return currentIndex >= dvs.size(); }
    }

    Frame[] frames;
    Proceso[] procs;

    static void ejecutarSimulador(int totalFrames) throws Exception {
        Simulator sim = new Simulator();
        sim.cargarProcesos();
        sim.asignarMarcos(totalFrames);
        sim.ejecutar();
        sim.imprimirStats();
    }

    void cargarProcesos() throws Exception {
        File carpeta = new File("casos");
        File[] archivos = carpeta.listFiles((d, name) -> name.endsWith(".txt"));
        if (archivos == null || archivos.length == 0) {
            throw new RuntimeException("No se encontraron casos en la carpeta 'casos'.");
        }

        procs = new Proceso[archivos.length];
        for (int p = 0; p < archivos.length; p++) {
            File archivo = archivos[p];
            try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                String header = br.readLine();
                Map<String, Integer> h = parseHeader(header);
                int TP = h.get("TP"), NF = h.get("NF"), NC = h.get("NC"), NP = h.get("NP");
                Proceso ps = new Proceso(p, NF, NC, TP, NP);
                String line;
                while ((line = br.readLine()) != null) ps.dvs.add(Integer.parseInt(line.trim()));
                ps.references = ps.dvs.size();
                procs[p] = ps;
            }
        }
    }

    static Map<String, Integer> parseHeader(String header) {
        Map<String, Integer> m = new HashMap<>();
        String[] toks = header.split("\\s+");
        for (String t : toks) {
            if (t.isEmpty()) continue;
            String[] kv = t.split("=", 2);
            if (kv.length == 2) m.put(kv[0], Integer.parseInt(kv[1]));
        }
        return m;
    }

    void asignarMarcos(int totalFrames) {
        int per = totalFrames / procs.length;
        frames = new Frame[totalFrames];
        int id = 0;
        for (int p = 0; p < procs.length; p++) {
            for (int k = 0; k < per; k++) {
                frames[id] = new Frame(id, p);
                procs[p].assignedFrames.add(id);
                id++;
            }
        }
        System.out.println("Marcos totales: " + totalFrames + ", por proceso: " + per);
    }

    void ejecutar() {
        Queue<Integer> cola = new LinkedList<>();
        for (int i = 0; i < procs.length; i++) cola.add(i);

        while (!cola.isEmpty()) {
            int pid = cola.poll();
            Proceso ps = procs[pid];
            if (ps.finished()) { reasignarMarcos(pid); continue; }

            int dv = ps.dvs.get(ps.currentIndex);
            int vpage = dv / ps.TP;
            int frameIndex = ps.pageTable[vpage];

            if (frameIndex != -1) { // hit
                frames[frameIndex].lastUsed = timeCounter++;
                ps.currentIndex++;
                if (!ps.finished()) cola.add(pid);
            } else { // fallo
                ps.pageFaults++;
                Integer freeFrame = buscarLibre(ps);
                if (freeFrame != null) {
                    cargarPagina(freeFrame, pid, vpage);
                    ps.pageTable[vpage] = freeFrame;
                    ps.swapAccesses += 1;
                } else {
                    int victima = seleccionarLRU(ps);
                    int oldVpage = frames[victima].vpage;
                    if (oldVpage != -1) ps.pageTable[oldVpage] = -1;
                    cargarPagina(victima, pid, vpage);
                    ps.pageTable[vpage] = victima;
                    ps.swapAccesses += 2;
                }
                cola.add(pid);
            }
        }
    }

    private Integer buscarLibre(Proceso ps) {
        for (int fid : ps.assignedFrames) if (frames[fid].vpage == -1) return fid;
        return null;
    }
    private int seleccionarLRU(Proceso ps) {
        long min = Long.MAX_VALUE; int victima = -1;
        for (int fid : ps.assignedFrames) {
            if (frames[fid].lastUsed < min) { min = frames[fid].lastUsed; victima = fid; }
        }
        return victima;
    }
    private void cargarPagina(int fid, int pid, int vpage) {
        frames[fid].ownerPid = pid;
        frames[fid].vpage = vpage;
        frames[fid].lastUsed = timeCounter++;
    }
    private void reasignarMarcos(int pidTerm) {
        Proceso term = procs[pidTerm];
        if (!term.assignedFrames.isEmpty()) {
            int mejor = -1; long maxFaults = -1;
            for (Proceso p : procs) {
                if (p.pid == pidTerm || p.finished()) continue;
                if (p.pageFaults > maxFaults) { maxFaults = p.pageFaults; mejor = p.pid; }
            }
            if (mejor == -1) return;
            for (int fid : new ArrayList<>(term.assignedFrames)) {
                frames[fid].ownerPid = mejor;
                frames[fid].vpage = -1;
                frames[fid].lastUsed = 0L;
                procs[mejor].assignedFrames.add(fid);
            }
            term.assignedFrames.clear();
        }
    }

    void imprimirStats() {
        System.out.println("\n=== Estadísticas finales ===");
        for (Proceso p : procs) {
            long hits = p.references - p.pageFaults;
            double tasaFallos = p.references > 0 ? (double) p.pageFaults / p.references : 0;
            double tasaExito = p.references > 0 ? (double) hits / p.references : 0;
            System.out.printf("Proc %d: refs=%d, fallos=%d, swap=%d, tasaFallos=%.4f, tasaExito=%.4f\n",
                    p.pid, p.references, p.pageFaults, p.swapAccesses, tasaFallos, tasaExito);
        }
    }
}
