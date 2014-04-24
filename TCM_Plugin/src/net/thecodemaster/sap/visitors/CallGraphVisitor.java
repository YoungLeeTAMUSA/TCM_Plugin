package net.thecodemaster.sap.visitors;

import java.util.List;

import net.thecodemaster.sap.graph.BindingResolver;
import net.thecodemaster.sap.graph.CallGraph;
import net.thecodemaster.sap.loggers.PluginLogger;
import net.thecodemaster.sap.utils.Timer;
import net.thecodemaster.sap.utils.UtilProjects;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * @author Luciano Sampaio
 */
public class CallGraphVisitor implements IResourceVisitor, IResourceDeltaVisitor {

  /**
   * The resource types that should be trigger the call graph visitor.
   */
  private static List<String> resourceTypes;

  private CallGraph           callGraph;
  private BindingResolver     bindingResolver;

  public CallGraphVisitor(CallGraph callGraph, BindingResolver bindingResolver) {
    this.callGraph = callGraph;
    this.bindingResolver = bindingResolver;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean visit(IResourceDelta delta) throws CoreException {
    IResource resource = delta.getResource();

    switch (delta.getKind()) {
      case IResourceDelta.REMOVED:
        // TODO - Handle removed files.
        break;
      case IResourceDelta.ADDED:
      case IResourceDelta.CHANGED:
        return visit(resource);
    }
    // Return true to continue visiting children.
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean visit(IResource resource) throws CoreException {
    if (isToPerformDetection(resource)) {
      ICompilationUnit cu = JavaCore.createCompilationUnitFrom((IFile) resource);

      if (cu.isStructureKnown()) {
        // Creates the AST for the ICompilationUnits.
        Timer timer = (new Timer("Parsing: " + resource.getName())).start();
        CompilationUnit cUnit = parse(cu);
        PluginLogger.logInfo(timer.stop().toString());

        // Visit the compilation unit.
        timer = (new Timer("Visiting: " + resource.getName())).start();
        CompilationUnitVisitor cuVisitor =
          new CompilationUnitVisitor(resource.getProjectRelativePath().toOSString(), cUnit, callGraph, bindingResolver);
        cUnit.accept(cuVisitor);
        PluginLogger.logInfo(timer.stop().toString());
      }
    }
    // Return true to continue visiting children.
    return true;
  }

  /**
   * Check if the detection should be performed in this resource or not.
   * 
   * @param resource The resource that will be tested.
   * @return True if the detection should be performed in this resource, otherwise false.
   */
  private boolean isToPerformDetection(IResource resource) {
    if (resource instanceof IFile) {
      if (null == resourceTypes) {
        resourceTypes = UtilProjects.getResourceTypesToPerformDetection();
      }

      for (String resourceType : resourceTypes) {
        if (resource.getFileExtension().equalsIgnoreCase(resourceType)) {
          return true;
        }
      }
    }
    // If it reaches this point, it means that the detection should not be performed in this resource.
    return false;
  }

  /**
   * Reads a ICompilationUnit and creates the AST DOM for manipulating the Java source file.
   * 
   * @param unit
   * @return A compilation unit.
   */
  private CompilationUnit parse(ICompilationUnit unit) {
    ASTParser parser = ASTParser.newParser(AST.JLS4);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setSource(unit);
    parser.setResolveBindings(true);
    return (CompilationUnit) parser.createAST(null); // Parse.
  }
}