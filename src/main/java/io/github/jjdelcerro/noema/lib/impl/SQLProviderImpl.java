package io.github.jjdelcerro.noema.lib.impl;

import java.util.List;
import java.util.Properties;

/**
 *
 * @author jjdelcerro
 */
public class SQLProviderImpl implements SQLProvider {
  private final Properties sqls;
  private final String name;
  
  public SQLProviderImpl(String providerName) {
    this.name = providerName;
    this.sqls = new Properties();
    // TODO: read sqls from resources
  }
  
  @Override
  public String get(String sqlid, String defaultSql) {
    return defaultSql;
  }

  @Override
  public String getSearchDocumentsByCategories(List<String> categories) {
      StringBuilder sql = new StringBuilder("SELECT * FROM DOCUMENTS WHERE 1=1"); 
      if (categories != null && !categories.isEmpty()) {
        for (String cat : categories) {
          sql.append(" AND categories LIKE '%,")
                  .append(cat.replace("'", "''")) // Escape básico
                  .append(",%'");
        }
      }
      return sql.toString();
  }
  
}
