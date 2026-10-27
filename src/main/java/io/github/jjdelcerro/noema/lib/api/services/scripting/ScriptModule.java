package io.github.jjdelcerro.noema.lib.api.services.scripting;

import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface ScriptModule {

  String getName();

  String getDescription();

  String help();
  
  List<Class> getImports();
  
}
