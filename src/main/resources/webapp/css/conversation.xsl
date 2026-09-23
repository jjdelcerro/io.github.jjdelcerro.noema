<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:output method="html" encoding="UTF-8" indent="yes" />

  <xsl:template match="/conversation">
    <div class="conversation-container">
      <style>
        .conversation-container {
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
          background-color: #1e1e1e;
          color: #d4d4d4;
          padding: 16px;
          line-height: 1.5;
          max-width: 100%;
          box-sizing: border-box;
        }

        /* Common turn card styles */
        .card {
          background-color: #252526;
          border-radius: 8px;
          margin-bottom: 14px;
          padding: 12px 16px;
          border-left: 4px solid #555;
          box-shadow: 0 2px 4px rgba(0,0,0,0.2);
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

        /* Role-specific color accents (matching Noema Swing colors) */
        .role-user {
          border-left-color: #3c8cc8; /* rgb(60, 140, 200) */
        }
        .role-user .card-header {
          color: #61afef;
        }

        .role-assistant {
          border-left-color: #50aa6e; /* rgb(80, 170, 110) */
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
          background-color: #202224;
        }
        .tool-result-card .card-header {
          color: #e5c07b;
        }

        /* Thinking block (collapsible) */
        details.thinking-box {
          background-color: #1a221d;
          border: 1px solid #2d4534;
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

        /* Tool call inside assistant turn */
        .tool-call-box {
          background-color: #1e2227;
          border: 1px solid #333842;
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

        /* Badges */
        .badge {
          background-color: #3a3f4b;
          color: #abb2bf;
          padding: 2px 6px;
          border-radius: 4px;
          font-family: monospace;
          font-size: 0.85em;
        }

        /* Preformatted verbatim text */
        pre.content-text {
          font-family: "JetBrains Mono", "Fira Code", Consolas, Menlo, Monaco, monospace;
          background-color: #181818;
          border: 1px solid #2d2d2d;
          border-radius: 4px;
          padding: 10px;
          overflow-x: auto;
          white-space: pre-wrap;
          word-break: break-word;
          margin: 6px 0 0 0;
          font-size: 0.9em;
          color: #cccccc;
        }

        /* Generic transcode tree styles (JSON converted to XML) */
        .tree-node {
          margin-left: 12px;
          border-left: 1px dashed #3a3f4b;
          padding-left: 8px;
          margin-top: 4px;
        }
        .tree-leaf {
          margin-left: 12px;
          font-family: monospace;
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
        pre.tree-val-multiline {
          background-color: #141414;
          border: 1px solid #282c34;
          border-radius: 4px;
          padding: 8px;
          margin: 4px 0;
          white-space: pre-wrap;
          color: #abb2bf;
          font-size: 0.9em;
        }
      </style>

      <xsl:apply-templates select="system_context | turn | tool_result" />
    </div>
  </xsl:template>

  <!-- Template: System Context / Instructions -->
  <xsl:template match="system_context">
    <div class="card role-system">
      <details>
        <summary class="card-header" style="cursor: pointer;">⚙️ System Context / Directives</summary>
        <pre class="content-text"><xsl:value-of select="content" /></pre>
      </details>
    </div>
  </xsl:template>

  <!-- Template: User Turn -->
  <xsl:template match="turn[@role='user']">
    <div class="card role-user">
      <div class="card-header">
        <span>👤 Usuario</span>
        <span class="badge">Turno <xsl:value-of select="@index" /></span>
      </div>
      <pre class="content-text"><xsl:value-of select="content" /></pre>
    </div>
  </xsl:template>

  <!-- Template: Assistant Turn -->
  <xsl:template match="turn[@role='assistant']">
    <div class="card role-assistant">
      <div class="card-header">
        <span>🤖 Asistente (Noema)</span>
        <span class="badge">Turno <xsl:value-of select="@index" /></span>
      </div>

      <!-- Thinking block if present -->
      <xsl:if test="thinking">
        <details class="thinking-box">
          <summary>🧠 Razonamiento interno (Thinking)</summary>
          <pre class="content-text"><xsl:value-of select="thinking" /></pre>
        </details>
      </xsl:if>

      <!-- Tool Execution Requests -->
      <xsl:apply-templates select="tool_call" />

      <!-- Final/Intermediate Speech text -->
      <xsl:if test="content">
        <pre class="content-text"><xsl:value-of select="content" /></pre>
      </xsl:if>
    </div>
  </xsl:template>

  <!-- Template: Tool Call inside Assistant Turn -->
  <xsl:template match="tool_call">
    <div class="tool-call-box">
      <div class="tool-call-header">
        <span>⚙️ Llamada a Herramienta: </span>
        <span class="badge" style="color: #e5c07b;"><xsl:value-of select="@name" /></span>
        <span class="badge" style="margin-left: 6px; font-size: 0.75em;"><xsl:value-of select="@id" /></span>
      </div>
      <div class="arguments-container">
        <xsl:apply-templates select="arguments/*" mode="render-tree" />
      </div>
    </div>
  </xsl:template>

  <!-- Template: Tool Execution Result -->
  <xsl:template match="tool_result">
    <div class="card tool-result-card">
      <details>
        <summary class="card-header" style="cursor: pointer;">
          <span>📄 Resultado Herramienta: <xsl:value-of select="@name" /></span>
          <span class="badge"><xsl:value-of select="@id" /></span>
        </summary>
        <div style="margin-top: 8px;">
          <xsl:apply-templates select="output/*" mode="render-tree" />
        </div>
      </details>
    </div>
  </xsl:template>

  <!-- Recursive Generic Template for Transcoded JSON Nodes -->
  <xsl:template match="*" mode="render-tree">
    <xsl:choose>
      <!-- Element has child elements: render branch -->
      <xsl:when test="*">
        <div class="tree-node">
          <span class="tree-key"><xsl:value-of select="name()" />:</span>
          <div class="tree-children">
            <xsl:apply-templates select="*" mode="render-tree" />
          </div>
        </div>
      </xsl:when>

      <!-- Leaf element with text content -->
      <xsl:otherwise>
        <div class="tree-leaf">
          <span class="tree-key"><xsl:value-of select="name()" />: </span>
          <xsl:choose>
            <!-- Multiline string -> render inside <pre> -->
            <xsl:when test="contains(., '&#10;')">
              <pre class="tree-val-multiline"><xsl:value-of select="." /></pre>
            </xsl:when>
            <!-- Single line primitive -> render inline -->
            <xsl:otherwise>
              <span class="tree-val"><xsl:value-of select="." /></span>
            </xsl:otherwise>
          </xsl:choose>
        </div>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

</xsl:stylesheet>
``
