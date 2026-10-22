# Tareas pendientes

Numeracion historica conservada: los huecos corresponden a tareas ya cerradas o
descartadas, no renumerar. Hay referencias por numero en el historial de sesiones y las
tareas retiradas siguen recuperables en el control de versiones del proyecto.

---

26. [ ] (alta) index.html: envolver el area en .work-area con .area-tabbar y .area-body.

43. [ ] (media) DEUDA: invalidacion de la cache cuando el agente escribe con file_write.

49. [ ] (media) Verificar la busqueda con root vacio, root inexistente y corpus vacio. (FileFuzzySearchUtils corta el walk en MAX_FILES_TO_SCAN=100 y devuelve conjunto vacio si la carpeta no existe; comprobar que resolveBase no propaga excepcion y que el handler responde 200 con total 0 en lugar de 500)

55. [ ] (media) Difuso sin resultados perceptibles. (Hipotesis confirmada en parte: NoteStore serializa el contenido en CDATA dentro del XML y el glob es **/*.xml, de modo que el chunker por lineas embebia la nota entera con etiquetas XML dentro y una consulta corta rara vez superaba MIN_SIMILARITY 0.5. IMPLEMENTADO: FileFuzzySearchUtils acepta un ReaderTransformer opcional (interfaz anidada con throws IOException) y NoteSearch le pasa this::toSearchableText, que reconstruye la Note desde el reader y devuelve titulo + contenido en un StringReader, de modo que el troceado recupera los saltos de linea reales del contenido. PENDIENTE: verificar en navegador con corpus real; si sigue sin devolver nada, mirar si en var/cache/notes-search hay un .idx con MODE fuzzy y TOTAL 0)

56. [ ] (baja) Aplicar treeLabel a los ficheros del arbol del workspace. (notes-ui.js ya envuelve el titulo con treeLabel en la rama File, pero workspace-ui.js sigue construyendo `new FileClass([], item.name)` con el nombre crudo, y vendor/file-tree/prod.js le aplica encodeURIComponent antes de pintarlo, de modo que "Mi nota.txt" se ve como "Mi%20nota.txt". Importar tree-label.js y envolver solo la rama File, conservando absolutePath intacto; las carpetas no se sanean porque Folder ya pinta el nombre original. Mismo trade-off ya aceptado en notas: en el arbol se pierden tildes y en el editor se siguen viendo.)

57. [ ] (media) DEUDA: unificar los dialogos de confirmacion. (confirmLeaveNotes sigue usando window.confirm, mientras que abrir otra nota, recargar y cerrar nota ya usan confirmDiscardDialog. OBSTACULO: area-tabs.js evalua el hook de forma sincrona con `current.confirmLeave() !== false`; si el hook devuelve una Promise, la comparacion es siempre true y el cambio de pestana se autoriza aunque el usuario cancele, con perdida de cambios silenciosa. Hace falta: 1) hacer asincrona la decision en area-tabs.js (await del hook, admitiendo false o Promise que resuelve false), 2) actualizar el contrato documentado en su cabecera, 3) revisar confirmLeaveWorkspace en editor-ui.js:337 y el registro de main.js:50-52. Mismo patron de bug que ya aparecio en handleReload y en el %20 del arbol: no dar por bueno un valor sin comprobar que el llamante lo consume como se espera.)
