package wyocl.filter;

import wyil.lang.Type;
import wyil.lang.Type.EffectiveCollection;
import wyil.lang.Type.EffectiveList;
import wyil.lang.Type.EffectiveTuple;

public class SupportedTypes {

	public static boolean includes(Type type) {
		if(type instanceof Type.Leaf) {
			return true;
		}
		else if(type instanceof EffectiveCollection && includes(((EffectiveCollection) type).element())) {
			if(type instanceof EffectiveList) {
				return true;
			}
			else if(type instanceof EffectiveTuple) {
				return true;
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}
}
