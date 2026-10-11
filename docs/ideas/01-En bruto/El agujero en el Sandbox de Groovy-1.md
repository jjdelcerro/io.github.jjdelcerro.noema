

### El agujero en el Sandbox de Groovy (`new File(...)` y bypass de `AgentAccessControl`)
* **Lo que advertían las notas (Sección 2.A):**  
  > *"`SecureASTCustomizer` bloquea imports estáticos... pero constructores directos (`new File('/...')`) o `Eval.me(...)` pueden saltarse restricciones si no se configuran límites sobre receptores o creación de objetos... cualquier I/O debe pasar obligatoriamente por `agent.fs`."*
* **Lo que hay en `ScriptEngine.java`:**  
  Solo tienes bloqueados los imports explícitos de `System`, `Runtime`, `ProcessBuilder` y reflection:
  ```java
  secureCustomizer.setDisallowedImports(List.of(
      "java.lang.System", "java.lang.Runtime", "java.lang.ProcessBuilder", "java.lang.reflect.*"
  ));
  ```
* **El problema real:**  
  En Groovy, `java.io.*` está **importado por defecto en el lenguaje**. Un script generado por el LLM puede escribir:
  ```groovy
  new File("/etc/passwd").text
  // o peor aún:
  new File("cualquier_sitio").delete()
  ```
  Esto no genera ningún error de compilación AST y **se salta por completo tu `AgentAccessControlImpl`**, los backups de RCS y las listas blancas/negras del workspace.
* **Solución a abordar:**  
  En `SecureASTCustomizer`, hay que prohibir la instanciación de tipos de I/O directo o limitar las clases instanciables:
  ```java
  secureCustomizer.setDisallowedImports(List.of(
      "java.lang.System", "java.lang.Runtime", "java.lang.ProcessBuilder",
      "java.lang.reflect.*", "java.io.File", "java.io.FileInputStream", 
      "java.io.FileOutputStream", "java.nio.file.*"
  ));
  // Y bloquear expresiones de creación de tipos prohibidos:
  secureCustomizer.setExpressionsBlacklist(List.of(
      org.codehaus.groovy.ast.expr.MethodCallExpression.class // si quieres hilar fino con Eval.me
  ));
  ```
  O al menos añadir `java.io.File`, `java.io.*` y `java.nio.file.*` a la lista de tipos no permitidos en el AST, forzando a que cualquier interacción con disco use exclusivamente `agent.fs`.


### Resumen de prioridades:

| Prioridad | Tarea pendiente de las notas | Impacto |
| :--- | :--- | :--- |
| 🔴 **Alta** | Bloquear `java.io.File` / E/S directa en `SecureASTCustomizer` | Evita que el LLM salte el sandbox con `new File(...)`. |


