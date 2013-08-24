package wyocl.filter;

import wyil.lang.Type;
import wyil.lang.Type.EffectiveList;
import wyil.lang.Type.EffectiveTuple;

public class SupportedTypes {

	public static boolean includes(Type type) {
		if(type instanceof Type.Leaf) {
			return true;
		}
		if(isSupportedList(type, 1)) {
			return true;
		}
		else if(type instanceof EffectiveTuple) {
			Type first = ((EffectiveTuple) type).element(0);
			boolean allSame = true;
			for(Type t : ((EffectiveTuple) type).elements()) {
				if(!t.equals(first)) {
					allSame = false;
					break;
				}
			}
			return allSame;
		}
		else {
			return false;
		}
	}

	private static boolean isSupportedList(Type type, int recursionDepth) {
		if(recursionDepth > 3) {
			return false;
		}
		
		return type instanceof EffectiveList && (((EffectiveList) type).element() instanceof Type.Leaf || isSupportedList(((EffectiveList) type).element(), recursionDepth + 1));
	}
}
