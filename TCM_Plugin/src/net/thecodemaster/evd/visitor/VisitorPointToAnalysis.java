package net.thecodemaster.evd.visitor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.thecodemaster.evd.constant.Constant;
import net.thecodemaster.evd.graph.BindingResolver;
import net.thecodemaster.evd.graph.CallGraph;
import net.thecodemaster.evd.graph.DataFlow;
import net.thecodemaster.evd.graph.VariableBindingManager;
import net.thecodemaster.evd.logger.PluginLogger;
import net.thecodemaster.evd.verifier.CodeAnalyzer;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * The main responsibility of this class is to find entry-points and vulnerable variables.
 * 
 * @author Luciano Sampaio
 */
public class VisitorPointToAnalysis extends CodeAnalyzer {

	public VisitorPointToAnalysis() {
	}

	public void run(List<IResource> resources, CallGraph callGraph) {
		setCallGraph(callGraph);

		// 01 - Iterate over all the resources.
		for (IResource resource : resources) {
			// We need this information when we are going retrieve the variable bindings in the callGraph.
			setCurrentResource(resource);

			run(resource);
		}
	}

	/**
	 * Iterate over all the method declarations found in the current resource.
	 * 
	 * @param resource
	 */
	protected void run(IResource resource) {
		// 01 - Get the list of methods in the current resource and its invocations.
		Map<MethodDeclaration, List<Expression>> methods = getCallGraph().getMethods(resource);

		// 02 - Iterate over all the method declarations of the current resource.
		for (MethodDeclaration methodDeclaration : methods.keySet()) {
			// To avoid unnecessary processing, we only process methods that are
			// not invoked by any other method in the same file. Because if the method
			// is invoked, eventually it will be processed.
			// 03 - Get the list of methods that invokes this method.
			Map<MethodDeclaration, List<Expression>> invokers = getCallGraph().getInvokers(methodDeclaration);
			boolean shouldProcess = false;
			if (invokers.size() > 0) {
				// 04 - Iterate over all the methods that invokes this method.
				for (MethodDeclaration callers : invokers.keySet()) {

					IResource resourceCaller = BindingResolver.getResource(callers);
					// It is a method invocation from another file.
					if (!resourceCaller.equals(resource)) {
						shouldProcess = true;
						break;
					}
				}
			} else {
				shouldProcess = true;
			}

			if (shouldProcess) {
				run(methodDeclaration);
			}
		}
	}

	/**
	 * Run the vulnerability detection on the current method declaration.
	 * 
	 * @param methodDeclaration
	 */
	protected void run(MethodDeclaration methodDeclaration) {
		// The depth control the investigation mechanism to avoid infinitive loops.
		int depth = 0;
		inspectBlock(depth, new DataFlow(), methodDeclaration.getBody());
	}

	/**
	 * 32 TODO Verify if we have to do something with the dfParent.
	 */
	@Override
	protected void inspectMethodInvocation(int depth, DataFlow dfParent, MethodInvocation expression) {
		// 01 - Check if this method is a Sanitization-Point.
		if (isMethodASanitizationPoint(expression)) {
			// If a sanitization method is being invoked, then we do not have a vulnerability.
			return;
		}

		// 02 - Check if this method is an Entry-Point.
		if (isMethodAnEntryPoint(expression)) {
			String message = getMessageEntryPoint(BindingResolver.getFullName(expression));

			// We found a invocation to a entry point method.
			dfParent.isVulnerable(Constant.Vulnerability.ENTRY_POINT, message);
			return;
		}

		// 03 - There are 2 cases: When we have the source code of this method and when we do not.
		MethodDeclaration methodDeclaration = getCallGraph().getMethod(getCurrentResource(), expression);

		if (null != methodDeclaration) {
			// We have the source code.
			inspectMethodDeclaration(depth, dfParent, expression, methodDeclaration);
		} else {
			// We do not have the source code.
			// We have to iterate over its parameters to see if any is vulnerable.
			// If there is a vulnerable parameter and this is a method from an object
			// we set this object as vulnerable.
			List<Expression> parameters = BindingResolver.getParameters(expression);
			for (Expression parameter : parameters) {
				addMethodReferenceToVariable(expression, parameter);

				// 02 - A new dataFlow for this variable.
				DataFlow df = new DataFlow(parameter);

				inspectNode(depth, df, parameter);
				// We found a vulnerability.
				if (df.isVulnerable()) {
					// There are 2 sub-cases: When is a method from an object and when is a method from a library.
					// 01 - stringBuilder.append("...");
					// 02 - System.out.println("..."); Nothing else to do.

					// Now we have to investigate if the element who is invoking this method is vulnerable or not.
					Expression objectName = expression.getExpression();
					if (null == objectName) {
						// 03.2.1 -
					}
				}
			}

		}
	}

	private void inspectMethodDeclaration(int depth, DataFlow df, MethodInvocation expression,
			MethodDeclaration methodDeclaration) {
		// If this method declaration has parameters, we have to add the values from
		// the invocation to these parameters.

		// 01 - Get the parameters of this method declaration.
		List<SingleVariableDeclaration> parameters = BindingResolver.getParameters(methodDeclaration);

		if (parameters.size() > 0) {
			int parameterIndex = 0;
			for (SingleVariableDeclaration currentParameter : parameters) {
				// 02 - The SimpleName of this parameter will be used for the addVariableToCallGraph.
				SimpleName currentName = currentParameter.getName();

				// 03 - Retrieve the variable binding of this parameter from the callGraph.
				Expression initializer = BindingResolver.getParameterAtIndex(expression, parameterIndex++);

				// 04 - We add the content with the one that came from the method invocation.
				addVariableToCallGraph(currentName, initializer);
			}
		}

		// 05 - Now I inspect the body of the method.
		inspectBlock(depth, df, methodDeclaration.getBody());
	}

	private VariableBindingManager addVariableToCallGraph(Expression expression, Expression initializer) {
		IBinding binding = BindingResolver.resolveBinding(expression);

		if (null != binding) {
			VariableBindingManager variableBinding = new VariableBindingManager(binding);
			variableBinding.setInitializer(initializer);

			getCallGraph().addVariable(variableBinding);

			return variableBinding;
		}

		return null;
	}

	/**
	 * 42
	 */
	@Override
	protected void inspectSimpleName(int depth, DataFlow df, SimpleName expression) {
		// 01 - Try to retrieve the variable from the list of variables.
		VariableBindingManager manager = getCallGraph().getLastReference(expression);
		if (null != manager) {
			if (manager.isVulnerable()) {
				DataFlow temp = manager.getDataFlow();
				df.isVulnerable(temp.getTypeProblem(), temp.getMessage());
			} else {
				// 02 - This is the case where we have to go deeper into the variable's path.
				inspectNode(depth, df, manager.getInitializer());

				// 03 - If there a vulnerable path, then this variable is vulnerable.
				if (df.isVulnerable()) {
					manager.setVulnerable(df);
				}
			}
		} else {
			PluginLogger.logIfDebugging("inspectSimpleName");
			// TODO do what here ?
		}
	}

	/**
	 * 60 TODO Verify if we have to do something with the dfParent.
	 */
	@Override
	protected void inspectVariableDeclarationStatement(int depth, DataFlow dfParent,
			VariableDeclarationStatement statement) {
		List<?> fragments = statement.fragments();
		for (Iterator<?> iter = fragments.iterator(); iter.hasNext();) {
			// VariableDeclarationFragment: is the plain variable declaration part.
			// Example: "int x=0, y=0;" contains two VariableDeclarationFragments, "x=0" and "y=0"
			VariableDeclarationFragment fragment = (VariableDeclarationFragment) iter.next();

			SimpleName simpleName = fragment.getName();
			Expression initializer = fragment.getInitializer();

			// 01 - Add the new variable to the callGraph.
			VariableBindingManager manager = addVariableToCallGraph(simpleName, initializer);
			if (null != manager) {
				// 02 - A new dataFlow for this variable.
				DataFlow df = new DataFlow(simpleName);

				// 03 - Inspect the Initializer to verify if this variable is vulnerable.
				inspectNode(depth, df, initializer);

				// 04 - If there a vulnerable path, then this variable is vulnerable.
				if (df.isVulnerable()) {
					manager.setVulnerable(df);
				}
			}
		}
	}

	private void addMethodReferenceToVariable(Expression expression, Expression initializer) {
		if (null != initializer) {
			switch (initializer.getNodeType()) {
				case ASTNode.SIMPLE_NAME:
					addReferenceSimpleName(expression, (SimpleName) initializer);
					break;
				case ASTNode.QUALIFIED_NAME:
					addReferenceQualifiedName(expression, (QualifiedName) initializer);
					break;
				case ASTNode.ASSIGNMENT:
					addReferenceAssgnment(expression, (Assignment) initializer);
					break;
				case ASTNode.INFIX_EXPRESSION:
					addReferenceInfixExpression(expression, (InfixExpression) initializer);
					break;
				case ASTNode.CONDITIONAL_EXPRESSION:
					addReferenceConditionalExpression(expression, (ConditionalExpression) initializer);
					break;
				case ASTNode.ARRAY_INITIALIZER:
					addReferenceArrayInitializer(expression, (ArrayInitializer) initializer);
					break;
				case ASTNode.PARENTHESIZED_EXPRESSION:
					addReferenceParenthesizedExpression(expression, (ParenthesizedExpression) initializer);
					break;
				default:
					PluginLogger.logIfDebugging("addMethodReferenceToVariable default: " + initializer.getNodeType());
			}
		}
	}

	private void addReference(Expression expression, IBinding binding) {
		VariableBindingManager variableBindingInitializer = getCallGraph().getLastReference(binding);
		if (null != variableBindingInitializer) {
			variableBindingInitializer.addReferences(expression);
		}
	}

	private void addReferenceSimpleName(Expression expression, SimpleName initializer) {
		addReference(expression, initializer.resolveBinding());
	}

	private void addReferenceQualifiedName(Expression expression, QualifiedName initializer) {
		addReference(expression, initializer.resolveBinding());
	}

	private void addReferenceAssgnment(Expression expression, Assignment initializer) {
		addMethodReferenceToVariable(expression, initializer.getLeftHandSide());
		addMethodReferenceToVariable(expression, initializer.getRightHandSide());
	}

	private void addReferenceInfixExpression(Expression expression, InfixExpression initializer) {
		addMethodReferenceToVariable(expression, initializer.getLeftOperand());
		addMethodReferenceToVariable(expression, initializer.getRightOperand());

		List<Expression> extendedOperands = BindingResolver.getParameters(initializer);

		for (Expression current : extendedOperands) {
			addMethodReferenceToVariable(expression, current);
		}
	}

	private void addReferenceConditionalExpression(Expression expression, ConditionalExpression initializer) {
		addMethodReferenceToVariable(expression, initializer.getThenExpression());
		addMethodReferenceToVariable(expression, initializer.getElseExpression());
	}

	private void addReferenceArrayInitializer(Expression expression, ArrayInitializer initializer) {
		List<Expression> expressions = BindingResolver.getParameters(initializer);

		for (Expression current : expressions) {
			addMethodReferenceToVariable(expression, current);
		}
	}

	private void addReferenceParenthesizedExpression(Expression expression, ParenthesizedExpression initializer) {
		addMethodReferenceToVariable(expression, initializer.getExpression());
	}

}
