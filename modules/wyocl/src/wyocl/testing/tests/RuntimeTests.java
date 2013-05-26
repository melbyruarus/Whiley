package wyocl.testing.tests;

import org.junit.Test;

import wyocl.testing.TestHarness;

public class RuntimeTests extends TestHarness {
	public RuntimeTests() {
		super("../../tests/gpgpu/valid","../../tests/gpgpu/valid","sysout");
	}

	@Test public void SwitchIfWhileForAll() { runTest("SwitchIfWhileForAll"); }
	@Test public void FunctionCall() { runTest("FunctionCall"); }
	@Test public void While() { runTest("While"); }
	@Test public void Forall() { runTest("Forall"); }
	@Test public void Switch() { runTest("Switch"); }
	@Test public void IfElse() { runTest("IfElse"); }
	@Test public void Arithmatic() { runTest("Arithmatic"); }
	@Test public void Remainder() { runTest("Remainder"); }
	@Test public void Logic() { runTest("Logic"); }
	@Test public void For_Valid_2_RuntimeTest() { runTest("For_Valid_2"); }
}
