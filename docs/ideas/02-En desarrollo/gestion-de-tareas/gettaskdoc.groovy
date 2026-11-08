
/*
 * Extrae secciones de un documento markdown delimitadas por encabezados.
 *
 * Variables inyectadas (todas opcionales):
 *   file   : ruta absoluta del documento. Por defecto, specs.md.
 *   titles : lista de titulos (o fragmentos) de seccion a extraer.
 *            Si se omite o viene vacia, imprime el indice de encabezados.
 *
 * Uso:
 *   variables = {"titles": ["Persistencia del estado", "Contrato de componentes"]}
 *   variables = {"file": "/ruta/otro.md", "titles": ["Estructura"]}
 *   variables = {}
 */

def DEFAULT_FILE = '/home/jjdelcerro/datos/devel/io.github.jjdelcerro.noema/plans/specs.md'

def docPath = binding.hasVariable('file') && file ? file.toString() : DEFAULT_FILE
def wanted = binding.hasVariable('titles') && titles ? titles : []
if (wanted instanceof CharSequence) {
    wanted = [ wanted.toString() ]
}

def lines = []
agent.fs.lines(docPath).each { lines << it }
if (lines.isEmpty()) {
    throw new IllegalStateException("Documento vacio o ilegible: " + docPath)
}

def headLevel = { String s ->
    int k = 0
    while (k < s.length() && s.charAt(k) == '#') {
        k++
    }
    if (k > 0 && k < s.length() && s.charAt(k) == ' ') {
        return k
    }
    return 0
}

def sectionEnd = { int start ->
    int level = headLevel(lines[start])
    for (int i = start + 1; i < lines.size(); i++) {
        int h = headLevel(lines[i])
        if (h > 0 && h <= level) {
            return i
        }
    }
    return lines.size()
}

def countTokens = { List body ->
    int chars = 0
    body.each { chars += it.length() + 1 }
    return (int) Math.round(chars / 4.0)
}

def headings = []
for (int i = 0; i < lines.size(); i++) {
    int level = headLevel(lines[i])
    if (level > 0) {
        headings << [ index: i, level: level, text: lines[i].trim() ]
    }
}

if (wanted.isEmpty()) {
    println "### " + docPath + " (" + lines.size() + " lineas, " + headings.size() + " encabezados)"
    headings.each { h ->
        int end = sectionEnd(h.index)
        int tok = countTokens(lines.subList(h.index, end))
        println String.format("L%-4d %-60s %3d lin ~%4d tok", h.index + 1, h.text, end - h.index, tok)
    }
    return "indice: " + headings.size() + " encabezados"
}

int found = 0
wanted.each { rawTitle ->
    String title = rawTitle.toString()
    def matches = headings.findAll { it.text.toLowerCase().contains(title.toLowerCase()) }
    println ""
    if (matches.isEmpty()) {
        println "### NO ENCONTRADA: " + title
        return
    }
    if (matches.size() > 1) {
        println "### AMBIGUA: " + title + " (" + matches.size() + " candidatas)"
        matches.each { println "    L" + (it.index + 1) + "  " + it.text }
        return
    }
    def h = matches[0]
    int end = sectionEnd(h.index)
    def body = lines.subList(h.index, end)
    println "### " + h.text + " (L" + (h.index + 1) + "-" + end + ", " + body.size() + " lineas, ~" + countTokens(body) + " tok)"
    body.each { println it }
    found++
}

if (found == 0) {
    throw new IllegalStateException("Ninguna seccion encontrada para: " + wanted)
}
return null
