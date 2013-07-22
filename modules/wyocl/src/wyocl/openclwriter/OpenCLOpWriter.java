package wyocl.openclwriter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import wybs.util.Pair;
import wyil.lang.Code;
import wyil.lang.Code.Comparator;
import wyil.lang.Code.UnArithKind;
import wyil.lang.Constant;
import wyil.lang.Type;
import wyil.lang.Type.Leaf;
import wyocl.ar.Bytecode;
import wyocl.ar.Bytecode.Assign;
import wyocl.ar.Bytecode.Binary;
import wyocl.ar.Bytecode.ComparisonBasedJump;
import wyocl.ar.Bytecode.ConstLoad;
import wyocl.ar.Bytecode.Convert;
import wyocl.ar.Bytecode.ForAll;
import wyocl.ar.Bytecode.GPUSupportedBytecode;
import wyocl.ar.Bytecode.Invoke;
import wyocl.ar.Bytecode.Label;
import wyocl.ar.Bytecode.LengthOf;
import wyocl.ar.Bytecode.Load;
import wyocl.ar.Bytecode.LoopEnd;
import wyocl.ar.Bytecode.Move;
import wyocl.ar.Bytecode.Not;
import wyocl.ar.Bytecode.Return;
import wyocl.ar.Bytecode.Switch;
import wyocl.ar.Bytecode.TupleLoad;
import wyocl.ar.Bytecode.Unary;
import wyocl.ar.Bytecode.UnconditionalJump;
import wyocl.ar.Bytecode.Update;
import wyocl.ar.Bytecode.While;
import wyocl.ar.CFGNode;
import wyocl.ar.CFGNode.ConditionalJumpNode;
import wyocl.ar.CFGNode.ForAllLoopNode;
import wyocl.ar.CFGNode.ForLoopNode;
import wyocl.ar.CFGNode.LoopBreakNode;
import wyocl.ar.CFGNode.LoopEndNode;
import wyocl.ar.CFGNode.MultiConditionalJumpNode;
import wyocl.ar.CFGNode.PassThroughNode;
import wyocl.ar.CFGNode.ReturnNode;
import wyocl.ar.CFGNode.UnresolvedTargetNode;
import wyocl.ar.CFGNode.VanillaCFGNode;
import wyocl.ar.CFGNode.WhileLoopNode;
import wyocl.ar.DFGNode;
import wyocl.ar.utils.BytecodeInstanceSwitch;
import wyocl.ar.utils.CFGInstanceSwitch;
import wyocl.ar.utils.CFGIterator;
import wyocl.ar.utils.CFGIterator.Entry;
import wyocl.filter.Argument;
import wyocl.openclwriter.OpenCLTypeWriter.ExpressionWriter;

public class OpenCLOpWriter {
	private static final boolean WRITE_BYTECODES_TO_COMMENTS = true;
	private static final String LABEL_PREFIX = "label";
	private static final boolean DEBUG = false;

	public final OpenCLTypeWriter typeWriter = new OpenCLTypeWriter();
	private final FunctionInvokeTranslator functionTranslator;

	public interface FunctionInvokeTranslator {
		/**
		 * The OpWriter will call this method when it encounters a invoke bytecode
		 * and requires a translation of the function name from WYIL to OpenCL.
		 * It is the responsibility of the implementer of this method to ensure
		 * that a function with a matching name and type signature exists in the OpenCL
		 * file. The appropriate place to write these called functions is when this
		 * method is called.
		 *
		 * @param b The bytecode of the function invoke
		 * @return The name of the translated function
		 */
		public String translateFunctionName(Bytecode b);
	}

	public static void writeRuntime(PrintWriter writer) { // TODO: this should be loaded in from a file or something
		writer.println();
		writer.println("// Beginning whiley GPGPU runtime library");
		writer.println("// Ending whiley GPGPU runtime library");
		writer.println();
	}

	public OpenCLOpWriter(FunctionInvokeTranslator functionTranslator) {
		this.functionTranslator = functionTranslator;
	}

	public void writeFunctionBody(List<Entry> entries, PrintWriter bodyWriter) {
		StringWriter sw = new StringWriter();
		PrintWriter writer = new PrintWriter(sw);

		startTask(entries, bodyWriter, writer, sw);
	}

	public void writeLoopBodyAsKernel(CFGNode.LoopNode rootNode, List<Entry> entries, PrintWriter bodyWriter) {
		StringWriter sw = new StringWriter();
		PrintWriter writer = new PrintWriter(sw);

		CFGNode.LoopNode loopNode = rootNode;
		Set<DFGNode> indexDFGs = new HashSet<DFGNode>();
		loopNode.getIndexDFGNodes(indexDFGs);

		if(indexDFGs.size() != 1) {
			throw new InternalError("Unable to handle more/less than one simultanious index register");
		}

		DFGNode indexDFG = indexDFGs.iterator().next();

		if(entries.size() > 0) {
			Utils.writeIndents(writer, 1);
			typeWriter.writeLHS(indexDFG.register, indexDFG.type, writer);
			writer.print(" = ");

			if(rootNode instanceof CFGNode.ForAllLoopNode) {
				Bytecode.ForAll forAll = ((CFGNode.ForAllLoopNode)loopNode).getBytecode();

				typeWriter.writeListAccessor(forAll.getSourceRegister(), forAll.getSourceType(), new ExpressionWriter() {
					@Override
					public void writeExpression(PrintWriter writer) {
						writeKernelGlobalIndex(0, writer); // TODO: Support multiple dimensions
					}
				}, writer);
			}
			else if(rootNode instanceof CFGNode.ForLoopNode) {
				writeKernelGlobalIndex(0, writer); // TODO: Support multiple dimensions
			}
			else {
				throw new InternalError("Unexepected loop type encountered: " + rootNode);
			}

			writer.println("; // Get work item");

			entries.remove(entries.size()-1);
		}

		startTask(entries, bodyWriter, writer, sw);
	}

	private void startTask(List<Entry> entries, PrintWriter bodyWriter, PrintWriter writer, StringWriter sw) {
		new Task(writer).execute(entries);

		bodyWriter.print("\n\t// Beginning Boilerplate\n\n");
		bodyWriter.print(typeWriter.boilerPlate());
		bodyWriter.print("\n\t// Ending Boilerplate\n\n");
		bodyWriter.print("\n\t// Begin kernel\n\n");
		bodyWriter.print(sw.toString());
	}

	private void writeKernelGlobalIndex(int dimension, PrintWriter writer) {
		writer.print("get_global_id(");
		writer.print(dimension);
		writer.print(')');
	}

	private class Task {
		private final PrintWriter writer;
		private final Map<CFGNode, Integer> nodeIndexes = new HashMap<CFGNode, Integer>();
		private final Map<Integer, CFGNode> reverseNodeIndexes = new HashMap<Integer, CFGNode>();
		private final Set<CFGNode> nodesNeedingLabels = new HashSet<CFGNode>();

		private int indentationLevel = 1;

		public Task(PrintWriter writer) {
			this.writer = writer;
		}

		private void execute(List<Entry> entries) {
			populateIndexes(entries);

			CFGIterator.traverseNestedRepresentation(entries, new CFGIterator.CFGNestedRepresentationVisitor() {
				@Override
				public void visitNonNestedNode(CFGNode node) {
					writeCFGNode(node);
				}

				@Override
				public void exitedNestedNode(CFGNode node) {
					indentationLevel--;
				}

				@Override
				public void enteredNestedNode(CFGNode node, List<Entry> nestedEntries) {
					indentationLevel++;
				}
			});
		}

		private void populateIndexes(List<Entry> entries) {
			final int index[] = new int[1];
			index[0] = 0;

			CFGIterator.traverseNestedRepresentation(entries, new CFGIterator.CFGNestedRepresentationVisitor() {
				@Override
				public void visitNonNestedNode(CFGNode node) {
					nodeIndexes.put(node, index[0]);
					reverseNodeIndexes.put(index[0], node);
					index[0]++;
				}

				@Override
				public void exitedNestedNode(CFGNode node) {
				}

				@Override
				public void enteredNestedNode(CFGNode node, List<Entry> nestedEntries) {
				}
			});
		}

		private void writeCFGNode(CFGNode node) {
			CFGInstanceSwitch.on(node, new CFGInstanceSwitch.CFGInstanceSwitchVisitor() {
				@Override
				public void visitWhileNode(WhileLoopNode node) {
					writeWhileNode(node);
				}

				@Override
				public void visitVanillaNode(VanillaCFGNode node) {
					writeVanillaNode(node);
				}

				@Override
				public void visitUnresolvedTargetNode(UnresolvedTargetNode node) {
					throw new RuntimeException("Internal state inconsistancy");
				}

				@Override
				public void visitReturnNode(ReturnNode node) {
					writeReturnNode(node);
				}

				@Override
				public void visitMultiConditionalJumpNode(MultiConditionalJumpNode node) {
					writeMulticonditionalJumpNode(node);
				}

				@Override
				public void visitLoopEndNode(LoopEndNode node) {
					writeLoopEndNode(node);
				}

				@Override
				public void visitLoopBreakNode(LoopBreakNode node) {
					writeLoopBreakNode(node);
				}

				@Override
				public void visitForAllNode(ForAllLoopNode node) {
					writeForAllNode(node);
				}

				@Override
				public void visitConditionalJumpNode(ConditionalJumpNode node) {
					writeConditionalJumpNode(node);
				}

				@Override
				public void vistForNode(ForLoopNode node) {
					writeForNode(node);
				}
			});
		}

		protected void writeConditionalJumpNode(ConditionalJumpNode node) {
			checkAndAddLabelIfNeeded(node);

			if(node.getBytecode() instanceof Bytecode.ComparisonBasedJump) {
				Bytecode.ComparisonBasedJump ifCode = (Bytecode.ComparisonBasedJump)node.getBytecode();

				boolean needMet = checkIfNeedingJumps(node, node.conditionMet);
				boolean needUnmet = checkIfNeedingJumps(node, node.conditionUnmet);

				boolean needsElse = needMet == needUnmet;
				boolean reversing = needUnmet && !needMet && comparisonReversable(ifCode.getComparison());

				Code.Comparator comparison = reversing ? reverseComparison(ifCode.getComparison()) : ifCode.getComparison();
				CFGNode whenMet = reversing ? node.conditionUnmet : node.conditionMet;
				CFGNode whenUnmet = reversing ? node.conditionMet : node.conditionUnmet;

				writeIndents();
				writer.print("if(");
				writeComparitor(comparison, ifCode.getLeftOperand(), ifCode.getRightOperand(), ifCode.getType());
				writer.print(") { ");
				checkAndAddJumpsIfNeeded(node, whenMet, 0, false);
				writer.print(" }");
				writeLineEnd(ifCode, false);
				if(needsElse) {
					writeIndents();
					writer.print("else { ");
					checkAndAddJumpsIfNeeded(node, whenUnmet, 0, false);
					writer.print(" }");
					writeLineEnd(ifCode, false);
				}
			}
		}

		protected void writeForNode(ForLoopNode node) {
			checkAndAddLabelIfNeeded(node);

			writeIndents();
			writer.print("for(");
			typeWriter.writeLHS(node.getIndexRegister(), node.getType(), writer);
			writer.print(" = ");
			typeWriter.writeRHS(node.getStartRegister(), node.getType(), writer);
			writer.print("; ");
			typeWriter.writeLHS(node.getIndexRegister(), node.getType(), writer);
			writer.print(" < ");
			typeWriter.writeRHS(node.getEndRegister(), node.getType(), writer);
			writer.print("; ");
			typeWriter.writeLHS(node.getIndexRegister(), node.getType(), writer);
			writer.print(" = ");
			typeWriter.writeRHS(node.getIndexRegister(), node.getType(), writer);
			writer.print(" + 1) {\n");

			checkAndAddJumpsIfNeeded(node, node.body);
		}

		protected void writeForAllNode(ForAllLoopNode node) {
			final int index = nodeIndexes.get(node);
			checkAndAddLabelIfNeeded(node);

			Bytecode.ForAll forAll = node.getBytecode();

			writeIndents();
			writer.print("for(int loopIndex");
			writer.print(index);
			writer.print(" = 0; loopIndex");
			writer.print(index);
			writer.print(" < ");
			typeWriter.writeListLength(forAll.getSourceRegister(), forAll.getSourceType(), writer);
			writer.print("; loopIndex");
			writer.print(index);
			writer.print("++) {");
			writeLineEnd(forAll);
			writeIndents(1);
			typeWriter.writeLHS(forAll.getIndexRegister(), forAll.getIndexType(), writer);
			writer.print(" = ");
			typeWriter.writeListAccessor(forAll.getSourceRegister(), forAll.getSourceType(), new OpenCLTypeWriter.ExpressionWriter() {
				@Override
				public void writeExpression(PrintWriter writer) {
					writer.print("loopIndex");
					writer.print(index);
				}
			}, writer);
			writer.print(";\n");

			checkAndAddJumpsIfNeeded(node, node.body);
		}

		protected void writeLoopBreakNode(LoopBreakNode node) {
		}

		protected void writeLoopEndNode(LoopEndNode node) {
			checkAndAddLabelIfNeeded(node);

			writeIndents(-1);
			writer.print("}\n");

			checkAndAddJumpsIfNeeded(node, node.next, -1, true);
		}

		protected void writeMulticonditionalJumpNode(MultiConditionalJumpNode node) {
			checkAndAddLabelIfNeeded(node);

			writeIndents();
			for(Pair<Constant, CFGNode> caseStatement : node.getBranches()) {
				writer.print("if(");
				writer.print(caseStatement.first());
				writer.print(" == ");
				typeWriter.writeRHS(node.getCheckedRegister(), node.getCheckedType(), writer);
				writer.print(") { ");
				checkAndAddJumpsIfNeeded(node, caseStatement.second(), 0, false);
				writer.print(" }");
				writeLineEnd(node.getBytecode(), false);
				writeIndents();
				writer.print("else ");
			}
			writer.print("{ ");
			checkAndAddJumpsIfNeeded(node, node.getDefaultBranch(), 0, false);
			writer.print(" }");
			writeLineEnd(node.getBytecode(), false);
		}

		protected void writeReturnNode(ReturnNode node) {
			checkAndAddLabelIfNeeded(node);

			Bytecode.Return returnBytecode = node.getBytecode();

			writeIndents();
			if(returnBytecode == null) {
				writer.print("return;\n");
				return;
			}
			else {
				if(returnBytecode.isVoid()) {
					writer.print("return");
					writeLineEnd(returnBytecode);
					return;
				}
				else {
					writer.print("return ");
					typeWriter.writeRHS(returnBytecode.getOperand(), returnBytecode.getType(), writer);
					writeLineEnd(returnBytecode);
					return;
				}
			}
		}

		protected void writeVanillaNode(VanillaCFGNode node) {
			checkAndAddLabelIfNeeded(node);

			for(Bytecode b : node.body.instructions) {
				if(!(b instanceof Bytecode.GPUSupportedBytecode)) {
					throw new RuntimeException("Internal inconsistancy exception");
				}

				BytecodeInstanceSwitch.on((GPUSupportedBytecode) b, new BytecodeInstanceSwitch.BytecodeInstanceSwitchVisitor() {
					@Override
					public void visitWhile(While b) {
						throw new RuntimeException("Unexecpeted bytecode: "+b);
					}

					@Override
					public void visitUpdate(Update b) {
						writeUpdate(b);
					}

					@Override
					public void visitUnconditionalJump(UnconditionalJump b) {
						throw new RuntimeException("Unexecpeted bytecode: "+b);
					}

					@Override
					public void visitUnary(Unary b) {
						writeUnary(b);
					}

					@Override
					public void visitTupleLoad(TupleLoad b) {
						writeTupleLoad(b);
					}

					@Override
					public void visitSwitch(Switch b) {
						throw new RuntimeException("Unexecpeted bytecode: "+b);
					}

					@Override
					public void visitReturn(Return b) {
						throw new RuntimeException("Unexecpeted bytecode: "+b);
					}

					@Override
					public void visitMove(Move b) {
						writeMove(b);
					}

					@Override
					public void visitLoopEnd(LoopEnd b) {
						throw new RuntimeException("Unexecpeted bytecode: "+b);
					}

					@Override
					public void visitLoad(Load b) {
						writeLoad(b);
					}

					@Override
					public void visitLengthOf(LengthOf b) {
						writeLengthOn(b);
					}

					@Override
					public void visitLabel(Label b) {
						throw new RuntimeException("Unexecpeted bytecode: "+b);
					}

					@Override
					public void visitInvoke(Invoke b) {
						writeInvoke(b);
					}

					@Override
					public void visitFor(ForAll b) {
						throw new RuntimeException("Unexecpeted bytecode: "+b);
					}

					@Override
					public void visitConvert(Convert b) {
						writeConvert(b);
					}

					@Override
					public void visitConstLoad(ConstLoad b) {
						writeConstLoad(b);
					}

					@Override
					public void visitComparisonBasedJump(ComparisonBasedJump b) {
						throw new RuntimeException("Unexecpeted bytecode: "+b);
					}

					@Override
					public void visitBinary(Binary b) {
						writeBinary(b);
					}

					@Override
					public void visitAssign(Assign b) {
						writeAssign(b);
					}

					@Override
					public void visitNot(Not b) {
						writeNot(b);
					}
				});
			}

			checkAndAddJumpsIfNeeded(node, node.next);
		}

		private boolean checkIfNeedingJumps(CFGNode node, CFGNode next) { // TODO: Share code with checkAndAddJumpsIfNeeded
			if(!nodeIndexes.containsKey(next)) {
				return true;
			}
			else {
				CFGNode after;
				int searchIndex = nodeIndexes.get(node)+1;
				while(true) {
					after = reverseNodeIndexes.get(searchIndex);

					if(after == null) {
						return true;
					}
					else if(after instanceof PassThroughNode) {
						searchIndex++;
					}
					else {
						break;
					}
				}

				return !next.equals(after);
			}
		}

		private void checkAndAddJumpsIfNeeded(CFGNode node, CFGNode next) {
			checkAndAddJumpsIfNeeded(node, next, 0, true);
		}

		private void checkAndAddJumpsIfNeeded(CFGNode node, CFGNode next, int indentDiff, boolean newLines) {
			if(!nodeIndexes.containsKey(next)) {
				if(newLines) { writeIndents(indentDiff); }
				writer.print("return;");
				if(newLines) { writer.print('\n'); }
			}
			else {
				CFGNode after;
				int searchIndex = nodeIndexes.get(node)+1;
				while(true) {
					after = reverseNodeIndexes.get(searchIndex);

					if(after == null) {
						if(newLines) { writeIndents(indentDiff); }
						writer.print("return;");
						if(newLines) { writer.print('\n'); }
						return;
					}
					else if(after instanceof PassThroughNode) {
						searchIndex++;
					}
					else {
						break;
					}
				}

				if(!next.equals(after)) {
					if(newLines) { writeIndents(indentDiff); }
					while(true) {
						if(next == null || !nodeIndexes.containsKey(next)) {
							writer.print("return;");
							if(newLines) { writer.print('\n'); }
							return;
						}

						if(next instanceof CFGNode.LoopBreakNode) {
							int distance = 0;
							CFGNode breakTarget = next;
							while(breakTarget instanceof CFGNode.LoopBreakNode) {
								breakTarget = ((CFGNode.LoopBreakNode)breakTarget).next;
								distance++;
							}

							// FIXME: check for switch statements
							if(distance == 1 && nodeIndexes.containsKey(breakTarget)) {
								writer.print("break;");
								if(newLines) { writer.print('\n'); }
								return;
							}
							else {
								next = breakTarget;
							}
						}
						else if(next instanceof CFGNode.ReturnNode) {
							writeReturnNode((CFGNode.ReturnNode)next);
						}
						else {
							writer.print("goto ");
							writer.print(LABEL_PREFIX);
							writer.print(nodeIndexes.get(next));
							writer.print(';');
							if(DEBUG) { writer.print("/* from: "+node + " followed by: " + after + " to: " + next + "*/"); }
							if(newLines) { writer.print('\n'); }

							nodesNeedingLabels.add(next);

							return;
						}
					}
				}
			}
		}

		private void checkAndAddLabelIfNeeded(CFGNode node) {
			if(nodesNeedingLabels.contains(node)) {
				writer.print(LABEL_PREFIX);
				writer.print(nodeIndexes.get(node));
				writer.print(":\n");
			}
		}

		protected void writeNot(Not b) {
			writeIndents();
			typeWriter.writeLHS(b.getTarget(), b.getType(), writer);
			writer.print(" = !");
			typeWriter.writeRHS(b.getOperand(), b.getType(), writer);
			writeLineEnd(b);
		}

		protected void writeAssign(Assign b) {
			writeIndents();
			typeWriter.writeLHS(b.getTarget(), b.getType(), writer);
			writer.print(" = ");
			typeWriter.writeRHS(b.getOperand(), b.getType(), writer);
			writeLineEnd(b);
		}

		protected void writeBinary(final Binary b) {
			if(!(b.getType() instanceof Type.Leaf)){
				throw new RuntimeException("Dont know how to handle nonleaf types for BinArithOp: "+ b.getType() + ", operation: " + b.getArithKind() + ", registers: " + b.getLeftOperand() + " & " + b.getRightOperand());
			}

			writeIndents();
			typeWriter.writeLHS(b.getTarget(), b.getType(), writer);
			writer.print(" = ");
			writePrimitiveBinArithOp(b.getArithKind(), (Type.Leaf)b.getType(), new ExpressionWriter() {

				@Override
				public void writeExpression(PrintWriter writer) {
					typeWriter.writeRHS(b.getLeftOperand(), b.getType(), writer);
				}
			}, new ExpressionWriter() {

				@Override
				public void writeExpression(PrintWriter writer) {
					typeWriter.writeRHS(b.getRightOperand(), b.getType(), writer);
				}
			});
			writeLineEnd(b);
		}
		
		protected void writeConvert(Convert b) {
			writeIndents();			
			typeWriter.writeLHS(b.getTarget(), b.getToType(), writer);
			writer.print(" = ");
			typeWriter.writeRHS(b.getOperand(), b.getFromType(), writer);
			writeLineEnd(b);
		}

		protected void writeConstLoad(ConstLoad b) {
			writeIndents();
			typeWriter.writeLHS(b.getTarget(), b.getConstant().type(), writer);
			writer.print(" = ");
			if(b.getConstant().type().equals(Type.T_INT)) {
				writer.print(b.getConstant());
			}
			else if(b.getConstant().type().equals(Type.T_REAL)) {
				writer.print(b.getConstant());
				writer.print('f');
			}
			else if(b.getConstant().type().equals(Type.T_BOOL)) {
				writer.print(b.getConstant());
			}
			else {
				throw new InternalError("Unknown constant type: " + b.getConstant().type());
			}
			writeLineEnd(b);
		}

		protected void writeInvoke(Invoke b) {
			writeIndents();
			if(b.getType().ret() != Type.T_VOID) {
				typeWriter.writeLHS(b.getTarget(), b.getType().ret(), writer);
				writer.print(" = ");
			}
			writer.print(functionTranslator.translateFunctionName(b));
			writer.print('(');
			String sep = "";
			for(int index = 0; index < b.getOperands().length; index++) {
				writer.print(sep);
				sep = ", ";
				typeWriter.writeRHS(b.getOperands()[index], b.getType().params().get(index), writer);
			}
			writer.print(')');
			writeLineEnd(b);
		}

		protected void writeLengthOn(LengthOf b) {
			writeIndents();
			typeWriter.writeLHS(b.getTarget(), b.getType().element(), writer);
			writer.print(" = ");
			typeWriter.writeListLength(b.getOperand(), (Type)b.getType(), writer);
			writeLineEnd(b);
		}

		protected void writeLoad(final Load b) {
			writeIndents();
			typeWriter.writeLHS(b.getTarget(), b.getType().element(), writer);
			writer.print(" = ");
			typeWriter.writeListAccessor(b.getLeftOperand(), (Type)b.getType(), new ExpressionWriter() {
				@Override
				public void writeExpression(PrintWriter writer) {
					typeWriter.writeRHS(b.getRightOperand(), b.getAssignedType(), writer);
				}
			}, writer);
			writeLineEnd(b);
		}

		protected void writeMove(Move b) {
			writeIndents();
			typeWriter.writeLHS(b.getTarget(), b.getType(), writer);
			writer.print(" = ");
			typeWriter.writeRHS(b.getOperand(), b.getType(), writer);
			writeLineEnd(b);
		}

		protected void writeTupleLoad(final TupleLoad b) {
			writeIndents();
			typeWriter.writeLHS(b.getTarget(), b.getType().element(b.getIndex()), writer);
			writer.print(" = ");
			typeWriter.writeTupleAccessor(b.getOperand(), (Type)b.getType(), new ExpressionWriter() {
				@Override
				public void writeExpression(PrintWriter writer) {
					writer.print(b.getIndex());
				}
			}, writer);
			writeLineEnd(b);
		}

		protected void writeUnary(final Unary b) {
			if(!(b.getType() instanceof Type.Leaf)){
				throw new RuntimeException("Dont know how to handle nonleaf types for UnArithOp: "+b.getType());
			}

			writeIndents();
			typeWriter.writeLHS(b.getTarget(), b.getType(), writer);
			writer.print(" = ");
			writePrimitiveUnArithOp(b.getArithKind(), (Type.Leaf)b.getType(), new ExpressionWriter() {
				@Override
				public void writeExpression(PrintWriter writer) {
					typeWriter.writeRHS(b.getOperand(), b.getType(), writer);
				}
			});
			writeLineEnd(b);
		}

		protected void writeUpdate(final Update b) {
			// TODO: implement this properly
			if(b.getDataStructureBeforeType() instanceof Type.List) {
				for(Code.LVal<Type.EffectiveList> _lv : b.getLValueIterator()) {
					final Code.ListLVal lv = (Code.ListLVal)_lv;

					writeIndents();
					typeWriter.writeListAccessor(b.getTarget(), b.getDataStructureBeforeType(), new ExpressionWriter() {
						@Override
						public void writeExpression(PrintWriter writer) {
							typeWriter.writeRHS(lv.indexOperand, b.readDFGNodes.get(lv.indexOperand).type, writer);
						}
					}, writer);
					writer.print(" = ");
					typeWriter.writeRHS(b.getOperand(), b.getRHSType(), writer); // TODO: support more than one
					writeLineEnd(b);
				}
			}
			else {
				throw new NotImplementedException();
			}
		}

		protected void writeWhileNode(WhileLoopNode node) {
			checkAndAddLabelIfNeeded(node);

			writeIndents();
			writer.print("while(1) {");
			writeLineEnd(node.getBytecode(), false);
		}

		private void writeIndents() {
			Utils.writeIndents(writer, indentationLevel);
		}

		private void writeIndents(int diff) {
			Utils.writeIndents(writer, indentationLevel+diff);
		}

		private void writePrimitiveBinArithOp(Code.BinArithKind kind, Type.Leaf type, ExpressionWriter lhs, ExpressionWriter rhs) {
			writer.print('(');
			switch(kind) {
				case ADD:
					lhs.writeExpression(writer);
					writer.print(' ');
					writer.print('+');
					writer.print(' ');
					rhs.writeExpression(writer);
					break;
				case SUB:
					lhs.writeExpression(writer);
					writer.print(' ');
					writer.print('-');
					writer.print(' ');
					rhs.writeExpression(writer);
					break;
				case MUL:
					lhs.writeExpression(writer);
					writer.print(' ');
					writer.print('*');
					writer.print(' ');
					rhs.writeExpression(writer);
					break;
				case DIV:
					lhs.writeExpression(writer);
					writer.print(' ');
					writer.print('/');
					writer.print(' ');
					rhs.writeExpression(writer);
					break;
				case BITWISEAND:
					lhs.writeExpression(writer);
					writer.print(' ');
					writer.print('&');
					writer.print(' ');
					rhs.writeExpression(writer);
					break;
				case BITWISEOR:
					lhs.writeExpression(writer);
					writer.print(' ');
					writer.print('|');
					writer.print(' ');
					rhs.writeExpression(writer);
					break;
				case BITWISEXOR:
					lhs.writeExpression(writer);
					writer.print(' ');
					writer.print('^');
					writer.print(' ');
					rhs.writeExpression(writer);
					break;
				case LEFTSHIFT:
					lhs.writeExpression(writer);
					writer.print(' ');
					writer.print("<<");
					writer.print(' ');
					rhs.writeExpression(writer);
					break;
				case RIGHTSHIFT:
					lhs.writeExpression(writer);
					writer.print(' ');
					writer.print(">>");
					writer.print(' ');
					rhs.writeExpression(writer);
					break;
				case RANGE:
					throw new NotImplementedException();
				case REM:
					lhs.writeExpression(writer);
					writer.print(' ');
					writer.print("%");
					writer.print(' ');
					rhs.writeExpression(writer);
			}
			writer.print(')');
		}

		private void writePrimitiveUnArithOp(UnArithKind kind, Leaf type, ExpressionWriter expressionWriter) {
			writer.print('(');
			switch(kind) {
				case NEG:
					writer.print('-');
					expressionWriter.writeExpression(writer);
					break;
				case DENOMINATOR:
					throw new NotSupportedByGPGPUException();
				case NUMERATOR:
					throw new NotSupportedByGPGPUException();
			}
			writer.print(')');
		}

		private Comparator reverseComparison(Comparator op) {
			switch(op) {
				case SUBSET:
					throw new NotImplementedException();
				case SUBSETEQ:
					throw new NotImplementedException();
				case ELEMOF:
					throw new NotImplementedException();
				case EQ:
					return Comparator.NEQ;
				case GT:
					return Comparator.LTEQ;
				case GTEQ:
					return Comparator.LT;
				case LT:
					return Comparator.GTEQ;
				case LTEQ:
					return Comparator.GT;
				case NEQ:
					return Comparator.EQ;
			}

			// Can't ever get here, compiler wants it
			throw new InternalError("Oops");
		}

		private boolean comparisonReversable(Comparator op) {
			switch(op) {
				case SUBSET:
					throw new NotImplementedException();
				case SUBSETEQ:
					throw new NotImplementedException();
				case ELEMOF:
					throw new NotImplementedException();
				case EQ:
					return true;
				case GT:
					return true;
				case GTEQ:
					return true;
				case LT:
					return true;
				case LTEQ:
					return true;
				case NEQ:
					return true;
			}

			// Can't ever get here, compiler wants it
			throw new InternalError("Oops");
		}

		private void writeComparitor(Comparator op, int leftOperand, int rightOperand, Type type) {
			writer.print('(');
			switch(op) {
				case SUBSET:
					throw new NotImplementedException();
				case SUBSETEQ:
					throw new NotImplementedException();
				case ELEMOF:
					throw new NotImplementedException();
				case EQ:
					typeWriter.writeRHS(leftOperand, type, writer);
					writer.print(" == ");
					typeWriter.writeRHS(rightOperand, type, writer);
					break;
				case GT:
					typeWriter.writeRHS(leftOperand, type, writer);
					writer.print(" > ");
					typeWriter.writeRHS(rightOperand, type, writer);
					break;
				case GTEQ:
					typeWriter.writeRHS(leftOperand, type, writer);
					writer.print(" >= ");
					typeWriter.writeRHS(rightOperand, type, writer);
					break;
				case LT:
					typeWriter.writeRHS(leftOperand, type, writer);
					writer.print(" < ");
					typeWriter.writeRHS(rightOperand, type, writer);
					break;
				case LTEQ:
					typeWriter.writeRHS(leftOperand, type, writer);
					writer.print(" <= ");
					typeWriter.writeRHS(rightOperand, type, writer);
					break;
				case NEQ:
					typeWriter.writeRHS(leftOperand, type, writer);
					writer.print(" != ");
					typeWriter.writeRHS(rightOperand, type, writer);
					break;
			}
			writer.print(')');
		}

		private void writeLineEnd(Bytecode b) {
			writeLineEnd(b, true);
		}

		private void writeLineEnd(Bytecode b, boolean semicolon) {
			if(semicolon) {
				writer.print(';');
			}

			if(WRITE_BYTECODES_TO_COMMENTS) {
				writer.print(" // ");
				writer.println(b.getCodeString());
			}
			else {
				writer.print('\n');
			}
		}
	}

	public void writeFunctionDecleration(String attributes, Type.Leaf returnType, String name, List<Argument> arguments, PrintWriter writer) {
		if(attributes != null) {
			writer.print(attributes);
			writer.print(' ');
		}
		typeWriter.writeReturnType(returnType, writer);
		writer.print(' ');
		writer.print(name);
		writer.print('(');

		String seperator = "";
		for(Argument arg : arguments) {
			writer.print(seperator);
			seperator = ", ";

			typeWriter.writeArgDecl(arg, writer);
		}

		writer.print(')');
	}
}
