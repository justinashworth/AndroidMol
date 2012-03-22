// Justin Ashworth 2009
// adapted from android example "TouchRotateActivity.java"

package com.ja.mol;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.regex.Pattern;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
//import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.app.Dialog;

class mDialog extends Dialog {
	public mDialog(Context context, int theme) {
		super(context,theme);
	}
	@Override
	public boolean onTouchEvent( MotionEvent event ) {
		dismiss();
		return true;
	}
}

public class AndMolActivity extends Activity {

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
	    Dialog dialog = new mDialog(this, R.style.SplashScreen);
	    dialog.setContentView(R.layout.splash);
	    dialog.show();
	    setContentView(R.layout.main);
		
		createView();
	}

	public void createView() {
		glview_ = new MoleculeView(this);
		setContentView(glview_);
		glview_.requestFocus();
		glview_.setFocusableInTouchMode(true);
		loadDefault(R.raw.dna);
		//loadDefault(R.raw.x2dgc);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		glview_.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		glview_.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0,DEMO_ID,0,"Demo");
		menu.add(0,LOAD_ID,0,"Load");
		menu.add(0,ZOOM_ID,0,"Zoom");
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case DEMO_ID:
				loadDefault(R.raw.x2dgc);
				return true;
			case LOAD_ID:
				Intent intent = new Intent(Intent.ACTION_PICK);
				Uri startdir = Uri.fromFile(new File("/sdcard"));
				intent.setDataAndType(startdir,"vnd.android.cursor.dir/*");
				startActivityForResult(intent,1);
				return true;
			case ZOOM_ID:
				glview_.zoom();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	public void loadDefault(int id) {
		// look up resource URI, open file stream, send to PDB reader
		//Uri uri = Uri.parse("android.resource://" + getPackageName() + '/' + R.raw.x2dgc );
		//Uri uri = Uri.parse("android.resource://" + getPackageName() + "/raw/x2dgc.pdb" );
		//String path = uri.toString();
		//Log.d("Mol","path: " + path);
		DataInputStream ds = new DataInputStream( getResources().openRawResource(id) );
		glview_.loadModel(ds);
	}
	
	public void onActivityResult(int reqcode, int rescode, Intent intent) {
		if (reqcode == 1) {
			if (rescode == RESULT_OK) {
				Uri uri = intent.getData();
				//Log.d("Mol","Pick: " + uri);
				if ( uri != null ) {
					String picked_path = uri.toString();
					if (picked_path.toLowerCase().startsWith("file://")) {
						picked_path = Pattern.compile("file:").matcher(picked_path).replaceAll("");
						//Log.d("Mol","path: " + picked_path);
						try {
							DataInputStream ds = new DataInputStream( new FileInputStream(picked_path) );
							glview_.loadModel(ds);
						} catch ( IOException e ) { e.printStackTrace(); }
					}
				}
			}
		}
	}
	
	private static final int DEMO_ID = Menu.FIRST;
	private static final int LOAD_ID = Menu.FIRST+1;
	private static final int ZOOM_ID = Menu.FIRST+2;
	private MoleculeView glview_;
}

class MoleculeRenderer implements GLSurfaceView.Renderer {
	
	public MoleculeRenderer() {
		view_distance_ = DEFAULT_VIEW_DISTANCE;
		center_ = new Atom();
	}
	
	public void loadModel(DataInputStream ds) {
		molecule_ = new MoleculeGL(ds);
		zoom();
	}
	
	public void onDrawFrame(GL10 gl) {
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
		
		if ( molecule_ == null ) return;
		
		gl.glMatrixMode(GL10.GL_MODELVIEW);
		gl.glLoadIdentity();
		gl.glTranslatef(0, 0, view_distance_ );
		gl.glRotatef(x_angle_, 0, 1, 0);
		gl.glRotatef(y_angle_, 1, 0, 0);
		gl.glTranslatef(-1*center_.x, -1*center_.y, -1*center_.z);
		molecule_.draw(gl);
	}

	public int[] getConfigSpec() {
		// We want a depth buffer, don't care about the
		// details of the color buffer.
		int[] configSpec = {
				EGL10.EGL_DEPTH_SIZE,   16,
				EGL10.EGL_NONE
		};
		return configSpec;
	}

	public void onSurfaceChanged(GL10 gl, int width, int height) {
		 gl.glViewport(0, 0, width, height);
		 /*
		  * Set our projection matrix. This doesn't have to be done
		  * each time we draw, but usually a new projection needs to
		  * be set when the viewport is resized.
		  */
		 float ratio = (float) width / height;
		 gl.glMatrixMode(GL10.GL_PROJECTION);
		 gl.glLoadIdentity();
		 //gl.glFrustumf(-ratio, ratio, -1, 1, 1, 500);
		 gl.glFrustumf(-ratio,ratio,1,-1,5,500);
		 //gl.glOrthof(-ratio*10,ratio*10,10,-10, 1, 500);
		 //gl.glOrthof(-ratio, ratio, -1, 1, 10, 500);
	}

	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		gl.glEnable(GL10.GL_DITHER);
		gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);
		gl.glEnable(GL10.GL_LINE_SMOOTH);
		gl.glEnable(GL10.GL_BLEND);
		gl.glHint(GL10.GL_LINE_SMOOTH_HINT, GL10.GL_NICEST);
		gl.glBlendFunc(GL10.GL_SRC_COLOR, GL10.GL_ONE_MINUS_SRC_COLOR);
		//gl.glHint(GL10.GL_FOG_HINT, GL10.GL_NICEST);	 
		gl.glClearColor(0,0,0,1);
		gl.glEnable(GL10.GL_CULL_FACE);
		gl.glShadeModel(GL10.GL_SMOOTH);
		gl.glEnable(GL10.GL_DEPTH_TEST);
	}
	
	public void zoom() {
		view_distance_ = DEFAULT_VIEW_DISTANCE;
		recenter();
	}
	
	public void recenter() {
		center_ = molecule_.center();
	}
	
	public void center( Atom c ) { center_ = c; }
	public Atom center() { return center_; }
	
	private static final float DEFAULT_VIEW_DISTANCE = -100.0f;
	private MoleculeGL molecule_;
	public float view_distance_;
	public float x_angle_;
	public float y_angle_;
	private Atom center_;
}

class MoleculeView extends GLSurfaceView {

	public MoleculeView(Context context) {
		super(context);
		renderer_ = new MoleculeRenderer();
		setRenderer(renderer_);
		setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	}

	public void loadModel(DataInputStream ds) {
		renderer_.loadModel(ds);
		zoom();
	}

	public void zoom() {
		renderer_.zoom();
		requestRender();
	}
	
	@Override public boolean onTouchEvent(MotionEvent e) {
		int action = e.getAction();
		int pointercount = e.getPointerCount();
		// note Droid only reports up to two pointers
		//Log.d("Mol","pointercount " + pointercount);
		if ( action == MotionEvent.ACTION_DOWN ) {
				//Log.d("Mol","ACTION_DOWN");
				x_rot_previous_ = e.getX();
				y_rot_previous_ = e.getY();
				x_trans_previous_ = e.getX();
				y_trans_previous_ = e.getY();
		}
		if ( (action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_DOWN ) {
			int id = (action & MotionEvent.ACTION_POINTER_ID_MASK) >> MotionEvent.ACTION_POINTER_ID_SHIFT;
			//Log.d("Mol","ACTION_POINTER_DOWN id " + id );
			float d = multiTouchDistance(e);
			if ( d > 0.0f ) multitouch_start_distance_ = d;
		}
		if ( action == MotionEvent.ACTION_MOVE ) {
			float x = e.getX();
			float y = e.getY();
			if ( pointercount > 1 ) {
				// multi-touch zoom
				float d = multiTouchDistance(e);
				//Log.d("Mol","d " + Float.toString(d));
				if ( d > 0.0f ) {
					renderer_.view_distance_ *= multitouch_start_distance_/d;
					multitouch_start_distance_ = d;
				}
				/*
				// also translate in plane while multi-touching?
				// crappy/buggy...
				float dx = x - x_trans_previous_;
				float dy = y - y_trans_previous_;
				Atom c = renderer_.center();
				c.x += dx * TOUCH_TRANS_FACTOR;
				c.y += dy * TOUCH_TRANS_FACTOR;
				renderer_.center(c);
				x_trans_previous_ = x;
				y_trans_previous_ = y;
				*/
			} else {
				float dx = x - x_rot_previous_;
				float dy = y - y_rot_previous_;
				renderer_.x_angle_ += dx * TOUCH_ROT_FACTOR;
				renderer_.y_angle_ += dy * TOUCH_ROT_FACTOR;
				x_rot_previous_ = x;
				y_rot_previous_ = y;
			}
			requestRender();
		}
		if ( (action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_UP ) {
			int id = (action & MotionEvent.ACTION_POINTER_ID_MASK) >> MotionEvent.ACTION_POINTER_ID_SHIFT;
			//Log.d("Mol","ACTION_POINTER_UP id " + id );
		}
		if ( action == MotionEvent.ACTION_UP ) {
			//Log.d("Mol","ACTION_UP");
		}
		return true;
	}

	public float multiTouchDistance(MotionEvent e) {
		if ( e.getPointerCount() < 2 ) return 0.0f;
		float x1 = e.getX( e.getPointerId(0) );
		float y1 = e.getY( e.getPointerId(0) );
		float x2 = e.getX( e.getPointerId(1) );
		float y2 = e.getY( e.getPointerId(1) );
		float dx = x2-x1;
		float dy = y2-y1;
		return (float) Math.sqrt( dx*dx + dy*dy );
	}
	
	private final float TOUCH_ROT_FACTOR = 180.0f / 320;
//	private final float TOUCH_TRANS_FACTOR = 0.1f;
	private MoleculeRenderer renderer_;
	private float x_rot_previous_ = 0;
	private float y_rot_previous_ = 0;
	private float x_trans_previous_ = 0;
	private float y_trans_previous_ = 0;
	private float multitouch_start_distance_ = 0;
	
}

