<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:output method="html" encoding="UTF-8" indent="yes" />

  <xsl:template match="/conversation">
    <div class="conversation-container">
      <style>
        .conversation-container {
        font-family: var(--font, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif);
        background-color: transparent;
        color: var(--ink, #e9eaf0);
        padding: 0;
        line-height: 1.5;
        max-width: 100%;
        box-sizing: border-box;
        }

        /* Tarjeta base para cada turno */
        .card {
        background-color: var(--surface, #171a21);
        border: 1px solid var(--line, rgba(255, 255, 255, 0.08));
        border-left: 4px solid #555;
        border-radius: 10px;
        margin-bottom: 16px;
        padding: 14px 18px;
        box-shadow: 0 4px 14px rgba(0, 0, 0, 0.25);
        }

        .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 0.85em;
        font-weight: 600;
        margin-bottom: 8px;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        }

        /* Summary como cabecera colapsable: sin marcador nativo, con chevron propio */
        summary.card-header {
        list-style: none;
        cursor: pointer;
        }
        summary.card-header::-webkit-details-marker {
        display: none;
        }
        summary.card-header::marker {
        content: "";
        }
        details.card > summary.card-header {
        margin-bottom: 0;
        }

        .card-header-left {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        min-width: 0;
        }

        .card-header .chevron {
        display: inline-block;
        width: 1em;
        text-align: center;
        color: var(--muted, #9296a5);
        font-size: 0.9em;
        transform-origin: 50% 50%;
        transition: transform 0.15s ease;
        }
        .card-header .chevron::before {
        content: "▸";
        }
        details[open] > summary.card-header .chevron {
        transform: rotate(90deg);
        }

        /* Acentos visuales según el rol */
        .role-user {
        border-left-color: #3c8cc8;
        }
        .role-user .card-header {
        color: #61afef;
        }

        .role-assistant {
        border-left-color: #50aa6e;
        }
        .role-assistant .card-header {
        color: #78d296;
        }

        .role-system {
        border-left-color: #888888;
        }
        .role-system .card-header {
        color: #aaaaaa;
        }

        .tool-result-card {
        border-left-color: #d19a66;
        background-color: #14171d;
        }
        .tool-result-card .card-header {
        color: #e5c07b;
        }

        /* Bloque colapsable de Thinking */
        details.thinking-box {
        background-color: rgba(80, 170, 110, 0.06);
        border: 1px solid rgba(80, 170, 110, 0.2);
        border-radius: 6px;
        margin: 8px 0 12px 0;
        padding: 8px 12px;
        }
        details.thinking-box summary {
        cursor: pointer;
        font-weight: 600;
        color: #88c09a;
        outline: none;
        font-size: 0.9em;
        }
        details.thinking-box summary:hover {
        color: #a8e6be;
        }

        /* Llamadas a herramientas dentro del turno del modelo */
        .tool-call-box {
        background-color: var(--surface2, #1d2029);
        border: 1px solid var(--line, rgba(255, 255, 255, 0.08));
        border-radius: 6px;
        margin: 10px 0;
        padding: 10px;
        }
        .tool-call-header {
        font-weight: 600;
        font-size: 0.9em;
        color: #e5c07b;
        margin-bottom: 6px;
        }

        /* Badges e identificadores */
        .badge {
        background-color: rgba(255, 255, 255, 0.08);
        color: var(--muted, #9296a5);
        padding: 2px 6px;
        border-radius: 4px;
        font-family: var(--mono, monospace);
        font-size: 0.85em;
        }

        /* Bloques de texto preformateado */
        pre.content-text {
        font-family: var(--mono, "SFMono-Regular", Consolas, monospace);
        background-color: #0b0d10;
        border: 1px solid var(--line, rgba(255, 255, 255, 0.08));
        border-radius: 6px;
        padding: 12px;
        overflow-x: auto;
        white-space: pre-wrap;
        word-break: break-word;
        margin: 6px 0 0 0;
        font-size: 0.9em;
        color: var(--ink, #e9eaf0);
        }

        /* Estructura en árbol para argumentos transcritos */
        .tree-node {
        margin-left: 12px;
        border-left: 1px dashed rgba(255, 255, 255, 0.15);
        padding-left: 8px;
        margin-top: 4px;
        }
        .tree-leaf {
        margin-left: 12px;
        font-family: var(--mono, monospace);
        font-size: 0.9em;
        margin-top: 2px;
        }
        .tree-key {
        color: #e06c75;
        font-weight: 600;
        }
        .tree-val {
        color: #98c379;
        }
        .tree-val-empty {
        color: var(--faint, #676b7a);
        font-style: italic;
        }
        pre.tree-val-multiline {
        background-color: #0b0d10;
        border: 1px solid var(--line, rgba(255, 255, 255, 0.08));
        border-radius: 4px;
        padding: 8px;
        margin: 4px 0;
        white-space: pre-wrap;
        color: var(--ink, #abb2bf);
        font-size: 0.9em;
        }
      </style>

      <xsl:apply-templates select="turn" />
    </div>
  </xsl:template>

  <!-- Template: System Context / Instructions (collapsed by default) -->
  <xsl:template match="turn[@role='system']">
    <details class="card role-system">
      <summary class="card-header">
        <span class="card-header-left">
          <span class="chevron"></span>
          <span>⚙️ System Context / Directives</span>
        </span>
        <span class="badge">Turno <xsl:value-of select="@index" /></span>
      </summary>
      <pre class="content-text">
        <xsl:value-of select="content" />
      </pre>
    </details>
  </xsl:template>

  <!-- Template: User Turn (open by default) -->
  <xsl:template match="turn[@role='user']">
    <details class="card role-user" open="open">
      <summary class="card-header">
        <span class="card-header-left">
          <span class="chevron"></span>
          <span>👤 Usuario</span>
        </span>
        <span class="badge">Turno <xsl:value-of select="@index" /></span>
      </summary>
      <pre class="content-text">
        <xsl:value-of select="content" />
      </pre>
    </details>
  </xsl:template>

  <!-- Template: Assistant Turn (open by default) -->
  <xsl:template match="turn[@role='assistant']">
    <details class="card role-assistant" open="open">
      <summary class="card-header">
        <span class="card-header-left">
          <span class="chevron"></span>
          <span>🤖 Asistente (Noema)</span>
        </span>
        <span class="badge">Turno <xsl:value-of select="@index" /></span>
      </summary>

      <!-- Thinking block if present -->
      <xsl:if test="thinking">
        <details class="thinking-box">
          <summary>🧠 Razonamiento interno (Thinking)</summary>
          <pre class="content-text">
            <xsl:value-of select="thinking" />
          </pre>
        </details>
      </xsl:if>

      <!-- Tool Execution Requests -->
      <xsl:apply-templates select="tool_call" />

      <!-- Final/Intermediate Speech text -->
      <xsl:if test="content">
        <pre class="content-text">
          <xsl:value-of select="content" />
        </pre>
      </xsl:if>
    </details>
  </xsl:template>

  <!-- Template: Tool Call inside Assistant Turn -->
  <xsl:template match="tool_call">
    <details class="tool-call-box">
      <summary class="tool-call-header">🧠 
        <span>⚙️ Llamada a Herramienta: </span>
        <span class="badge" style="color: #e5c07b;">
          <xsl:value-of select="@name" />
        </span>
        <span class="badge" style="margin-left: 6px; font-size: 0.75em;">
          <xsl:value-of select="@id" />
        </span>
      </summary>
      <div style="margin-top: 8px;">
      <xsl:if test="arguments/*">
        <div class="arguments-container">
          <xsl:apply-templates select="arguments/*" mode="render-tree" />
        </div>
      </xsl:if>
      </div>
    </details>
  </xsl:template>
<!--
    <div class="tool-call-box">
      <div class="tool-call-header">
        <span>⚙️ Llamada a Herramienta: </span>
        <span class="badge" style="color: #e5c07b;">
          <xsl:value-of select="@name" />
        </span>
        <span class="badge" style="margin-left: 6px; font-size: 0.75em;">
          <xsl:value-of select="@id" />
        </span>
      </div>

      <xsl:if test="arguments/*">
        <div class="arguments-container">
          <xsl:apply-templates select="arguments/*" mode="render-tree" />
        </div>
      </xsl:if>
    </div>
  </xsl:template>
      -->

  <!-- Template: Tool Execution Result (collapsed by default) -->
  <xsl:template match="turn[@role='tool']">
    <details class="card tool-result-card">
      <summary class="card-header">
        <span class="card-header-left">
          <span class="chevron"></span>
          <span>📄 Resultado Herramienta: <xsl:value-of select="@name" /> </span>
          <span class="badge" style="margin-left: 6px; font-size: 0.75em;">
            <xsl:value-of select="@id" />
          </span>          
        </span>
        <span class="badge">
          <xsl:text>Turno </xsl:text>
          <xsl:value-of select="@index" />
        </span>
      </summary>
      <div style="margin-top: 8px;">
        <xsl:choose>
          <xsl:when test="output/*">
            <xsl:apply-templates select="output/*" mode="render-tree" />
          </xsl:when>
          <xsl:otherwise>
            <pre class="content-text">
              <xsl:value-of select="output" />
            </pre>
          </xsl:otherwise>
        </xsl:choose>
      </div>
    </details>
  </xsl:template>

  <!-- Recursive Generic Template for Transcoded JSON Nodes -->
  <xsl:template match="*" mode="render-tree">
    <xsl:choose>
      <!-- Element has child elements: render branch -->
      <xsl:when test="*">
        <div class="tree-node">
          <span class="tree-key">
            <xsl:value-of select="name()" />:</span>
          <div class="tree-children">
            <xsl:apply-templates select="*" mode="render-tree" />
          </div>
        </div>
      </xsl:when>

      <!-- Leaf element with text content -->
      <xsl:otherwise>
        <div class="tree-leaf">
          <span class="tree-key">
            <xsl:value-of select="name()" />: </span>
          <xsl:choose>
            <xsl:when test="normalize-space(.) = ''">
              <span class="tree-val-empty">(vacío)</span>
            </xsl:when>
            <xsl:when test="contains(., '&#10;')">
              <pre class="tree-val-multiline">
                <xsl:value-of select="." />
              </pre>
            </xsl:when>
            <xsl:otherwise>
              <span class="tree-val">
                <xsl:value-of select="." />
              </span>
            </xsl:otherwise>
          </xsl:choose>
        </div>
      </xsl:otherwise>      
    </xsl:choose>
  </xsl:template>

</xsl:stylesheet>
