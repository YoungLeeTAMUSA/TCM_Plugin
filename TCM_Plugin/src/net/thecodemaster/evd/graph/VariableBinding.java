package net.thecodemaster.evd.graph;

import java.util.List;

import net.thecodemaster.evd.helper.Creator;
import net.thecodemaster.evd.ui.enumeration.EnumVariableStatus;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;

/**
 * @author Luciano Sampaio
 */
public class VariableBinding {

	private IBinding								binding;
	private Expression							initializer;
	private EnumVariableStatus			status;
	private DataFlow								dataFlow;

	private final List<Expression>	references;

	public VariableBinding(IBinding binding, Expression initializer) {
		setBinding(binding);
		setInitializer(initializer);
		setStatus(EnumVariableStatus.UNKNOWN);

		references = Creator.newList();
	}

	private IBinding getBinding() {
		return binding;
	}

	private void setBinding(IBinding binding) {
		this.binding = binding;
	}

	public Expression getInitializer() {
		return initializer;
	}

	private void setInitializer(Expression initializer) {
		this.initializer = initializer;
	}

	public void addReferences(Expression reference) {
		getReferences().add(reference);
	}

	public List<Expression> getReferences() {
		return references;
	}

	public EnumVariableStatus status() {
		return status;
	}

	public VariableBinding setStatus(EnumVariableStatus status) {
		this.status = status;

		return this;
	}

	public DataFlow getDataFlow() {
		return dataFlow;
	}

	public VariableBinding setDataFlow(DataFlow dataFlow) {
		this.dataFlow = dataFlow;

		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result *= prime + getBinding().hashCode();
		result *= prime + getInitializer().hashCode();
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (null == obj) {
			return false;
		}

		if (getClass() != obj.getClass()) {
			return false;
		}

		VariableBinding other = (VariableBinding) obj;
		if (!getBinding().equals(other.getBinding())) {
			return false;
		}
		if (!getInitializer().equals(other.getInitializer())) {
			return false;
		}

		return true;
	}

	@Override
	public String toString() {
		return String.format("%s - %s - %s", getBinding().toString(), getInitializer().toString(), status());
	}

}