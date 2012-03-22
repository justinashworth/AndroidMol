// Justin Ashworth 2009

package com.ja.mol;

import java.io.*;
import java.nio.*;
import java.util.regex.*;
import java.util.Enumeration;
import java.util.Vector;

import javax.microedition.khronos.opengles.GL10;

//import android.util.Log;

class Atom {
	public Atom() {
		name = "EMPTY";
		chain = '_';
		x = 0.0f; y = 0.0f; z = 0.0f;
	}
	public String name;
	public char chain;
	public float x;
	public float y;
	public float z;
}

class Bond {
	public Bond( int index1, int index2 ) {
		atom1_index = index1;
		atom2_index = index2;
	}
	public int atom1_index;
	public int atom2_index;
}

class Molecule {

	public Molecule() {
		// center_ deliberately not initialized here: see center() method
	}
	
	public Molecule(DataInputStream ds) {
		atoms_ = new Vector<Atom>();
		bonds_ = new Vector<Bond>();
		loadFromStream(ds);
		makeBonds();
	}
	
	public void loadFromStream(DataInputStream ds) {
		Pattern atompattern = Pattern.compile("ATOM +[0-9]+ +([A-Z0-9]+).+?([0-9-]+\\.[0-9]+) +([0-9-]+\\.[0-9]+) +([0-9-]+\\.[0-9]+)");

		try {
		String line;
		while ( ( line = ds.readLine() ) != null ) {
			//Log.d("Molecule",line);
			// match ATOM pattern and extract atom name,x,y,z
			Matcher match = atompattern.matcher(line);
			if ( ! match.find() ) { continue; }
			//Log.d("Molecule","match");
			Atom atom = new Atom();
			atom.name = match.group(1);
			atom.x = Float.valueOf(match.group(2));
			atom.y = Float.valueOf(match.group(3));
			atom.z = Float.valueOf(match.group(4));
			//Log.d("Molecule", printf("%s,%f,%f,%f", atom.name, atom.x, atom.y, atom.z) );
			atoms_.add(atom);
		}
		} catch ( IOException e ) { e.printStackTrace(); }
	}

	// "slow," but should only need to happen once at load time as long as structure is static
	private void makeBonds() {
		int natoms = atoms_.size();
		//Log.i("Molecule",natoms + " atoms");
		for ( int i = 0; i < natoms; ++i ) {
			Atom atomi = atoms_.elementAt(i);
			for ( int j = i+1; j < natoms; ++j ) {
				Atom atomj = atoms_.elementAt(j);
				float dis2 =
					(atomj.x - atomi.x) * (atomj.x - atomi.x) +
					(atomj.y - atomi.y) * (atomj.y - atomi.y) +
					(atomj.z - atomi.z) * (atomj.z - atomi.z);
				if ( dis2 < 3.0 ) {
					Bond bond = new Bond(i,j);
					//Log.d("Molecule","Bond " + i + " " + atomi.name + " " + j + " " + atomj.name );
					bonds_.add( bond );
				}
			}
		}
		//Log.i("Molecule",bonds_.size() + " bonds");
	}

	public Atom center() {
		if ( center_ != null ) { return center_; }
		//Log.i("Molecule","center atom being calculated");
		float sum_x = 0.0f;
		float sum_y = 0.0f;
		float sum_z = 0.0f;
		for ( Enumeration<Atom> e = atoms_.elements(); e.hasMoreElements(); ) {
			Atom a = e.nextElement();
			sum_x += a.x;
			sum_y += a.y;
			sum_z += a.z;
		}
		center_ = new Atom();
		center_.x = sum_x / (float) atoms_.size();
		center_.y = sum_y / (float) atoms_.size();
		center_.z = sum_z / (float) atoms_.size();
		//Log.i("Molecule","center is " +
		//		Float.toString(center_.x) + "," +
		//		Float.toString(center_.y) + "," +
		//		Float.toString(center_.z) );
		return center_;
	}

	public Vector<Atom> atoms() { return atoms_; }
	public Vector<Bond> bonds() { return bonds_; }

	private Vector<Atom> atoms_;
	private Vector<Bond> bonds_;
	private Atom center_;
}

class MoleculeGL {

	public MoleculeGL() {
		molecule_ = new Molecule();
		natoms_ = 0;
		nbonds_ = 0;
		line_width_ = LINE_WIDTH_DEFAULT;
	}
	
	public MoleculeGL(DataInputStream ds) {
		line_width_ = LINE_WIDTH_DEFAULT;		
		molecule_ = new Molecule(ds);
		
		Vector<Atom> atoms = molecule_.atoms();
		natoms_ = atoms.size();
		float[] vertices = new float[ natoms_ * 3 ];
		float[] colors = new float[ natoms_ * 4 ];
		int vindex = 0;
		int cindex = 0;
		for ( Enumeration<Atom> e = atoms.elements(); e.hasMoreElements(); ) {
			Atom atom = e.nextElement();
			vertices[vindex++] = atom.x;
			vertices[vindex++] = atom.y;
			vertices[vindex++] = atom.z;
			float[] atom_color = getColor(atom.name);
			colors[cindex++] = atom_color[0];
			colors[cindex++] = atom_color[1];
			colors[cindex++] = atom_color[2];
			colors[cindex++] = atom_color[3];
		}
		
		Vector<Bond> bonds = molecule_.bonds();
		nbonds_ = bonds.size();
		short[] indices = new short[ nbonds_*2 ];
		int bondindex = 0;
		for ( Enumeration<Bond> e = bonds.elements(); e.hasMoreElements(); ) {
			Bond b = e.nextElement();
			int at1 = b.atom1_index;
			int at2 = b.atom2_index;
			//Log.d("Molecule","indices for bond " + bondindex + " " + at1 + " " + at2 );
			indices[bondindex++] = (short)at1;
			indices[bondindex++] = (short)at2;
		}
		
		ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length*4);
		vbb.order(ByteOrder.nativeOrder());
		vertex_buffer_ = vbb.asFloatBuffer();
		vertex_buffer_.put(vertices);
		vertex_buffer_.position(0);

		ByteBuffer cbb = ByteBuffer.allocateDirect(colors.length*4);
		cbb.order(ByteOrder.nativeOrder());
		color_buffer_ = cbb.asFloatBuffer();
		color_buffer_.put(colors);
		color_buffer_.position(0);
		
		ByteBuffer ibb = ByteBuffer.allocateDirect(indices.length*2);
		ibb.order(ByteOrder.nativeOrder());
		index_buffer_ = ibb.asShortBuffer();
		index_buffer_.put(indices);
		index_buffer_.position(0);
	}

	public float[] getColor(String atom_name) {
		if ( atom_name.charAt(0) == 'N') { float[] color = {0,0,1,1}; return color; }
		if ( atom_name.charAt(0) == 'C') { float[] color = {0,1,0,1}; return color; }
		if ( atom_name.charAt(0) == 'O') { float[] color = {1,0,0,1}; return color; }
		if ( atom_name.charAt(0) == 'H') { float[] color = {1,1,1,1}; return color; }
		if ( atom_name.charAt(0) == 'P') { float[] color = {1,0.6f,0,1}; return color; }
		if ( atom_name.charAt(0) == 'S') { float[] color = {1,1,0,1}; return color; }
		if ( Pattern.matches("^[0-9]H", atom_name ) ) { float[] color = {1,1,1,1}; return color; }		
		float[] color = {0,0,0,1}; return color;
	}
	
	public void draw(GL10 gl) {
		gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
		gl.glLineWidth(line_width_);
        gl.glFrontFace(gl.GL_CW);
		gl.glVertexPointer(3, gl.GL_FLOAT, 0, vertex_buffer_ );
		gl.glColorPointer(4, gl.GL_FLOAT, 0, color_buffer_);
		gl.glDrawElements(gl.GL_LINES, nbonds_*2, gl.GL_UNSIGNED_SHORT, index_buffer_ );
	}

	public Atom center() {
		return molecule_.center();
	}
	
	private Molecule molecule_;
	private ShortBuffer index_buffer_;
	private FloatBuffer color_buffer_;
	private FloatBuffer vertex_buffer_;
	int natoms_;
	int nbonds_;
	private static final float LINE_WIDTH_DEFAULT = 3.0f;
	float line_width_;
}
