package main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class STL_file_minipulation {

    File stl_given;
    BufferedReader br;
    String[] Lines;

    // Legacy integer vertex list (kept for compatibility)
    ArrayList<Vertex> vertices = new ArrayList<>();

    // Raw float coordinates: rawVertices[n] = { x, y, z }
    // Every three consecutive rows form one triangle.
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

        boolean run_once = true;
        while (run_once) {
            Lines = new String[countLines(stl_given)];

            // Re-open the reader each pass (binary_to_ASCII may swap the file)
            try {
                br = new BufferedReader(new FileReader(stl_given));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            for (int i = 0; i < Lines.length; i++) {
                try {
                    Lines[i] = br.readLine().strip();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            run_once = false;
            if (!Lines[0].contains("solid")) {
                run_once = true;
                stl_given = binary_to_ASCII(stl_given);
            }
        }

        System.out.println("NEW GROUP");
        vector_making();
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

    public File binary_to_ASCII(File given) {
        return given;
    }

    public void vector_making() {
        vertices.clear();
        ArrayList<float[]> rawList = new ArrayList<>();

        int l = 0;
        for (int i = 0; i < Lines.length; i++) {
            if (Lines[i].contains("vertex")) {
                l++;
                if (l % 3 == 0) {
                    // Parse all three lines of the triangle as floats
                    float[] v0 = parseVertex(Lines[i - 2]);
                    float[] v1 = parseVertex(Lines[i - 1]);
                    float[] v2 = parseVertex(Lines[i]);

                    rawList.add(v0);
                    rawList.add(v1);
                    rawList.add(v2);

                    // Also populate the legacy integer list
                    vertices.add(new Vertex((int) v0[0], (int) v0[1], (int) v0[2]));
                    vertices.add(new Vertex((int) v1[0], (int) v1[1], (int) v1[2]));
                    vertices.add(new Vertex((int) v2[0], (int) v2[1], (int) v2[2]));

                    System.out.println("NEW MESH");
                }
            }
        }

        // Sort the legacy integer list
        vertices.sort((a, b) -> {
            if (a.i != b.i) return Integer.compare(a.i, b.i);
            if (a.j != b.j) return Integer.compare(a.j, b.j);
            return Integer.compare(a.k, b.k);
        });

        // Convert rawList to array — triangles stay in their original winding order
        rawVertices = rawList.toArray(new float[0][]);

        System.out.println("Sorting complete — " + rawVertices.length + " vertices, "
                + (rawVertices.length / 3) + " triangles ready.");

        home.onMeshReady();
    }

    /**
     * Parse a line like "vertex 1.234 -5.678 9.0" into a float[3].
     */
    private float[] parseVertex(String line) {
        String[] parts = line.trim().split("\\s+");
        float[] v = new float[3];
        try { v[0] = Float.parseFloat(parts[1]); } catch (Exception e) { v[0] = 0; }
        try { v[1] = Float.parseFloat(parts[2]); } catch (Exception e) { v[1] = 0; }
        try { v[2] = Float.parseFloat(parts[3]); } catch (Exception e) { v[2] = 0; }
        return v;
    }

    // Kept for compatibility
    public int vertex_i_get(String s) { return (int) Float.parseFloat(s.trim().split("\\s+")[1]); }
    public int vertex_j_get(String s) { return (int) Float.parseFloat(s.trim().split("\\s+")[2]); }
    public int vertex_k_get(String s) { return (int) Float.parseFloat(s.trim().split("\\s+")[3]); }

    public void mesh_maping()   {}
    public void mesh_ordering() {}
    public void give_to_pi()    {}
}