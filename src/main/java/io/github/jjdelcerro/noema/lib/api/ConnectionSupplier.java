package io.github.jjdelcerro.noema.lib.api;

import java.sql.Connection;
import java.util.function.Supplier;

/**
 *
 * @author jjdelcerro
 */
public interface ConnectionSupplier extends Supplier<Connection>{

  public String getProviderName();
}
