package wyocl.filter;

import wyil.lang.Type;
import wyil.lang.Type.EffectiveList;
import wyil.lang.Type.EffectiveTuple;

public class SupportedTypes {

	public static boolean includes(Type type) {
		if(type instanceof Type.Leaf) {
			return true;
		}
		if(type instanceof EffectiveList && includes(((EffectiveList) type).element())) {
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
}
