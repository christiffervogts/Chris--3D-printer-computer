package main;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class STL_file_minipulation {

    File stl_given;
    BufferedReader br;
    String[] Lines;

    ArrayList<Vertex> vertices = new ArrayList<>();
    float[][] rawVertices;

    private final Home home;

    public STL_file_minipulation(Home home) {
        this.home = home;
    }

    public void stl_get(File stl_get) {
        stl_given = stl_get;
        try {
            br = new BufferedReader(new FileReader(stl_given));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        stl_decomposition();
        try { br.close(); } catch (IOException e) { e.printStackTrace(); }
    }

    public void stl_decomposition() {
        if (!stl_given.exists()) return;

        // Detect binary vs ASCII without reading the whole file
        if (isBinarySTL(stl_given)) {
            parseBinaryDirect(stl_given);  // parse binary directly, no temp file
        } else {
            parseASCIIStreaming(stl_given); // single-pass, no Lines[] array
        }
        System.out.println("vertices: " + vertices.size());
        home.onMeshReady();
    }

    private boolean isBinarySTL(File f) {
        // ASCII STL always starts with "solid"; binary rarely does
        // but safest check: compare file size against expected binary size
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(f)))) {
            byte[] header = new byte[80];
            in.readFully(header);
            byte[] countBytes = new byte[4];
            in.readFully(countBytes);
            int triCount = ByteBuffer.wrap(countBytes)
                               .order(ByteOrder.LITTLE_ENDIAN).getInt();
            long expectedSize = 80 + 4 + (long) triCount * 50;
            return f.length() == expectedSize;
        } catch (Exception e) { return false; }
    }

    // Parse binary directly into rawVertices — no temp file, one pass
    private void parseBinaryDirect(File f) {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(f), 1 << 16))) {
            byte[] header = new byte[80];
            in.readFully(header);
            int triCount = ByteBuffer.wrap(new byte[]{
                (byte)in.read(),(byte)in.read(),
                (byte)in.read(),(byte)in.read()
            }).order(ByteOrder.LITTLE_ENDIAN).getInt();

            rawVertices = new float[triCount * 3][3];
            vertices.clear();

            for (int t = 0; t < triCount; t++) {
                // skip normal
                in.skipBytes(12);

                for (int v = 0; v < 3; v++) {
                    float x = readFloatLE(in);
                    float y = readFloatLE(in);
                    float z = readFloatLE(in);
                    rawVertices[t * 3 + v][0] = x;
                    rawVertices[t * 3 + v][1] = y;
                    rawVertices[t * 3 + v][2] = z;
                    vertices.add(new Vertex((int)x, (int)y, (int)z));
                }
                in.skipBytes(2); // attribute byte count
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // Parse ASCII in a single streaming pass — no Lines[] array held in memory
    private void parseASCIIStreaming(File f) {
        ArrayList<float[]> rawList = new ArrayList<>();
        vertices.clear();

        String[] pending = new String[2]; // rolling window of last 2 vertex lines
        int pendingCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new FileReader(f), 1 << 16)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("vertex")) {
                    if (pendingCount < 2) {
                        pending[pendingCount++] = line;
                    } else {
                        // we have all 3 lines of a triangle
                        float[] v0 = parseVertex(pending[0]);
                        float[] v1 = parseVertex(pending[1]);
                        float[] v2 = parseVertex(line);
                        rawList.add(v0); rawList.add(v1); rawList.add(v2);
                        vertices.add(new Vertex((int)v0[0],(int)v0[1],(int)v0[2]));
                        vertices.add(new Vertex((int)v1[0],(int)v1[1],(int)v1[2]));
                        vertices.add(new Vertex((int)v2[0],(int)v2[1],(int)v2[2]));
                        pendingCount = 0;
                    }
                } else if (line.startsWith("endfacet") || line.startsWith("endloop")) {
                    pendingCount = 0; // reset on facet boundary for safety
                }
            }
        } catch (IOException e) { e.printStackTrace(); }

        rawVertices = rawList.toArray(new float[0][]);
    }
    public int countLines(File fileName) {
        int lines = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            while (reader.readLine() != null) lines++;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }
    
    private float readFloatLE(DataInputStream in) throws IOException {
        byte[] b = new byte[4];
        in.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    public void vector_making() {
        vertices.clear();
        ArrayList<float[]> rawList = new ArrayList<>();

        int l = 0;
        for (int i = 0; i < Lines.length; i++) {
            if (Lines[i].contains("vertex")) {
                l++;
                if (l % 3 == 0) {

                    float[] v0 = parseVertex(Lines[i - 2]);
                    float[] v1 = parseVertex(Lines[i - 1]);
                    float[] v2 = parseVertex(Lines[i]);

                    rawList.add(v0);
                    rawList.add(v1);
                    rawList.add(v2);

                    vertices.add(new Vertex((int) v0[0], (int) v0[1], (int) v0[2]));
                    vertices.add(new Vertex((int) v1[0], (int) v1[1], (int) v1[2]));
                    vertices.add(new Vertex((int) v2[0], (int) v2[1], (int) v2[2]));

                    System.out.println("NEW MESH");
                }
            }
        }

        vertices.sort((a, b) -> {
            if (a.i != b.i) return Integer.compare(a.i, b.i);
            if (a.j != b.j) return Integer.compare(a.j, b.j);
            return Integer.compare(a.k, b.k);
        });

        rawVertices = rawList.toArray(new float[0][]);

        System.out.println("Sorting complete — " + rawVertices.length + " vertices, "
                + (rawVertices.length / 3) + " triangles ready.");

        home.onMeshReady();
    }

    private float[] parseVertex(String line) {
        String[] parts = line.trim().split("\\s+");
        float[] v = new float[3];
        try { v[0] = Float.parseFloat(parts[1]); } catch (Exception e) { v[0] = 0; }
        try { v[1] = Float.parseFloat(parts[2]); } catch (Exception e) { v[1] = 0; }
        try { v[2] = Float.parseFloat(parts[3]); } catch (Exception e) { v[2] = 0; }
        return v;
    }

    public int vertex_i_get(String s) { return (int) Float.parseFloat(s.trim().split("\\s+")[1]); }
    public int vertex_j_get(String s) { return (int) Float.parseFloat(s.trim().split("\\s+")[2]); }
    public int vertex_k_get(String s) { return (int) Float.parseFloat(s.trim().split("\\s+")[3]); }

    public void mesh_maping() {}
    public void mesh_ordering() {}
    public void give_to_pi() {}
}