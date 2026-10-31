package hu.lasterbergamot.mealmagician.shared.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Serves the PWA manifest ({@code static/manifest.webmanifest}) with the media type browsers
 * expect, {@code application/manifest+json}.
 *
 * <p>Why not leave it to the static-resource handler: neither Tomcat nor Spring knows the {@code
 * .webmanifest} extension and would answer {@code application/octet-stream}, which some browsers
 * refuse to install from. This controller wins over the static handler for this one URL.
 */
@Controller
public class ManifestController {

  private static final Resource MANIFEST = new ClassPathResource("static/manifest.webmanifest");

  /**
   * Returns the manifest file.
   *
   * @return the file's bytes; the {@code produces} attribute sets the content type
   */
  @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
  @ResponseBody
  public Resource manifest() {
    return MANIFEST;
  }
}
