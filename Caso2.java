import java.io.*;
import java.util.*;

public class Caso2 {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        System.out.println("1. Generar procesos");
        System.out.println("2. Simular memoria");
        System.out.print("Digite la opcion: ");
        int opcion = sc.nextInt();
        sc.nextLine();

        if (opcion == 1) {
            System.out.print("Digite el nombre del archivo de configuracion: ");
            String archivoConfig = sc.nextLine().trim();
            Generador.generarArchivos(archivoConfig);
        } else if (opcion == 2) {
            System.out.print("Digite el numero de marcos de memoria: ");
            int marcosTotales = sc.nextInt();
            Simulador.simular(marcosTotales);
        } else {
            System.out.println("Opcion incorrecta");
        }
    }
}

class Generador {
    public static void generarArchivos(String config) throws Exception {
        File carpeta = new File("casos");
        if (!carpeta.exists()) {
            carpeta.mkdir();
        }

        Map<String, String> datos = leerConfig(config);
        int TP = Integer.parseInt(datos.get("TP"));
        int NPROC = Integer.parseInt(datos.get("NPROC"));
        String[] TAMS = datos.get("TAMS").split(",");

        for (int i = 0; i < NPROC; i++) {
            String[] dato = TAMS[i].trim().split("x");
            int NF = Integer.parseInt(dato[0]);
            int NC = Integer.parseInt(dato[1]);

            List<Long> direcciones = new ArrayList<>();
            long baseA = 0;
            long baseB = (long) 4 * NF * NC;
            long baseC = (long) 2 * baseB;

            for (int f = 0; f < NF; f++) {
                for (int c = 0; c < NC; c++) {
                    long pos = (long) f * NC + c;
                    direcciones.add(baseA + (long) 4 * pos);
                    direcciones.add(baseB + (long) 4 * pos);
                    direcciones.add(baseC + (long) 4 * pos);
                }
            }

            long NR = direcciones.size();
            long bytesTotal = (long) 3 * NF * NC * (long) 4;
            long NP = (bytesTotal + TP - 1) / TP;

            String nombreArchivo = "casos/proceso" + i + ".txt";
            try (PrintWriter pw = new PrintWriter(new FileWriter(nombreArchivo))) {
                pw.printf("TP=%d NF=%d NC=%d NR=%d NP=%d\n", TP, NF, NC, NR, NP);
                for (long dir : direcciones) {
                    pw.println(dir);
                }
            }
            System.out.println("Generado: " + nombreArchivo + " (NR=" + NR + ", NP=" + NP + ")");
        }
    }

    static Map<String, String> leerConfig(String archivo) throws Exception {
        Map<String, String> datos = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                String[] partes = linea.split("=", 2);
                if (partes.length == 2) {
                    datos.put(partes[0].trim(), partes[1].trim());
                }
            }
        }
        return datos;
    }
}

class Simulador {
    static long accesos = (long) 1; 

    static class Marco {
        int idMarco;
        int idProceso;
        int pagina;
        long ultimoUso;

        Marco(int id, int idProc) {
            this.idMarco = id;
            this.idProceso = idProc;
            this.pagina = -1;
            this.ultimoUso = (long) 0;
        }
    }

    static class Proceso {
        int idProceso, NF, NC, TP, NP;
        List<Integer> direcciones = new ArrayList<>();
        int[] paginas;
        int posActual = 0;
        List<Integer> misMarcos = new ArrayList<>();
        long accesos = 0, fallos = 0, accesosSwap = 0;

        Proceso(int id, int NF, int NC, int TP, int NP) {
            this.idProceso = id;
            this.NF = NF;
            this.NC = NC;
            this.TP = TP;
            this.NP = NP;
            this.paginas = new int[NP];
            Arrays.fill(this.paginas, -1);
        }

        boolean terminado() {
            return posActual >= direcciones.size();
        }
    }

    Marco[] marcos;
    Proceso[] procesos;

    static void simular(int totalMarcos) throws Exception {
        Simulador sim = new Simulador();
        sim.cargarProcesos();
        sim.asignarMarcos(totalMarcos);
        sim.ejecutar();
        sim.mostrarResultados();
    }

    void cargarProcesos() throws Exception {
        File carpeta = new File("casos");
        File[] archivos = carpeta.listFiles((d, name) -> name.endsWith(".txt"));
        if (archivos == null || archivos.length == 0) {
            throw new RuntimeException("No se encontraron procesos en la carpeta");
        }

        procesos = new Proceso[archivos.length];
        for (int i = 0; i < archivos.length; i++) {
            File archivo = archivos[i];
            try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                String cabecera = br.readLine();
                Map<String, Integer> datos = leerCabecera(cabecera);
                int TP = datos.get("TP"), NF = datos.get("NF"), NC = datos.get("NC"), NP = datos.get("NP");
                Proceso p = new Proceso(i, NF, NC, TP, NP);
                String linea;
                while ((linea = br.readLine()) != null) {
                    p.direcciones.add(Integer.parseInt(linea.trim()));
                }
                p.accesos = p.direcciones.size();
                procesos[i] = p;
            }
        }
    }

    static Map<String, Integer> leerCabecera(String cabecera) {
        Map<String, Integer> datos = new HashMap<>();
        String[] partes = cabecera.split("\\s+");
        for (String t : partes) {
            if (t.isEmpty()) continue;
            String[] dato = t.split("=", 2);
            if (dato.length == 2) datos.put(dato[0], Integer.parseInt(dato[1]));
        }
        return datos;
    }

    void asignarMarcos(int totalMarcos) {
        int marcosPorProceso = totalMarcos / procesos.length;
        marcos = new Marco[totalMarcos];
        int id = 0;
        for (int i = 0; i < procesos.length; i++) {
            for (int j = 0; j < marcosPorProceso; j++) {
                marcos[id] = new Marco(id, i);
                procesos[i].misMarcos.add(id);
                id++;
            }
        }
        System.out.println("Marcos totales: " + totalMarcos + ", por proceso: " + marcosPorProceso);
    }

    void ejecutar() {
        Queue<Integer> cola = new LinkedList<>();
        for (int i = 0; i < procesos.length; i++) cola.add(i);

        while (!cola.isEmpty()) {
            int idProc = cola.poll();
            Proceso p = procesos[idProc];
            if (p.terminado()) {
                liberarMarcos(idProc);
                continue;
            }

            int dir = p.direcciones.get(p.posActual);
            int numPagina = dir / p.TP;
            int marcoAsignado = p.paginas[numPagina];

            if (marcoAsignado != -1) {
                marcos[marcoAsignado].ultimoUso = accesos++;
                p.posActual++;
                if (!p.terminado()) cola.add(idProc);
            } else {
                p.fallos++;
                Integer libre = buscarLibre(p);
                if (libre != null) {
                    cargarPagina(libre, idProc, numPagina);
                    p.paginas[numPagina] = libre;
                    p.accesosSwap += 1;
                } else {
                    int victima = seleccionarLRU(p);
                    int paginaVieja = marcos[victima].pagina;
                    if (paginaVieja != -1) p.paginas[paginaVieja] = -1;
                    cargarPagina(victima, idProc, numPagina);
                    p.paginas[numPagina] = victima;
                    p.accesosSwap += 2;
                }
                cola.add(idProc);
            }
        }
    }

    private Integer buscarLibre(Proceso p) {
        for (int m : p.misMarcos) {
            if (marcos[m].pagina == -1) return m;
        }
        return null;
    }

    private int seleccionarLRU(Proceso p) {
        long minimo = Long.MAX_VALUE;
        int victima = -1;
        for (int m : p.misMarcos) {
            if (marcos[m].ultimoUso < minimo) {
                minimo = marcos[m].ultimoUso;
                victima = m;
            }
        }
        return victima;
    }

    private void cargarPagina(int m, int idProc, int numPagina) {
        marcos[m].idProceso = idProc;
        marcos[m].pagina = numPagina;
        marcos[m].ultimoUso = accesos++;
    }

    private void liberarMarcos(int idProc) {
        Proceso p = procesos[idProc];
        if (!p.misMarcos.isEmpty()) {
            int candidato = -1;
            long masFallos = -1;
            for (Proceso otro : procesos) {
                if (otro.idProceso == idProc || otro.terminado()) continue;
                if (otro.fallos > masFallos) {
                    masFallos = otro.fallos;
                    candidato = otro.idProceso;
                }
            }
            if (candidato == -1) return;
            for (int m : new ArrayList<>(p.misMarcos)) {
                marcos[m].idProceso = candidato;
                marcos[m].pagina = -1;
                marcos[m].ultimoUso = (long) 0;
                procesos[candidato].misMarcos.add(m);
            }
            p.misMarcos.clear();
        }
    }

    void mostrarResultados() {
        for (Proceso p : procesos) {
            long aciertos = p.accesos - p.fallos;
            double tasaFallos = p.accesos > 0 ? (double) p.fallos / p.accesos : 0;
            double tasaAciertos = p.accesos > 0 ? (double) aciertos / p.accesos : 0;
            System.out.printf("Proceso %d: accesos=%d, fallos=%d, swap=%d, tasaFallos=%.4f, tasaAciertos=%.4f\n",
                    p.idProceso, p.accesos, p.fallos, p.accesosSwap, tasaFallos, tasaAciertos);
        }
    }
}
