package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.ConnectionSupplier;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface SQLProvider {
  
  public static SQLProvider from(ConnectionSupplier conn) {
    return AgentLocator.getAgentManager().getSQLProvider(conn.getProviderName());
  }
  
  public String get(String id,String sql);

  public String getSearchDocumentsByCategories(List<String> categories);
}
