package wyocl.filter;

import java.util.ArrayList;

import wybs.lang.Path.ID;
import wyil.lang.Block;
import wyil.lang.Code;

public class LoopFilter {
	/**
	 * The endLabel is used to determine when we're within a for loop being
	 * translated in OpenCL. If this is <code>null</code> then we're *not*
	 * within a for loop. Otherwise, we are.
	 */
	private String endLabel;
	/**
	 * A unique path which can be used to identify the module currently
	 * being filtered.
	 */
	private final String modulePath;
	/**
	 * A list of entries which have been filtered out and are awaiting processing
	 */
	private ArrayList<Block.Entry> filteredEntries;
	private ArrayList<Block.Entry> replacementEntries;
	
	public LoopFilter(ID id) {
		StringBuilder sb = new StringBuilder();
		for(String s : id) {
			sb.append('_');
			sb.append(s.replaceAll("[^a-z,A-Z]", "_"));
		}
		modulePath = sb.toString();
	}

	public FilterAction filter(Block.Entry entry) {
		// TODO: we're going to have to do something like store all byte codes for the scope
		// so that we can determine what dependancies the loop has and what arguments it is
		// going to need
		
		Code code = entry.code;
		
		if(endLabel == null) {
			if(code instanceof Code.ForAll) {
				Code.ForAll forall = (Code.ForAll)code;
				endLabel = forall.target;
				filteredEntries = new ArrayList<Block.Entry>();
				filteredEntries.add(entry);
				
				return FilterAction.SKIP;
			}
			else {
				return FilterAction.DEFAULT;
			}
		}
		else {
			if(code instanceof Code.Label) {
				Code.Label label = (Code.Label)code;
				if(label.label.equals(endLabel)) {
					filteredEntries.add(entry);
					endLabel = null;
					
					processFilteredBytecodes();
					
					return FilterAction.FILTER_RESULTS_READY;
				}
			}
			
			filteredEntries.add(entry);
			
			return FilterAction.SKIP;
		}
	}

	/**
	 * This method goes through the contents of filteredEntries once the loop
	 * has been finished, and determines what entries should be filtered out
	 * for OpenCL, which entries should be left intact, and what entries should
	 * be added if any.
	 * 
	 * This method outputs the entries which should stay in the source or be added
	 * to replacementEntries, and the entries which have been filtered out will be
	 * stored in filteredEntries (this includes the loop start and end label).
	 */
	private void processFilteredBytecodes() {
		// TODO: determine if the loop can be parallalized, and if there
		// are nested loops figure out which level of the loop should be
		// parallalized
		
		replacementEntries = new ArrayList<Block.Entry>();
	}
	
	/**
	 * Get the list of entries which should be put into the place of the
	 * entries which have been filtered out.
	 * 
	 * NOTE: make sure to avoid the possibility of the filter being re-run
	 * on the output of this function. So if you loop over the contents of
	 * this array make sure the function that processes the entries does not call
	 * any filters.
	 * 
	 * @return
	 */
	public ArrayList<Block.Entry> getReplacementEntries() {
		return replacementEntries;
	}
	
	public ArrayList<Block.Entry> getFilteredEntries() {
		return filteredEntries;
	}
	
	public boolean wasLoopFiltered() {
		return filteredEntries.size() > 0;
	}
}
