# 02. STACK TECNOLÓGICO Y NORMAS DE CÓDIGO

## 1. Contexto Tecnológico y Nivel de Experiencia
Ajusta tus respuestas y el código generado según el dominio tecnológico:

*   **ZONA CORE (Nivel Senior - Escritorio/Sistemas):** 
    *   **Stack:** Java SE (Swing, Maven), Jython, Linux/Bash, IPC, MCP, Arquitecturas OSGi/Plugins.
    *   **Enfoque:** Arquitectura sólida, rendimiento, mantenibilidad a largo plazo. 
    *   **Librerías:** Prioriza SIEMPRE la biblioteca estándar o librerías maduras (ej: Apache Commons). Evita dependencias modernas superfluas o "micro-librerías".
*   **ZONA DE TRANSICIÓN (Nivel Medio/Aprendizaje - Web):** 
    *   **Stack:** Python 3 (Django), JavaScript (React), Docker.
    *   **Enfoque:** Explicaciones didácticas. Prioriza la claridad algorítmica sobre la "magia" o el *sugar syntax* del framework. Minimiza el uso de CSS complejo.

## 2. Restricciones de Versión (CRÍTICO)
*   **Java:** Solo genera código para **Java 1.8** o **Java 21**. 
    *   *Regla de Oro:* Antes de proponer código moderno, verifica si el contexto del proyecto es Java 1.8. Si es 1.8, **TIENES PROHIBIDO** usar `var`, `records`, `switch` expressions, bloques de texto `"""` o APIs introducidas en versiones posteriores.
*   **Linux/Bash:** Asume siempre un entorno Ubuntu/Bash para scripts de terminal.

## 3. Normas de Formateo de Código (Java)
El código generado debe respetar estrictamente el estilo legacy del proyecto:

*   **Estructuras de control:** Uso de llaves `{}` OBLIGATORIO siempre, incluso en bloques de una sola línea.
*   **Saltos de línea:** La sentencia NUNCA debe ir en la misma línea que la condición (`if`, `for`, `while`).
*   **Métodos:** La llave de apertura `{` va en la MISMA LÍNEA que la firma del método.
*   **Bloques Switch:** El contenido de cada `case` debe ir en una LÍNEA SEPARADA, debajo de la etiqueta `case`. 
    *   *Incorrecto:* `case TIPO_A: procesarA(); break;`
    *   *Correcto:* 
        `case TIPO_A:`
        `    procesarA();`
        `    break;`
*   **Codificación y Comentarios:** Escribe los comentarios y Javadoc preferiblemente en inglés. Si los escribes en español, **TIENES PROHIBIDO usar tildes, la letra 'ñ' o caracteres especiales** (escribe en formato ASCII puro) para evitar problemas de *encoding*.

## 4. Normas para Otros Lenguajes
*   **Python:** Aplica PEP-8, pero si estás modificando código existente antiguo (ej. Jython), mantén la coherencia visual con el bloque circundante por encima de la norma PEP-8.
*   **Bash:** Prioriza la legibilidad. Usa variables descriptivas en MAYÚSCULAS para variables globales o de entorno.
