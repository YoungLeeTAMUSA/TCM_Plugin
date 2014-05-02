package net.thecodemaster.sap.markers;

import net.thecodemaster.sap.Activator;
import net.thecodemaster.sap.constants.Constants;
import net.thecodemaster.sap.ui.l10n.Messages;

import org.eclipse.core.resources.IMarker;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution2;

/**
 * @author Luciano Sampaio
 */
public class SecurityMisconfigurationResolution implements IMarkerResolution2 {

  public SecurityMisconfigurationResolution(IMarker marker) {
  }

  @Override
  public String getLabel() {
    return Messages.SecurityVulnerabilitiesVerifier.LABEL_RESOLUTION;
  }

  @Override
  public String getDescription() {
    return Messages.SecurityVulnerabilitiesVerifier.DESCRIPTION_RESOLUTION;
  }

  @Override
  public Image getImage() {
    return Activator.getImageDescriptor(Constants.Icons.SECURITY_VULNERABILITY).createImage();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void run(IMarker marker) {
  }

}
