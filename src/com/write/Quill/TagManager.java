package com.write.Quill;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Collections;

import android.util.Log;

public class TagManager {
	private static final String TAG = "TagManager";
	private static TagManager tagManager = new TagManager(); 
	private static LinkedList<Tag> tags = new LinkedList<Tag>();
	
	public class Tag {
		protected String name;
		protected int count = 0;
		protected boolean autogenerated = false;
		protected long ctime;
		
		public Tag(String tagName) {
			name = new String(tagName);
			ctime = System.currentTimeMillis();
		}
		
		public String toString() {
			return name;
		}
		
		public void write_to_stream(DataOutputStream out) throws IOException {
			out.writeInt(1);  // protocol #1
			out.writeUTF(name);
			out.writeBoolean(autogenerated);
			out.writeLong(ctime);
			out.writeLong(0); // reserved
		}

		public Tag(DataInputStream in) throws IOException {
			int version = in.readInt();
			if (version != 1)
				throw new IOException("Unknown version!");
			name = in.readUTF();
			autogenerated = in.readBoolean();
			ctime = in.readLong();
			in.readLong(); // reserved
		}
	}
	
	public class TagSet {
		protected LinkedList<Tag> tags = new LinkedList<Tag>();
		
		public TagSet() {}
		
		public void close() {
			ListIterator<Tag> iter = tags.listIterator();
			while (iter.hasNext()) {
				Tag t = iter.next();
				t.count -= 1;
			}
			tags.clear();
		}
		
		public void set(TagSet ts) {
			close();
			tags.addAll(ts.tags);
			ListIterator<Tag> iter = tags.listIterator();
			while (iter.hasNext()) {
				Tag t = iter.next();
				t.count += 1;
			}		
		}
		
		public TagSet copy() {
			TagSet ts = new TagSet();
			ts.set(this);
			return ts;
		}
		
		public boolean contains(Tag tag) {
			return tags.contains(tag);
		}
		
		public boolean add(Tag tag) {
			if (!tags.contains(tag)) {
				tags.add(tag);
				tag.count += 1;
				return true;
			}
			return false;
		}
		
		public boolean remove(Tag tag) {
			boolean rc = tags.remove(tag);
			if (rc) tag.count -= 1;
			return rc;
		}
		
		public ListIterator<Tag> tagIterator() {
			return tags.listIterator();
		}
		
		public LinkedList<Tag> allTags() {
			return tagManager.tags;
		}
		
		public int size() {
			return tags.size();
		}
		
		public void write_to_stream(DataOutputStream out) throws IOException {
			out.writeInt(1);  // protocol #1
			out.writeInt(tags.size());
			Log.d(TAG, "TagSet wrote n = "+tags.size());
			ListIterator<Tag> iter = tags.listIterator();
			while (iter.hasNext()) {
				Tag t = iter.next();
				t.write_to_stream(out);
			}
			out.writeInt(0); // reserved1
			out.writeInt(0); // reserved2
		}

		public TagSet(DataInputStream in) throws IOException {
			int version = in.readInt();
			if (version != 1)
				throw new IOException("Unknown version!");
			int n = in.readInt();
			Log.d(TAG, "TagSet read n = "+n);
			for (int i=0; i<n; i++) {
				Tag t = new Tag(in);
				Tag found = findTag(t.name);
				if (found == null) {
					allTags().add(t);
					add(t);
				} else
					add(found);
			}
			in.readInt();  // reserved1
			in.readInt();  // reserved2
		}
	}
	
	
	private TagManager() {}
	
	public static TagManager getTagManager() {
		return tagManager;
	}
	
	public static TagSet newTagSet() {
		return tagManager.new TagSet();
	}
	
	public static TagSet loadTagSet(DataInputStream in) throws IOException {
		return tagManager.new TagSet(in);
	}
	
	public Tag findTag(String name) {
		ListIterator<Tag> iter = tags.listIterator();
		while (iter.hasNext()) {
			Tag t = iter.next();
			if (name.equalsIgnoreCase(t.name))
				return t;
		}
		return null;
	}

	public Tag makeTag(String name) {
		Tag t = findTag(name);
		if (t == null)
			t = new Tag(name);
		tags.add(t);
		Log.d(TAG, "Created new tag "+name+" "+tagManager.tags.size());
		return t;
	}
	
	public Tag get(int position) {
		return tags.get(position);
	}
	
	public void sort() {
		Collections.sort(tags, new CompareCount());
	}
	
	public class CompareCount implements Comparator<Tag> {
		@Override
		public int compare(Tag t0, Tag t1) {
			int x = t0.count;
			int y = t1.count;
			if(x > y) {
				return -1;
			} else if(x == y) {
				return 0;
			} else {
				return 1;
			}
		}
	}

}
