La comunidad ha resuelto este problema creando **pasarelas universales MCP $\longleftrightarrow$ LSP**. 

El esquema es simple y elegante:
$$\text{Noema (Cliente MCP)} \longleftrightarrow \text{Servidor MCP (Bridge)} \longleftrightarrow \text{Servidor LSP nativo (\texttt{jdtls}, \texttt{pyright}, \texttt{rust-analyzer})}$$

Como Noema ya soporta transporte `stdio` en `McpServiceImpl`, puedes enchufar cualquiera de estos servidores en tu `settings.json` **sin picar una sola línea de Java**.

A continuación tienes los servidores MCP de LSP más destacados, maduros y probados en el ecosistema:

---

### 1. `isaacphi/mcp-language-server` (El más probado y políglota)
Es probablemente el puente más conocido (escrito en Go). Su diseño es universal: actúa como servidor MCP de cara a Noema y por detrás se comunica por `stdio` con cualquier Language Server que le indiques.

* **Herramientas que expone al agente:**
  * `diagnostics`: Devuelve los errores y advertencias de compilación para un archivo específico.
  * `definition`: Salta a la definición exacta del símbolo (resuelve tipos e imports).
  * `references`: Encuentra todas las llamadas/usos de un método o clase en el proyecto.
  * `hover`: Muestra tipos y Javadoc/docstring del elemento en una línea/columna.
* **Lenguajes:** Java (`jdtls`), Rust (`rust-analyzer`), TypeScript (`tsserver`), Python (`pyright`), Go (`gopls`), C/C++ (`clangd`).
* **Cómo se instala:** Binario de Go (`go install github.com/isaacphi/mcp-language-server@latest`).

---

### 2. `ProfessioneIT/lsp-mcp-server` (Diseñado para agentes de código)
Construido específicamente para dar inteligencia de código a agentes tipo Claude Code. Soporta más de 10 lenguajes (incluido Java con `jdtls`) y tiene una optimización clave: **Push-based diagnostics**.

* **Ventaja:** Mantiene una caché en tiempo real de los diagnósticos del LSP. Cuando Noema le pide los diagnósticos tras un cambio, la respuesta es inmediata porque el servidor no espera a reindexar; ya tiene la cola de errores lista.
* **Herramientas:** Más de 20 herramientas de código, enfocadas en diagnóstico de ficheros, jerarquía de tipos y espacios de trabajo multi-raíz.

---

### 3. `bug-ops/mcpls` (Ultraligero en Rust)
Si buscas rendimiento nativo y no depender de Node ni de Go, `mcpls` es un binario único en Rust (~2MB) que actúa como puente universal sin dependencias de runtime.

* **Ventaja:** Latencia mínima. Al estar escrito en Rust sobre Tokio, el paso de mensajes JSON-RPC entre Noema y el LSP tiene una sobrecarga de apenas unos microsegundos.
* **Herramientas:** `get_diagnostics`, `get_references`, `get_hover`, `rename_symbol`.

---

### 4. `tritlo/lsp-mcp` (Vía Node.js / `npx`)
Es una de las implementaciones pioneras en TypeScript. No requiere compilar binarios si tienes Node/npm en el sistema.

* **Ejecución directa:** Se lanza directamente con `npx tritlo/lsp-mcp <language-id> <path-to-lsp> <args>`.
* **Herramientas:** `get_diagnostics`, `open_document`, `get_info_on_location`.

---

### ¿Cómo se integraría esto en tu Noema?

En tu archivo `var/config/settings.json`, dentro de la sección `"mcp"`, añadirías el servidor apuntando al binario elegido. Por ejemplo, para un proyecto Java usando `mcp-language-server` y `jdtls` (el Eclipse Java Language Server que usan Neovim y VS Code):

```json
{
  "mcp": {
    "servers": [
      {
        "name": "codebase-memory",
        "type": "stdio",
        "mode": "READ",
        "enabled": true,
        "command": ["codebase-memory-mcp"]
      },
      {
        "name": "java-lsp",
        "type": "stdio",
        "mode": "READ",
        "enabled": true,
        "command": [
          "mcp-language-server",
          "--workspace", ".",
          "--lsp", "jdtls"
        ]
      }
    ]
  }
}
```

### El nuevo flujo de desarrollo en Noema

Al arrancar Noema, tu `McpServiceImpl` conectará con el servidor e inyectará automáticamente `diagnostics`, `definition`, etc. en el catálogo de `availableTools`.

El protocolo en tu `software_development/SKILL.md` quedaría así de elegante:

1. **Exploración:** El modelo usa `codebase-memory-mcp` y `find_references` para entender el impacto.
2. **Edición:** Aplica el cambio con `file_search_and_replace`.
3. **Verificación inmediata (El "subrayado rojo"):** 
   En el mismo turno, el modelo invoca `diagnostics(path: "RecentMemoryImpl.java")`.
   * Si devuelve `[]` (vacío): el código es sintácticamente y semánticamente válido. El modelo da por cerrada la edición.
   * Si devuelve `[Line 42: incompatible types]`: el modelo repara la línea 42 de inmediato sin haber ejecutado jamás `mvn compile`.
4. **Cierre:** Cuando todo el archivo está limpio de subrayados rojos, ejecuta `shell_execute("mvn test-compile")` una sola vez como confirmación final del empaquetado.

***

He revisado en detalle la documentación técnica del repositorio oficial (`DeusData/codebase-memory-mcp`), sus especificaciones de herramientas y el paper de investigación publicado (*arXiv:2603.27277*).

La respuesta directa es: **No, `codebase-memory-mcp` NO tiene herramientas para comprobar errores ni diagnósticos de compilación al estilo LSP**.

A continuación te explico qué hace realmente, qué herramientas expone y por qué existe esta separación.

---

### 1. El catálogo real de herramientas de `codebase-memory-mcp`

`codebase-memory-mcp` expone un catálogo de herramientas orientado estrictamente a **la cartografía, la búsqueda y la navegación estructural**:

| Categoría | Herramientas principales | Propósito |
| :--- | :--- | :--- |
| **Indexación** | `index_repository`, `index_status`, `list_projects` | Construir y mantener la base de datos SQLite con el grafo de símbolos. |
| **Búsqueda** | `search_graph`, `get_code_snippet`, `search_code` | Localizar clases, métodos o rutas HTTP y leer su definición exacta. |
| **Trazabilidad** | `trace_path`, `detect_changes` | Rastrear cadenas de llamadas entrantes/salientes (*call chains*) y ver qué símbolos se ven afectados tras un cambio de Git. |
| **Arquitectura** | `get_architecture`, `get_graph_schema`, `query_graph` | Visión global por comunidades (algoritmo Louvain) y consultas directas en lenguaje Cypher. |
| **Memoria** | `manage_adr` | Almacenar y consultar registros de decisiones de arquitectura (ADRs). |

En ningún sitio de su API existe un equivalente a `textDocument/publishDiagnostics`, `get_diagnostics` o `check_lint`.

---

### 2. Por qué no tiene diagnósticos (Tree-sitter vs. Compilador)

La confusión es habitual porque en el README de `codebase-memory-mcp` mencionan que usan **"Hybrid LSP type resolution"**. Sin embargo, ese mecanismo se usa para otra cosa:

1. **Tree-sitter es tolerante a errores por diseño:** Su motor de análisis sintáctico está hecho para no romperse si el código está a medio escribir o tiene errores graves de sintaxis. Simplemente salta el error y sigue indexando el resto del archivo. No está programado para emitir quejas de compilación.
2. **El "Hybrid LSP" solo infiere aristas del grafo:** Lo usan únicamente para resolver tipos en lenguajes como Java o TypeScript (por ejemplo: saber que `servicio.start()` apunta a `ReasoningServiceImpl` y no a otro `start`), de modo que el grafo trace la flecha correcta. Pero no valida si faltan argumentos, si los tipos no coinciden o si falta un `import`.
3. **No ejecuta el compilador:** Un diagnóstico de LSP real requiere un *type-checker* completo (como el compilador de Eclipse `jdtls` para Java, o `pyright` para Python), que es un software pesado que valida el programa contra el classpath completo del proyecto. `codebase-memory-mcp` es un binario ligero en C/Go que intencionadamente no carga esa complejidad.

---

### 3. La combinación ideal en Noema: Especialistas Complementarios

Esto no es un defecto de `codebase-memory-mcp`, sino una delimitación clara de responsabilidades. De hecho, en foros técnicos de agentes de desarrollo se suele recomendar tenerlos juntos:

```
┌────────────────────────────────────────────────────────┐
│                   KERNEL DE NOEMA                      │
│                                                        │
│  ┌─────────────────────────┐  ┌─────────────────────┐  │
│  │  codebase-memory-mcp    │  │ mcp-language-server │  │
│  │                         │  │      (con JDT.LS)   │  │
│  │  * Grafo del proyecto   │  │  * Diagnósticos     │  │
│  │  * trace_path           │  │  * get_diagnostics  │  │
│  │  * search_graph         │  │  * find_definition  │  │
│  │                         │  │                     │  │
│  │ (Para EXPLORAR y        │  │ (Para VERIFICAR     │  │
│  │  ahorrar tokens)        │  │  errores al editar) │  │
│  └─────────────────────────┘  └─────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

Gracias a que en `settings.json` tienes `mcp/servers` definido como un array de servidores, **no tienes que renunciar a ninguno**:

1. Mantienes **`codebase-memory-mcp`**: Noema lo usa para responder *"¿dónde se instancia `RecentMemory`?"* o *"¿qué componentes llaman a `processSingleEvent`?"* en milisegundos y con un consumo insignificante de tokens.
2. Añades **`mcp-language-server`** (o `mcpls`): Noema lo usa tras ejecutar `file_search_and_replace` para llamar a `get_diagnostics` y comprobar inmediatamente si ha dejado una línea en rojo antes de hablar contigo.


***

**Sí, se solapan claramente, y sí: el solapamiento de herramientas es una de las mayores fuentes de confusión, indecisión y degradación cognitiva en un LLM.**

Tu intuición es totalmente certera. En el diseño de agentes existe un principio fundamental: **una intención debe mapear a una sola herramienta inequívoca**. 

Si le das a un modelo dos herramientas que hacen cosas parecidas con nombres distintos, el LLM empieza a dudar, mezcla argumentos, entra en bucles innecesarios o sufre lo que en investigación se conoce como *Tool Saturation* (saturación de atención provocada por un catálogo de herramientas demasiado inflado).

Veamos exactamente dónde chocan y cómo se resuelve esto limpiamente en la arquitectura de Noema.

---

### 1. Dónde se solapan exactamente

Si levantas `codebase-memory-mcp` y un servidor como `mcp-language-server` a la vez sin filtrar nada, el LLM se encuentra con duplicidades flagrantes:

| Intención del Agente | Herramienta en `codebase-memory` | Herramienta en `mcp-language-server` | Conflicto para el LLM |
| :--- | :--- | :--- | :--- |
| **Buscar dónde está definida una clase/método** | `search_graph` | `definition` / `workspace_symbol` | ¿Busco en el grafo o pregunto al LSP? |
| **Saber quién llama a esta función** | `trace_path` | `references` | Ambas devuelven llamadores, pero una en JSON relacional y otra en ubicaciones de fichero. |
| **Ver la firma o documentación de algo** | `get_code_snippet` | `hover` | Dos formas distintas de pedir el código circundante. |
| **Comprobar si el código compila** | *(No existe)* | `diagnostics` | **Cero solapamiento (Esta es la única que realmente nos falta).** |

---

### 2. El peligro real: El problema de la "Doble Verdad" (Sincronización)

Más allá de la duda de qué herramienta elegir, hay un problema mucho más sutil y peligroso: **la frescura de los datos**.

1. **`codebase-memory-mcp` es estático / indexado:** Trabaja sobre una base de datos SQLite precalculada. Si Noema edita un archivo con `file_search_and_replace`, el grafo de `codebase-memory` **no sabe que el archivo ha cambiado** hasta que alguien o algo ejecute `index_repository`.
2. **El LSP es dinámico / en memoria:** Trabaja con los archivos en caliente en disco. En cuanto el archivo se guarda, el LSP conoce el cambio al instante.

Si el modelo edita un archivo y luego usa `search_graph` o `get_code_snippet` para ver cómo ha quedado, **leerá el código antiguo del grafo**, pensará que su edición ha fallado y entrará en un bucle alucinatorio.

---

### 3. Cómo solucionarlo en Noema: "La navaja de Ockham"

La regla de oro en ingeniería de agentes es: **no le des al LLM herramientas que no necesita**. 

De un servidor LSP, Noema **no necesita** ni `definition`, ni `references`, ni `hover`, porque la navegación arquitectónica ya la hace de forma excelente `codebase-memory-mcp` (que además comprende rutas HTTP, dependencias Maven globales y comunidades Louvain, cosas que el LSP no ve).

De la pasarela LSP, Noema **solo necesita UNA herramienta: `diagnostics`**.

#### ¿Cómo implementarlo en Noema sin fricción?

Actualmente, en tu `McpServiceImpl.java` (línea 112), Noema hace un registro ciego de todo lo que expone el servidor:
```java
List<ToolSpecification> mcpTools = client.listTools();
for (ToolSpecification spec : mcpTools) {
    this.tools.add(new McpToolWrapper(agent, client, spec, mode));
}
```

Para evitar la saturación de herramientas y el solapamiento, basta con darle a `McpServiceImpl` la capacidad de **filtrar herramientas** desde `settings.json`. Por ejemplo, permitiendo una lista blanca opcional de herramientas autorizadas para cada servidor:

```json
{
  "name": "java-lsp",
  "type": "stdio",
  "mode": "READ",
  "enabled": true,
  "command": ["mcp-language-server", "--lsp", "jdtls"],
  "include_tools": ["diagnostics"]
}
```

O si no quieres tocar `McpServiceImpl`, gestionarlo a través de la infraestructura que ya tienes:
* Todas las herramientas descubiertas se registran en `ReasoningServiceImpl`.
* En la lista `reasoning/active_tools` de `settings.json` (o vía `reasoning.setToolActive(toolName, false)`), **desactivas por defecto** las herramientas duplicadas (`definition`, `references`, `hover`) y dejas encendida únicamente `diagnostics`.

---

### Resumen

Tu preocupación es totalmente real:
1. **Tener ambos servidores completos confunde al LLM** y desperdicia tokens de atención describiendo herramientas redundantes en el prompt de sistema.
2. **La solución elegante:** Especialización estricta.
   * **`codebase-memory-mcp`:** Se queda con el monopolio de la **navegación, búsqueda y arquitectura**.
   * **El servidor LSP:** Queda recortado quirúrgicamente para aportar únicamente el **compilador/diagnóstico (`diagnostics`)**.

Así, cuando Noema quiere explorar el proyecto, usa el grafo. Cuando edita una línea, llama a la única herramienta de verificación posible: `diagnostics`. Cero ambigüedad para el modelo.
