package wyocl.filter;

import java.util.List;

import wyocl.ar.CFGNode;
import wyocl.ar.utils.CFGIterator.Entry;

public class OpenCLKernelInvocationDescription {
	public final CFGNode.LoopNode loopNode;
	public final List<Entry> loopBody;
	public final List<Argument> kernelArguments;
	public final List<wyil.lang.Block.Entry> replacementEntries;
	public final int id;

	public OpenCLKernelInvocationDescription(CFGNode.LoopNode loopNode, List<Entry> loopBody, List<Argument> kernelArguments, List<wyil.lang.Block.Entry> replacementEntries, int id) {
		this.loopNode = loopNode;
		this.loopBody = loopBody;
		this.kernelArguments = kernelArguments;
		this.replacementEntries = replacementEntries;
		this.id = id;
	}
}
