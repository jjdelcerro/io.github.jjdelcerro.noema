
# Migración de la documentación técnica de Noema a la web personal


---

## 1. Objetivo general

Trasladar la documentación técnica de Noema desde el repositorio de GitHub (donde está enterrada y no es indexable por buscadores) a tu sitio web personal (`jjdelcerro.github.io`), manteniendo un único punto de verdad, evitando duplicaciones y asegurando que los enlaces sean estables incluso cuando la documentación evolucione.

---

## 2. Principios rectores

1. **Unidad de verdad:** la documentación técnica vive en un solo lugar (tu web). El repositorio solo contiene punteros.
2. **URLs estables:** los enlaces públicos no dependen del nombre del archivo ni de su estructura interna. Usan `permalink` fijos.
3. **Separación de roles:**
   - **Artículos (DEV/LinkedIn):** contenido conceptual perenne. Explican problemas y principios, no detalles de implementación.
   - **Documentación técnica:** contenido operativo vivo. Describe la implementación actual, con fecha de revisión.
   - **Anuncios (LinkedIn):** contenido efímero para notificar actualizaciones.
4. **Trazabilidad:** los artículos pueden enlazar a una versión congelada de la documentación (snapshot en GitHub) para referencias históricas.

---

## 3. Estructura final de la documentación en tu web

Dentro de tu repositorio de Jekyll (`jjdelcerro.github.io`):

```
jjdelcerro.github.io/
├── noema/
│   ├── index.md                     # Landing page de Noema
│   ├── docs/
│   │   ├── sensors-service.md       # Documentos técnicos (con permalink fijo)
│   │   ├── reasoning-service.md     # Ej: permalink: /noema/docs/reasoning/
│   │   ├── memory-service.md
│   │   ├── agent-tools.md
│   │   └── scheduler-service.md
│   └── articles.md                  # (opcional) índice de artículos relacionados
```

Cada documento técnico tendrá en el frontmatter:

```yaml
---
layout: doc
title: Sistema sensorial (SensorsService)
permalink: /noema/docs/sensores/     # URL pública estable
last_modified_at: 2026-08-15
---
```

La landing page (`noema/index.md`) contendrá:
- Una introducción a Noema (qué es, filosofía).
- Un índice de documentación técnica con enlaces a cada subsistema (usando los `permalink` fijos).
- Un aviso claro: *"Esta documentación es técnica y está viva. Refleja el estado actual del desarrollo. Última revisión: [fecha]."*
- Enlaces a los artículos relacionados (los que ya has publicado).
- Enlace al repositorio de GitHub.

---

## 4. Flujo de trabajo para mover la documentación

### Paso 1: Preparar el repositorio de Noema (GitHub)

- Elimina los archivos de documentación técnica de la carpeta `docs/` (o muévelos a un directorio `archive/` si quieres conservar histórico).
- Crea o actualiza `AGENT_CONTEXT.md` para que contenga enlaces a la nueva ubicación en tu web:

```markdown
## Documentación técnica

La documentación técnica completa de Noema está alojada en mi [sitio web personal](https://jjdelcerro.github.io/noema/).

- [Sistema sensorial](https://jjdelcerro.github.io/noema/docs/sensores/)
- [Servicio de razonamiento](https://jjdelcerro.github.io/noema/docs/razonamiento/)
- [Servicio de memoria](https://jjdelcerro.github.io/noema/docs/memoria/)
- [Sistema de herramientas y paginación](https://jjdelcerro.github.io/noema/docs/herramientas/)
- [Servicio de planificación](https://jjdelcerro.github.io/noema/docs/planificacion/)

*Nota: esta documentación se actualiza con el desarrollo. La fecha de la última revisión aparece en cada página.*
```

- Actualiza el `README.md` para que el enlace a la documentación apunte a `AGENT_CONTEXT.md` o directamente a tu web.

### Paso 2: Copiar y adaptar los documentos a tu web

- Copia los archivos `.md` de documentación al directorio `noema/docs/` de tu repositorio Jekyll.
- Añade el frontmatter con `permalink` fijo, `layout`, `title` y `last_modified_at`.
- Añade al inicio de cada documento un **aviso de documentación viva**:

> *⚠️ Esta es una especificación técnica de un proyecto en desarrollo activo. Los detalles de implementación pueden cambiar. La fecha de la última revisión aparece al final de esta página.*

- Asegúrate de que los enlaces internos entre documentos (si los hay) usen los `permalink` fijos, no rutas relativas a archivos.

### Paso 3: Crear la landing page de Noema

- Crea `noema/index.md` con la estructura descrita arriba.
- Añade el índice de documentación.
- Añade la sección "Artículos relacionados" con enlaces a tus posts (los de memoria narrativa, proactividad, etc.).
- Añade un aviso claro sobre la naturaleza de la documentación.

### Paso 4: Publicar y verificar

- Haz `git push` de tu repositorio Jekyll.
- Verifica que las URLs funcionan (ej: `https://jjdelcerro.github.io/noema/docs/razonamiento/`).
- Verifica que los enlaces desde `AGENT_CONTEXT.md` en GitHub apuntan a las URLs correctas.

---

## 5. Gestión de enlaces estables (clave)

### Para artículos (DEV / LinkedIn)

- **Nunca enlazar directamente a un archivo o sección concreta de la documentación** (porque puede moverse o renombrarse).
- En su lugar, enlazar a la **landing page de Noema** o al **índice de documentación**.
- Si se quiere ser más específico, enlazar a la sección de la landing que describe el subsistema, usando un `id` fijo en el HTML (ej: `#sistema-sensorial`).

**Ejemplo:**

> *"La implementación concreta de este principio está documentada en la [sección de documentación técnica de Noema](https://jjdelcerro.github.io/noema/#documentacion-tecnica)."*

### Para el artículo + snapshot dual (opcional, pero recomendado)

En los artículos, puedes incluir dos enlaces:

> * **Versión activa (recomendada):** la especificación del [ReasoningService se mantiene actualizada en mi web](https://jjdelcerro.github.io/noema/docs/razonamiento/).*
> * **Versión congelada (referencia histórica):** si necesitas consultar el estado exacto de la documentación en el momento de esta publicación, puedes ver el [snapshot en GitHub](https://github.com/jjdelcerro/io.github.jjdelcerro.noema/blob/8f3b7a1/docs/reasoning-service.md).*

**Cómo obtener el snapshot de GitHub:**
- Ve al archivo en el repo, haz clic en "History", selecciona el commit anterior a la publicación, y copia la URL del archivo en ese commit.

### Para la landing page

- Los enlaces a los documentos técnicos usan los `permalink` fijos (nunca cambian).
- Si añades un nuevo subsistema, actualizas la landing. Si renombras un archivo, mantienes el `permalink` fijo, así que los enlaces no se rompen.

---

## 6. Estrategia de artículos y anuncios

### Artículos cortos en DEV / LinkedIn (contenido perenne)

- Tratan sobre **problemas y principios**, no sobre detalles de implementación.
- No mencionan fechas de actualización ni "he actualizado la documentación".
- Enlazan a la landing de Noema o al índice de documentación.
- Si se incluye el enlace dual (vivo + snapshot), se hace de forma clara y diferenciada.

**Frecuencia:** cuando introduces un nuevo concepto arquitectónico (ej: arquitectura Albert, sistema sensorial, etc.).

### Anuncios en LinkedIn (contenido efímero)

- Son posts cortos que notifican una actualización importante de la documentación.
- Ejemplo: *"Acabo de actualizar la especificación técnica del ReasoningService de Noema con la nueva arquitectura de consciencia multiterminal. Detalles aquí: [enlace]."*
- Caducan en el feed en pocos días. No requieren mantenimiento.

**Frecuencia:** cuando actualizas un documento de forma significativa.

---

## 7. Mantenimiento continuo

### Cuando actualizas un documento técnico

1. Editas el archivo `.md` en tu repositorio Jekyll.
2. Actualizas el campo `last_modified_at` en el frontmatter (o usas el plugin `jekyll-last-modified-at` para que sea automático).
3. Si el cambio es sustancial y quieres anunciarlo, publicas un post corto en LinkedIn (no un artículo en DEV).
4. No tocas los artículos de DEV, a menos que el cambio afecte a un principio fundamental descrito en ellos (lo cual es raro).

### Cuando añades un nuevo subsistema

1. Creas el nuevo archivo `.md` en `noema/docs/` con su `permalink` fijo.
2. Actualizas el índice en la landing page (`noema/index.md`).
3. Si el subsistema merece un artículo conceptual, escribes un artículo corto para DEV/LinkedIn (enlazando a la landing, no al archivo concreto).

### Cuando reestructuras la carpeta de documentación

- Los `permalink` fijos protegen las URLs públicas. Puedes mover archivos, cambiarlos de nombre o reestructurar la carpeta siempre que mantengas los `permalink` en el frontmatter.
- Si quieres cambiar un `permalink` (porque el subsistema ha cambiado de nombre), actualiza la landing y, si es necesario, haz una redirección 301 en Jekyll (usando el plugin `jekyll-redirect-from`).

---

## 8. Resumen de pasos concretos para la migración

1. **En tu web Jekyll:**
   - Crea el directorio `noema/` y `noema/docs/`.
   - Crea `noema/index.md` (landing) con la estructura descrita.
   - Copia los archivos `.md` de documentación a `noema/docs/`.
   - Añade frontmatter con `permalink` fijo a cada uno.
   - Añade el aviso de "documentación viva" a cada archivo.

2. **En el repositorio de Noema (GitHub):**
   - Elimina los archivos de documentación de `docs/` (o archívalos).
   - Actualiza `AGENT_CONTEXT.md` con los nuevos enlaces a tu web.
   - Actualiza `README.md` para que apunte a `AGENT_CONTEXT.md` o a tu web.
   - Commitea y pushea.

3. **En tus artículos existentes (si los hay):**
   - Si alguno enlazaba a documentación del repo, actualiza el enlace a la landing o al índice.
   - Si quieres añadir el enlace dual (vivo + snapshot), hazlo en los nuevos artículos; los antiguos no es necesario retocarlos a menos que los enlaces estén rotos.

4. **En el futuro:**
   - Cuando actualices documentación, edita los archivos en tu web y actualiza `last_modified_at`.
   - Si el cambio es relevante, publica un anuncio en LinkedIn.
   - Si el cambio introduce un nuevo concepto arquitectónico, escribe un artículo corto para DEV/LinkedIn (perenne, sin fechas).

---

## 9. Consideraciones finales

- **La documentación técnica es para arquitectos y desarrolladores.** No la disfraces de artículo divulgativo. El aviso de "documentación viva" gestiona expectativas.
- **Los artículos son para el público general.** Explican el "por qué" y el "qué", no el "cómo exacto". El "cómo" está en la documentación.
- **El enlace dual (vivo + snapshot) es una seña de identidad.** Refuerza tu perfil de arquitecto meticuloso y honesto. Úsalo en los artículos que traten temas donde la trazabilidad sea valiosa (arquitectura Albert, memoria narrativa, etc.).

# Anexo: Transparencia sobre el origen de la documentación técnica

Este anexo complementa la propuesta principal de migración, añadiendo un componente estratégico y ético: **cómo comunicar de forma transparente que la documentación técnica ha sido generada con asistencia de IA, sin que ello reste valor al contenido ni al proyecto.**

---

## 1. El principio: honestidad radical como activo, no como limitación

En tus artículos has defendido la idea de que la IA es una herramienta, no un sustituto del criterio humano. Has escrito sobre los límites de los LLMs para programar, sobre la necesidad de supervisión y sobre la importancia de no vender humo. Esta misma filosofía debe aplicarse a la documentación técnica de Noema.

**No se trata de ocultar el proceso, sino de explicitarlo.** La documentación técnica generada con IA no es inferior; es diferente. Su valor reside en la precisión, la exhaustividad y la velocidad de producción. El valor añadido del arquitecto está en la definición de la estructura, la validación del contenido y la toma de decisiones sobre qué incluir y qué destacar.

Si no declaras el proceso, cualquiera que reconozca el estilo de un LLM (y hoy en día es fácil) asumirá que has delegado la documentación sin supervisión. Si lo declaras, estás mostrando un uso profesional y consciente de la herramienta, con supervisión y criterio. La percepción cambia radicalmente.

---

## 2. Los elementos de transparencia

### 2.1. Declaración en la landing page de Noema

En la página principal de Noema, incluye una sección que explique el proceso de elaboración de la documentación técnica. Esta declaración debe ser:

- **Visible:** no la escondas en una nota al pie. Ponla en un lugar destacado, cerca del índice de documentación.
- **Honesta:** di claramente que los borradores se generan con IA y que tú los revisas y validas.
- **Contextualizada:** explica por qué la documentación tiene ese tono (porque es una especificación técnica, no un artículo literario).
- **Orientativa:** si el lector busca una lectura más narrativa, enlaza a tus artículos.

**Ejemplo de declaración:**

> **📄 Sobre esta documentación técnica**
>
> La documentación que encontrarás aquí son **especificaciones técnicas** del proyecto Noema. Su objetivo es la precisión y la exhaustividad, no el estilo literario.
>
> **Proceso de elaboración:**
> - La estructura inicial, el índice y los borradores completos se generan con asistencia de modelos de lenguaje (LLMs) a partir de los fuentes del proyecto.
> - **Yo reviso, valido y ajusto cada documento.** La decisión final sobre qué incluir, cómo estructurarlo y qué aspectos técnicos destacar es siempre mía.
> - El resultado es un documento que combina la velocidad de la generación automática con el criterio y la experiencia de un arquitecto de software.
>
> Si buscas una lectura más narrativa, consulta los [artículos](https://jjdelcerro.github.io/blog/) enlazados en esta página.

### 2.2. Aviso en cada documento técnico

Además de la declaración global en la landing, cada documento técnico debe incluir un pequeño aviso que indique su naturaleza y el proceso de revisión.

**Opciones:**

- **Opción A (visible y constante):** un bloque de texto fijo al inicio de cada documento, justo después del título.

> **📄 Especificación técnica**  
> *Documento generado con asistencia de IA, revisado y validado por el arquitecto del sistema. Su objetivo es la precisión técnica, no el estilo literario. Última revisión: 2026-08-15.*

- **Opción B (discreta, al final):** una nota al pie del documento.

> *Este documento es una especificación técnica. Su redacción ha sido asistida por IA y validada por el autor. Si encuentras algún error, por favor, abre un issue en el repositorio.*

- **Opción C (técnica, en el frontmatter):** si usas Jekyll, el aviso se renderiza automáticamente desde el layout.

```yaml
---
layout: doc
title: Sistema sensorial
permalink: /noema/docs/sensores/
disclaimer: true
last_modified_at: 2026-08-15
---
```

### 2.3. Coherencia entre la declaración y el contenido

La declaración debe ser coherente con el contenido de los documentos. Si un documento tiene un tono muy "manual de referencia", la declaración explica por qué. Si tiene algún comentario personal o una decisión de diseño no obvia, la declaración refuerza que eso ha sido añadido por ti.

No hay contradicción entre "generado con IA" y "revisado por mí". La declaración las reconcilia.

---

## 3. Implementación técnica en Jekyll

Para que el aviso sea fácil de mantener y aparezca en todos los documentos técnicos sin duplicar código, puedes implementarlo en el layout de Jekyll.

### 3.1. Layout `doc.html`

Crea un layout específico para la documentación técnica en `_layouts/doc.html`:

```html
---
layout: base
---

<article class="doc">
  <h1>{{ page.title }}</h1>

  {% if page.disclaimer != false %}
  <div class="doc-disclaimer">
    <strong>📄 Especificación técnica</strong><br>
    Documento generado con asistencia de IA, revisado y validado por el arquitecto del sistema.
    Su objetivo es la precisión técnica, no el estilo literario.
    {% if page.last_modified_at %}
    Última revisión: {{ page.last_modified_at | date: "%d-%m-%Y" }}
    {% endif %}
  </div>
  {% endif %}

  {{ content }}

  <hr>
  <p class="doc-footer">
    ¿Has encontrado un error? <a href="{{ site.github_repo }}/issues">Abre un issue</a> en el repositorio.
  </p>
</article>
```

### 3.2. Uso en cada documento

Cada archivo `.md` en `noema/docs/` usará este layout y podrá opcionalmente desactivar el aviso si se prefiere (aunque no es recomendable):

```yaml
---
layout: doc
title: Sistema sensorial
permalink: /noema/docs/sensores/
last_modified_at: 2026-08-15
---
```

### 3.3. Estilo CSS para el aviso

Añade un poco de estilo para que el aviso sea visible pero no intrusivo (por ejemplo, un recuadro con color de fondo suave y borde):

```css
.doc-disclaimer {
  background-color: #f8f9fa;
  border-left: 4px solid #6c757d;
  padding: 1rem 1.5rem;
  margin: 1.5rem 0;
  font-size: 0.95rem;
  color: #495057;
}
```

---

## 4. Estrategia de comunicación para los artículos

En los artículos cortos de DEV/LinkedIn que publiques sobre Noema, puedes incluir una nota breve sobre la documentación, sin que sea el foco del artículo:

> *"La documentación técnica completa de Noema está disponible en mi web. Está redactada como especificación, con asistencia de IA y validación manual, priorizando la precisión sobre el estilo."*

Esto no es un "spoiler", sino una información de contexto que refuerza la transparencia y prepara al lector para el formato que encontrará si hace clic en los enlaces.

---

## 5. Por qué esta estrategia es beneficiosa para tu perfil

1. **Refuerza tu posicionamiento como arquitecto:** muestras que usas IA como herramienta, no como muleta. Sabes cuándo delegar la redacción y cuándo supervisar.
2. **Evita críticas:** nadie puede acusarte de "ocultar" el uso de IA porque lo has declarado abiertamente.
3. **Genera confianza:** la transparencia sobre el proceso de elaboración de la documentación es una señal de honestidad profesional.
4. **Alinea la documentación con tu filosofía:** si en tus artículos hablas de no vender humo, aplicarlo a tu documentación es la mejor demostración práctica.
5. **Protege tu voz personal:** al distinguir claramente entre artículos (tu voz) y documentación técnica (especificación), no hay confusión ni dilución de tu estilo.

---

## 6. Resumen de acciones

1. **Añade la declaración de transparencia en la landing page de Noema** (sección visible).
2. **Implementa el aviso automático en el layout `doc.html` de Jekyll** para que aparezca en todos los documentos técnicos.
3. **Añade `last_modified_at` en el frontmatter de cada documento** para que la fecha de revisión sea visible.
4. **En los artículos de DEV/LinkedIn, incluye una nota breve** sobre el formato de la documentación (especificación, generada con asistencia de IA y validada).
5. **No modifiques el contenido de los documentos existentes.** La declaración y el aviso son suficientes para enmarcarlos correctamente.

# Anexo: Transparencia sobre el origen de la documentación técnica

Este anexo complementa la propuesta principal de migración, añadiendo un componente estratégico y ético: **cómo comunicar de forma transparente que la documentación técnica ha sido generada con asistencia de IA, sin que ello reste valor al contenido ni al proyecto.**

---

## 1. El principio: honestidad radical como activo, no como limitación

En tus artículos has defendido la idea de que la IA es una herramienta, no un sustituto del criterio humano. Has escrito sobre los límites de los LLMs para programar, sobre la necesidad de supervisión y sobre la importancia de no vender humo. Esta misma filosofía debe aplicarse a la documentación técnica de Noema.

**No se trata de ocultar el proceso, sino de explicitarlo.** La documentación técnica generada con IA no es inferior; es diferente. Su valor reside en la precisión, la exhaustividad y la velocidad de producción. El valor añadido del arquitecto está en la definición de la estructura, la validación del contenido y la toma de decisiones sobre qué incluir y qué destacar.

Si no declaras el proceso, cualquiera que reconozca el estilo de un LLM (y hoy en día es fácil) asumirá que has delegado la documentación sin supervisión. Si lo declaras, estás mostrando un uso profesional y consciente de la herramienta, con supervisión y criterio. La percepción cambia radicalmente.

---

## 2. Los elementos de transparencia

### 2.1. Declaración en la landing page de Noema

En la página principal de Noema, incluye una sección que explique el proceso de elaboración de la documentación técnica. Esta declaración debe ser:

- **Visible:** no la escondas en una nota al pie. Ponla en un lugar destacado, cerca del índice de documentación.
- **Honesta:** di claramente que los borradores se generan con IA y que tú los revisas y validas.
- **Contextualizada:** explica por qué la documentación tiene ese tono (porque es una especificación técnica, no un artículo literario).
- **Orientativa:** si el lector busca una lectura más narrativa, enlaza a tus artículos.

**Ejemplo de declaración:**

> **📄 Sobre esta documentación técnica**
>
> La documentación que encontrarás aquí son **especificaciones técnicas** del proyecto Noema. Su objetivo es la precisión y la exhaustividad, no el estilo literario.
>
> **Proceso de elaboración:**
> - La estructura inicial, el índice y los borradores completos se generan con asistencia de modelos de lenguaje (LLMs) a partir de los fuentes del proyecto.
> - **Yo reviso, valido y ajusto cada documento.** La decisión final sobre qué incluir, cómo estructurarlo y qué aspectos técnicos destacar es siempre mía.
> - El resultado es un documento que combina la velocidad de la generación automática con el criterio y la experiencia de un arquitecto de software.
>
> Si buscas una lectura más narrativa, consulta los [artículos](https://jjdelcerro.github.io/blog/) enlazados en esta página.

### 2.2. Aviso en cada documento técnico

Además de la declaración global en la landing, cada documento técnico debe incluir un pequeño aviso que indique su naturaleza y el proceso de revisión.

**Opciones:**

- **Opción A (visible y constante):** un bloque de texto fijo al inicio de cada documento, justo después del título.

> **📄 Especificación técnica**  
> *Documento generado con asistencia de IA, revisado y validado por el arquitecto del sistema. Su objetivo es la precisión técnica, no el estilo literario. Última revisión: 2026-08-15.*

- **Opción B (discreta, al final):** una nota al pie del documento.

> *Este documento es una especificación técnica. Su redacción ha sido asistida por IA y validada por el autor. Si encuentras algún error, por favor, abre un issue en el repositorio.*

- **Opción C (técnica, en el frontmatter):** si usas Jekyll, el aviso se renderiza automáticamente desde el layout.

```yaml
---
layout: doc
title: Sistema sensorial
permalink: /noema/docs/sensores/
disclaimer: true
last_modified_at: 2026-08-15
---
```

### 2.3. Coherencia entre la declaración y el contenido

La declaración debe ser coherente con el contenido de los documentos. Si un documento tiene un tono muy "manual de referencia", la declaración explica por qué. Si tiene algún comentario personal o una decisión de diseño no obvia, la declaración refuerza que eso ha sido añadido por ti.

No hay contradicción entre "generado con IA" y "revisado por mí". La declaración las reconcilia.

---

## 3. Implementación técnica en Jekyll

Para que el aviso sea fácil de mantener y aparezca en todos los documentos técnicos sin duplicar código, puedes implementarlo en el layout de Jekyll.

### 3.1. Layout `doc.html`

Crea un layout específico para la documentación técnica en `_layouts/doc.html`:

```html
---
layout: base
---

<article class="doc">
  <h1>{{ page.title }}</h1>

  {% if page.disclaimer != false %}
  <div class="doc-disclaimer">
    <strong>📄 Especificación técnica</strong><br>
    Documento generado con asistencia de IA, revisado y validado por el arquitecto del sistema.
    Su objetivo es la precisión técnica, no el estilo literario.
    {% if page.last_modified_at %}
    Última revisión: {{ page.last_modified_at | date: "%d-%m-%Y" }}
    {% endif %}
  </div>
  {% endif %}

  {{ content }}

  <hr>
  <p class="doc-footer">
    ¿Has encontrado un error? <a href="{{ site.github_repo }}/issues">Abre un issue</a> en el repositorio.
  </p>
</article>
```

### 3.2. Uso en cada documento

Cada archivo `.md` en `noema/docs/` usará este layout y podrá opcionalmente desactivar el aviso si se prefiere (aunque no es recomendable):

```yaml
---
layout: doc
title: Sistema sensorial
permalink: /noema/docs/sensores/
last_modified_at: 2026-08-15
---
```

### 3.3. Estilo CSS para el aviso

Añade un poco de estilo para que el aviso sea visible pero no intrusivo (por ejemplo, un recuadro con color de fondo suave y borde):

```css
.doc-disclaimer {
  background-color: #f8f9fa;
  border-left: 4px solid #6c757d;
  padding: 1rem 1.5rem;
  margin: 1.5rem 0;
  font-size: 0.95rem;
  color: #495057;
}
```

---

## 4. Estrategia de comunicación para los artículos

En los artículos cortos de DEV/LinkedIn que publiques sobre Noema, puedes incluir una nota breve sobre la documentación, sin que sea el foco del artículo:

> *"La documentación técnica completa de Noema está disponible en mi web. Está redactada como especificación, con asistencia de IA y validación manual, priorizando la precisión sobre el estilo."*

Esto no es un "spoiler", sino una información de contexto que refuerza la transparencia y prepara al lector para el formato que encontrará si hace clic en los enlaces.

---

## 5. Por qué esta estrategia es beneficiosa para tu perfil

1. **Refuerza tu posicionamiento como arquitecto:** muestras que usas IA como herramienta, no como muleta. Sabes cuándo delegar la redacción y cuándo supervisar.
2. **Evita críticas:** nadie puede acusarte de "ocultar" el uso de IA porque lo has declarado abiertamente.
3. **Genera confianza:** la transparencia sobre el proceso de elaboración de la documentación es una señal de honestidad profesional.
4. **Alinea la documentación con tu filosofía:** si en tus artículos hablas de no vender humo, aplicarlo a tu documentación es la mejor demostración práctica.
5. **Protege tu voz personal:** al distinguir claramente entre artículos (tu voz) y documentación técnica (especificación), no hay confusión ni dilución de tu estilo.

---

## 6. Resumen de acciones

1. **Añade la declaración de transparencia en la landing page de Noema** (sección visible).
2. **Implementa el aviso automático en el layout `doc.html` de Jekyll** para que aparezca en todos los documentos técnicos.
3. **Añade `last_modified_at` en el frontmatter de cada documento** para que la fecha de revisión sea visible.
4. **En los artículos de DEV/LinkedIn, incluye una nota breve** sobre el formato de la documentación (especificación, generada con asistencia de IA y validada).
5. **No modifiques el contenido de los documentos existentes.** La declaración y el aviso son suficientes para enmarcarlos correctamente.

