import java.util.Arrays;
import java.util.PriorityQueue;
/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = new int[ALPH_SIZE+1];
		Arrays.fill(counts, 0);
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			counts[val] += 1;
		}
		counts[PSEUDO_EOF] = 1;
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for (int c = 0; c < counts.length; c++) {
			if (counts[c] > 0) pq.add(new HuffNode(c,counts[c],null,null));
		}
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0,left.myWeight+right.myWeight,left,right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		String[] encodings = new String[ALPH_SIZE+1];
		codingHelper(root,"",encodings);
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		in.reset();
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			String code = encodings[val];
			out.writeBits(code.length(), Integer.parseInt(code,2));
		}
		String code = encodings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code,2));
		out.close();
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) throw new HuffException("Illegal header starts with "+bits);
		HuffNode root = readTreeHeader(in);
		HuffNode current = root;
		while (true) {
			bits = in.readBits(1);
			if (bits == -1) throw new HuffException("Bad input, no PSEUDO_EOF");
			else {
				if (bits == 0) current = current.myLeft;
				else current = current.myRight;
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) break;
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
		out.close();
	}
	/**
	 *  Recursive helper method for creating a Huffman trie from a pre-order representation
	 *  @param in the raw bit input stream being read
	 *  @return the root node of the Huffman trie
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) throw new HuffException("Illegal bit -1");
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		int value = in.readBits(BITS_PER_WORD+1);
		return new HuffNode(value,0,null,null);
	}
	/**
	 *  Recursive helper meethod for writing an output stream of a pre-order traversal of a Huffman trie
	 *  @param root the HuffNode being processed
	 *  @param out the output stream
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myLeft == null && root.myRight == null) {
			out.writeBits(1,1);
			out.writeBits(BITS_PER_WORD+1,root.myValue);
		}
		else {
			out.writeBits(1,0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
	}
	/**
	 *  Recursive helper method for filling String array of Huffman trie path representations for all values in leaves of the trie
	 *  @param root the HuffNode being processed
	 *  @param path the path to the HuffNode root
	 *  @param encodings the String array storing all paths
	 */
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			return;
		}
		codingHelper(root.myLeft, path + "0", encodings);
		codingHelper(root.myRight, path + "1", encodings);
	}
}