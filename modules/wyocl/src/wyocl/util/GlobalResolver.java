package wyocl.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import wybs.lang.NameID;
import wybs.lang.Path;
import wybs.lang.Path.Entry;
import wybs.util.Pair;
import wyil.lang.Block;
import wyil.lang.Type;
import wyil.lang.WyilFile;
import wyocl.filter.LoopFilter.FunctionResolver;

public class GlobalResolver implements FunctionResolver {
	private final HashMap<Path.ID, WyilFile> wyilFiles = new HashMap<Path.ID, WyilFile>();
	
	public GlobalResolver(List<Pair<Entry<?>, Entry<?>>> delta) throws IOException {
		for(Pair<Path.Entry<?>,Path.Entry<?>> p : delta) {
			@SuppressWarnings("unchecked")
			Path.Entry<WyilFile> wy = (Path.Entry<WyilFile>) p.first();
			wyilFiles.put(wy.id(), wy.read());
		}
	}

	@Override
	public Block blockForFunctionWithName(NameID name, Type.FunctionOrMethod type) {
		WyilFile module = wyilFiles.get(name.module());
		if(module == null) {
			return null;
		}
		
		String mangledName = SymbolUtilities.nameMangle(name.name(), type);
		
		Block block = null;
		for(WyilFile.MethodDeclaration m : module.method(name.name())) {
			if(mangledName.equals(SymbolUtilities.nameMangle(m.name(), m.type()))) {
				// FIXME: what are the multiple cases?
				block = m.cases().get(0).body();
				break;
			}
		}
		
		return block;
	}
}
