import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import wyautl.core.Automaton;
import wyautl.io.PrettyAutomataWriter;

public final class Main {
	private Main() {} // avoid instantiation of this class

	public static void main(String[] args) {
		final BufferedReader input =
			new BufferedReader(new InputStreamReader(System.in));
		try {
			System.out.println("Welcome!\n");			
			while(true) {				
				System.out.print("> ");
				String text = input.readLine();

				// commands goes here
				if(text.equals("help")) {
					printHelp();
				} else if(text.equals("exit")) {
					System.exit(0);
				} else {
					reduce(text);
				}
			}
		} catch(IOException e) {
			System.err.println("I/O Error - " + e.getMessage());
		}
	}

	private static void reduce(String text) {
		try {										
			Parser parser = new Parser(text);
			Automaton automaton = new Automaton();
			int root = parser.parse(automaton);
			automaton.setRoot(0, root);
			
			PrettyAutomataWriter writer = new PrettyAutomataWriter(System.out,
					Types.SCHEMA, "Or", "And");
			System.out.println("------------------------------------");
			writer.write(automaton);
			System.out.println();
			writer.flush();
			
			Types.infer(automaton);
			
			System.out.println("\n==> (" + Types.numSteps + " steps)\n");
			writer.write(automaton);
			writer.flush();
			System.out.println("\n");			
		} catch(RuntimeException e) {
			// Catching runtime exceptions is actually rather bad style;
			// see lecture about Exceptions later in the course!
			System.err.println(e.getMessage());
			e.printStackTrace(System.err);
			System.err.println("Type \"help\" for help");
		} catch(IOException e) {
			System.err.println("I/O Exception?");
		}
	}

	private static void printHelp() {
		System.out.println("Calculator commands:");
		System.out.println("\thelp --- access this help page");
		System.out.println("\texit --- quit");
	}
}