package io.github.jjdelcerro.chatagent.lib;

import java.sql.Connection;
import java.util.function.Supplier;

/**
 *
 * @author jjdelcerro
 */
public interface ConnectionSupplier extends Supplier<Connection>{

  public String getProviderName();
}
