package main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;

public class STL_file_minipulation {

	File stl_given;
	BufferedReader br;
	String[] Lines;
	
	ArrayList<Vertex> vertices = new ArrayList<>();
	
	public void stl_get(File stl_get) {
		
		stl_given = stl_get;
				
		try {br = new BufferedReader(new FileReader(stl_given));
		
		} catch (FileNotFoundException e) {e.printStackTrace();}

		stl_decomposition();
		try {br.close();} catch (IOException e) {e.printStackTrace();}
	}
	public void stl_decomposition() {
		if(stl_given.exists()) {
			boolean run_once = true;
			while(run_once) {
			Lines = new String[countLines(stl_given)];
			
			for(int i = 0; i < Lines.length; i++) {
				try {
					Lines[i] = br.readLine();
					Lines[i].strip();
					System.out.println(Lines[i]);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			run_once = false;
			if(!Lines[0].contains("solid")) {
				run_once = true;
				stl_given = binary_to_ASCII(stl_given);
			}
			}
			System.out.println("NEW GROUP");
			vector_making();
		}
	}
    public int countLines(File fileName) {
        int lines = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            while (reader.readLine() != null) {
                lines++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

	public File binary_to_ASCII(File Given) {
		
		return Given;
		
	}
	public void vector_making() {
		int l = 0;
		int c = 0;
		for(int i = 0; i < Lines.length; i++) {
			if(Lines[i].contains("vert")) {
				l++;
				System.out.println(Lines[i]);
				if(l%3 == 0) {
					int i_val = (vertex_i_get(Lines[i-2]));
					int j_val = (vertex_i_get(Lines[i-1]));
					int k_val = (vertex_i_get(Lines[i]));
					
				    vertices.add(new Vertex(i_val, j_val, k_val));

				    vertices.sort((a, b) -> {
				        if (a.i != b.i) return Integer.compare(a.i, b.i);
				        if (a.j != b.j) return Integer.compare(a.j, b.j);
				        return Integer.compare(a.k, b.k);
				    });
				    
					System.out.println("NEW MESH");
				}
			}
			
		}
	}
	public int vertex_i_get(String s) {
	    String[] parts = s.split("\\s+");
	    return Integer.parseInt(parts[1]);
	}

	public int vertex_j_get(String s) {
	    String[] parts = s.split("\\s+");
	    return Integer.parseInt(parts[2]);
	}

	public int vertex_k_get(String s) {
	    String[] parts = s.split("\\s+");
	    return Integer.parseInt(parts[3]);
	}
	public void mesh_maping() {
		
	}
	public void mesh_ordering() {
		
	}
	public void give_to_pi() {
		
	}
}
