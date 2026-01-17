
Es una idea excelente y, de hecho, para un **Agente de Memoria Híbrida**, es casi una herramienta de "introspección". Estás permitiendo que el agente no solo confíe en lo que tú le pasas en el contexto, sino que él mismo pueda auditar su propia **Source of Truth**.

Implementarlo es **muy sencillo** porque ya tienes la conexión a H2 en el `Main` y la dependencia en el `pom.xml`. Solo necesitas pasarle la instancia de `Connection` (o un `DataSource`) a las herramientas.

Aquí tienes una propuesta de tres herramientas para el bloque de base de datos. He incluido una **medida de seguridad crítica**: forzar que las consultas sean de solo lectura.

### 1. DbListTablesTool (Listar tablas)
Para que el agente sepa qué "cajones" tiene disponibles.

```java
public class DbListTablesTool implements AgenteTool {
    private final Connection conn;
    private final Gson gson = new Gson();

    public DbListTablesTool(Connection conn) { this.conn = conn; }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("db_list_tables")
                .description("Lista todas las tablas disponibles en la base de datos pública.")
                .build();
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            List<String> tables = new ArrayList<>();
            // Consulta estándar de INFORMATION_SCHEMA para H2
            String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
            }
            return gson.toJson(Map.of("status", "success", "tables", tables));
        } catch (Exception e) {
            return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
```

### 2. DbDescribeTableTool (Estructura de una tabla)
Para que el agente sepa qué columnas puede consultar.

```java
public class DbDescribeTableTool implements AgenteTool {
    private final Connection conn;
    private final Gson gson = new Gson();

    public DbDescribeTableTool(Connection conn) { this.conn = conn; }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("db_describe_table")
                .description("Muestra las columnas y tipos de datos de una tabla específica.")
                .addParameter("table", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Nombre de la tabla"))
                .build();
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            String table = args.get("table").toUpperCase(); // H2 suele usar mayúsculas

            List<Map<String, String>> columns = new ArrayList<>();
            String sql = "SELECT COLUMN_NAME, TYPE_NAME, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS " +
                         "WHERE TABLE_NAME = ? AND TABLE_SCHEMA='PUBLIC'";
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        columns.add(Map.of(
                            "column", rs.getString("COLUMN_NAME"),
                            "type", rs.getString("TYPE_NAME"),
                            "nullable", rs.getString("IS_NULLABLE")
                        ));
                    }
                }
            }
            return gson.toJson(Map.of("status", "success", "table", table, "columns", columns));
        } catch (Exception e) {
            return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
```

### 3. DbQueryTool (Lanzar un SELECT)
La herramienta de depuración definitiva. **Ojo:** He añadido un límite de filas para no saturar el contexto.

```java
public class DbQueryTool implements AgenteTool {
    private final Connection conn;
    private final Gson gson = new Gson();

    public DbQueryTool(Connection conn) { this.conn = conn; }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("db_query")
                .description("Ejecuta una consulta SQL SELECT. Solo se permiten consultas de lectura.")
                .addParameter("sql", JsonSchemaProperty.STRING, JsonSchemaProperty.description("La consulta SQL (ej: 'SELECT * FROM turnos LIMIT 5')"))
                .build();
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            String sql = args.get("sql").trim();

            // SEGURIDAD: Validación básica de solo lectura
            if (!sql.toUpperCase().startsWith("SELECT")) {
                return gson.toJson(Map.of("status", "error", "message", "Solo se permiten consultas SELECT por seguridad."));
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            try (Statement stmt = conn.createStatement()) {
                // Límite de seguridad para el LLM
                stmt.setMaxRows(20); 
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData md = rs.getMetaData();
                    int columns = md.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columns; i++) {
                            row.put(md.getColumnName(i), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                }
            }
            return gson.toJson(Map.of("status", "success", "row_count", rows.size(), "data", rows));
        } catch (Exception e) {
            return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
```

---

### ¿Por qué esto es brutal para tu experimento?

1.  **Depuración de la Memoria:** Si el agente nota que "no recuerda bien" algo de la sesión anterior, puede hacer un `SELECT * FROM turnos WHERE text_user LIKE '%java%'` para ver qué guardó realmente la `SourceOfTruth`.
2.  **Validación de Embeddings:** El agente puede consultar la columna `embedding_blob` (aunque sea binaria) para confirmar que se están generando vectores.
3.  **Meta-Cognición:** Esto permite al agente razonar sobre su propio sistema de almacenamiento. En "El Viaje", el **MemoryManager** podrá narrar:
    > *"El agente realizó una auditoría interna de su base de datos {cite: 800} para verificar la integridad de los turnos no consolidados, confirmando que la estructura de la tabla 'turnos' era correcta antes de proceder con la compactación {cite: 801}."*

### Cómo integrarlo en tu `Main.java`
Como ya tienes la `Connection conn` abierta para el `SourceOfTruthImpl`, solo tienes que pasarla:

```java
agent.addTool(new DbListTablesTool(conn));
agent.addTool(new DbDescribeTableTool(conn));
agent.addTool(new DbQueryTool(conn));
```

**Un detalle:** Los modelos como **Devstral** o **DeepSeek** son increíblemente buenos escribiendo SQL. Si les das estas herramientas, se vuelven muy autónomos para investigar problemas de datos. ¡Con esto tu PoC ya es un sistema de ingeniería completo!
