package name.vbraun.view.write;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.write.Quill.data.TagManager;
import com.write.Quill.data.TagManager.TagSet;

import junit.framework.Assert;

import name.vbraun.view.write.Graphics.Tool;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;


public class Page {
	private static final String TAG = "Page";
	private final Background background = new Background();
	
	private TagManager tagManager;
	
	// persistent data
	protected UUID uuid;  // unique identifier
	public final LinkedList<GraphicsImage> images = new LinkedList<GraphicsImage>();
	public final LinkedList<Stroke> strokes = new LinkedList<Stroke>();
	// lineArt contains straight lines, arrows, etc.
	public final LinkedList<GraphicsControlpoint> lineArt = new LinkedList<GraphicsControlpoint>();
	public final TagManager.TagSet tags;
	protected float aspect_ratio = AspectRatio.Table[0].ratio;
	protected boolean is_readonly = false;
	protected Paper.Type paper_type = Paper.Type.RULED;
	protected TextBox backgroundText = new TextBox(Tool.TEXT);
	
	// coordinate transformation Stroke -> screen
	protected Transformation transformation = new Transformation();
	
	protected boolean is_modified = false;

	private final RectF mRectF = new RectF();
	
	public TagSet getTags() {
		return tags;
	}
	
	public UUID getUUID() {
		return uuid;
	}
	
	public boolean is_empty() {
		return strokes.isEmpty() && lineArt.isEmpty();
	}
	
	public void touch() {
		is_modified = true;
	}
	
	public boolean isModified() {
		return is_modified;
	}

	public float getAspectRatio() {
		return aspect_ratio;
	}
	
	public Paper.Type getPaperType() {
		return paper_type;
	}
	
	public boolean isReadonly() {
		return is_readonly;
	}
	
	public void setReadonly(boolean ro) {
		is_readonly = ro;
		is_modified = true;
	}
	
	public void setPaperType(Paper.Type type) {
		paper_type = type;
		is_modified = true;
		background.setPaperType(paper_type);
	}
	
	public void setAspectRatio(float aspect) {
		aspect_ratio = aspect;
		is_modified = true;
		background.setAspectRatio(aspect_ratio);
	}
	
	protected void setTransform(float dx, float dy, float s) {
		transformation.offset_x = dx;
		transformation.offset_y = dy;
		transformation.scale = s;
	    for (Stroke stroke : strokes)
	    	stroke.setTransform(transformation);
	    for (GraphicsControlpoint line : lineArt)
	    	line.setTransform(transformation);
	}
	
	protected void setTransform(Transformation newTrans) {
		transformation.offset_x = newTrans.offset_x;
		transformation.offset_y = newTrans.offset_y;
		transformation.scale = newTrans.scale;
	    for (Stroke stroke : strokes)
	    	stroke.setTransform(transformation);
	    for (GraphicsControlpoint line : lineArt)
	    	line.setTransform(transformation);
	}

	// set transform but clamp the offset such that the page stays visible
	protected void setTransform(float dx, float dy, float s, Canvas canvas) {
		float W = canvas.getWidth();
		float H = canvas.getHeight();
		dx = Math.min(dx, 2*W/3);
		dx = Math.max(dx,   W/3 - s*aspect_ratio);
		dy = Math.min(dy, 2*H/3);
		dy = Math.max(dy,   H/3 - s);
		setTransform(dx, dy, s);
	}
	
	protected void setTransform(Transformation newTrans, Canvas canvas) {
		setTransform(newTrans.offset_x, newTrans.offset_y, newTrans.scale, canvas);
	}

	
	protected Transformation getTransform() {
		return transformation;
	}
	
	public void addStroke(Stroke s) {
		strokes.add(s);
		s.setTransform(getTransform());
		is_modified = true;
	}
	
	public void removeStroke(Stroke s) {
		strokes.remove(s);
		is_modified = true;
	}

	public void addLine(GraphicsLine line) {
		lineArt.add(line);
		line.setTransform(getTransform());
		is_modified = true;
	}
	
	public void removeLine(GraphicsLine line) {
		lineArt.remove(line);
		is_modified = true;
	}

	public void addImage(GraphicsImage image) {
		images.add(image);
		image.setTransform(getTransform());
		is_modified = true;
	}
	
	public void removeImage(GraphicsImage image) {
		images.remove(image);
		is_modified = true;
	}

	public void draw(Canvas canvas, RectF bounding_box) {
		draw(canvas, bounding_box, true);
	}
	
	public void draw(Canvas canvas, RectF bounding_box, boolean drawBackgroundLines) {
		canvas.save();
		canvas.clipRect(bounding_box);
		if (drawBackgroundLines)
			background.draw(canvas, bounding_box, transformation);
		else
			background.drawEmptyBackground(canvas, bounding_box, transformation);
		backgroundText.draw(canvas, bounding_box);
		for (GraphicsImage img: images) {
		   	if (!canvas.quickReject(img.getBoundingBox(), Canvas.EdgeType.AA))
		   		img.draw(canvas, bounding_box);
	    }
		for (Stroke s: strokes) {
		   	if (!canvas.quickReject(s.getBoundingBox(), Canvas.EdgeType.AA))
		   		s.draw(canvas, bounding_box);
	    }
		for (GraphicsControlpoint graphics: lineArt) {
		   	if (!canvas.quickReject(graphics.getBoundingBox(), Canvas.EdgeType.AA))
		   		graphics.draw(canvas, bounding_box);
	    }
		canvas.restore();
	}
	
	public Stroke findStrokeAt(float x, float y, float radius) {
	    ListIterator<Stroke> siter = strokes.listIterator();
		while(siter.hasNext()) {	
			Stroke s = siter.next();	    	
			if (!s.getBoundingBox().contains(x,y)) continue;
			if (s.distance(x,y) < radius)
				return s;
		}
		return null;
	}
	
	public void draw(Canvas canvas) {
		draw(canvas, true);
	}

	public void draw(Canvas canvas, boolean background) {
		mRectF.set(0,0,canvas.getWidth(), canvas.getHeight());
		draw(canvas, mRectF, background);
	}
	
	
	public void writeToStream(DataOutputStream out) throws IOException {
		out.writeInt(5);  // protocol version number
		out.writeUTF(uuid.toString());
		tags.write_to_stream(out);
		out.writeInt(paper_type.ordinal());
		out.writeInt(0); // reserved1
		out.writeInt(0); // reserved2
		out.writeBoolean(is_readonly);
		out.writeFloat(aspect_ratio);
		
		out.writeInt(strokes.size());
		for (Stroke stroke : strokes)
			stroke.writeToStream(out);
		
		out.writeInt(lineArt.size());
		for (GraphicsControlpoint line : lineArt)
			line.writeToStream(out);
		
		out.writeInt(0); // number of images
		out.writeInt(0); // number of text boxes
	}
	
	public Page(TagManager tagMgr) {
		uuid=UUID.randomUUID();
		tagManager = tagMgr;
		tags = tagManager.newTagSet();
		setPaperType(paper_type);
		setAspectRatio(aspect_ratio);
		setTransform(transformation);
		is_modified = true;
	}

	public Page(Page template) {
		uuid=UUID.randomUUID();
		tagManager = template.tagManager;
		tags = template.tags.copy();
		setPaperType(template.paper_type);
		setAspectRatio(template.aspect_ratio);
		setTransform(template.transformation);
		is_modified = true;
	}
	

	public Page(DataInputStream in, TagManager tagMgr) throws IOException {
		tagManager = tagMgr;
		int version = in.readInt();
		if (version == 1) {
			uuid = UUID.randomUUID();
			tags = tagManager.newTagSet();
			paper_type = Paper.Type.EMPTY;
		} else if (version == 2) {
			uuid = UUID.randomUUID();
			tags = tagManager.newTagSet();
			paper_type = Paper.Type.values()[in.readInt()];
			in.readInt();
			in.readInt();
		} else if (version == 3) {
			uuid = UUID.randomUUID();
			tags = tagManager.loadTagSet(in);
			paper_type = Paper.Type.values()[in.readInt()];
			in.readInt();
			in.readInt();
		} else if (version == 4 || version == 5) {
			uuid = UUID.fromString(in.readUTF());
			tags = tagManager.loadTagSet(in);
			paper_type = Paper.Type.values()[in.readInt()];
			in.readInt();
			in.readInt();
		} else 
			throw new IOException("Unknown version!");
		is_readonly = in.readBoolean();
		aspect_ratio = in.readFloat();
		
		int nStrokes = in.readInt();
		for (int i=0; i<nStrokes; i++) {
			strokes.add(new Stroke(in));
		}
		
		if (version >= 5) {
			int nLines = in.readInt();
			for (int i=0; i<nLines; i++) {
				lineArt.add(new GraphicsLine(in));
			}
			int nImages= in.readInt(); // TODO
			int nText = in.readInt();  // TODO
		}
		
		background.setAspectRatio(aspect_ratio);
		background.setPaperType(paper_type);
	}
	
	public Bitmap renderBitmap(int width, int height, boolean background) {
		Transformation backup = getTransform().copy();
		float scale = Math.min(height, width/aspect_ratio);
		setTransform(0, 0, scale);
		int actual_width  = (int)Math.rint(scale*aspect_ratio);
		int actual_height = (int)Math.rint(scale);
		Bitmap bitmap = Bitmap.createBitmap
			(actual_width, actual_height, Config.ARGB_8888);
		Canvas c = new Canvas(bitmap);
		draw(c, background);
		setTransform(backup);
		return bitmap;
	}
}


