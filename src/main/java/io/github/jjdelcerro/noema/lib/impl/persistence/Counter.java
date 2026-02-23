package io.github.jjdelcerro.noema.lib.impl.persistence;

import io.github.jjdelcerro.noema.lib.ConnectionSupplier;
import io.github.jjdelcerro.noema.lib.impl.SQLProvider;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gestiona un contador autoincremental en memoria, inicializado desde el valor
 * máximo actual de una tabla en base de datos.
 * <p>
 * Diseñado para entornos de proceso único (single-tenant).
 */
public class Counter {

    private int currentMax;

    // Constructor privado para forzar el uso del método de factoría
    private Counter(int initialValue) {
        this.currentMax = initialValue;
    }

    /**
     * Inicializa el contador consultando el MAX(id) de la tabla especificada.
     * Lee la base de datos una única vez durante la inicialización.
     *
     * @param connSupplier Conexión abierta a la base de datos.
     * @param table Nombre de la tabla sobre la que contar.
     * @return Una instancia de Counter sincronizada.
     * @throws IllegalArgumentException Si la tabla no existe o hay error SQL.
     */
    public static Counter from(ConnectionSupplier connSupplier, String table) {
        String sql = SQLProvider.from(connSupplier).get("Counter_selectmax","SELECT MAX(id) FROM " + table);

        try (   Connection conn = connSupplier.get();
                Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)
            ) {

            if (rs.next()) {
                // getInt devuelve 0 si el valor SQL es NULL (tabla vacía),
                // lo cual es perfecto para iniciar el siguiente ID en 1.
                return new Counter(rs.getInt(1));
            }
            // En teoría SELECT MAX siempre devuelve una fila (aunque sea null),
            // pero por seguridad retornamos 0 si no hay resultado.
            return new Counter(0);

        } catch (SQLException e) {
            throw new IllegalArgumentException("Error al inicializar Counter. Verifique que la tabla '" + table + "' existe.", e);
        }
    }

    /**
     * Incrementa y devuelve el siguiente ID disponible. Es Thread-Safe dentro
     * de la misma JVM.
     *
     * @return El siguiente identificador entero.
     */
    public synchronized int get() {
        return ++currentMax;
    }
}
