
# plan push/pop 

**Conceptualmente encaja bien.** El flujo real es "estoy en A, descubro que necesito hacer B primero, hago B, vuelvo a A". Hoy eso son dos pares de `setPlanState` manuales y recordar el orden tú. Un stack lo automatiza. Para proyectos con prerrequisitos descubiertos sobre la marcha, es la forma natural.

**Lo importante: es una capa encima de la máquina de estados, no un reemplazo.** `pushPlan(B)` internamente hace `setPlanState(A, "stopped")` + `setPlanState(B, "running")` y apunta `A` en una pila. `popPlan()` hace lo inverso. `PlanCore` no cambia; solo se añaden dos métodos y un fichero `.noema-agent/plans/.stack`. Eso significa que **puedes posponerlo sin coste**: implementa la validación de "un solo `running`" en la segunda vuelta tal como está planeada, y cuando quieras el stack, lo añades encima.

**Cuatro decisiones que tendrás que cerrar cuando lo abordes:**

1. **¿`suspended` como estado nuevo, o reusar `stopped`?** Mi voto: reusar `stopped`. El stack ya guarda el orden; no necesitas distinguir "parado por el usuario" de "parado porque hay otro encima". Menos estado, menos transiciones, mismos casos cubiertos.

2. **¿Auto-pop al cerrar el plan?** Si `setPlanState(top, "done")` y hay algo en la pila, ¿resucitas automáticamente el anterior? Mi voto: no. `popPlan()` explícito. Si el LLM cierra A sin darse cuenta de que había B en la pila, prefieres que te pregunte a que reactive B por su cuenta.

3. **Interacción con `listPlans`.** La regla actual ("`running` no cargado → `stopped`") sigue siendo correcta, pero necesitas que `listPlans` muestre también la pila: `"A: stopped (en pila, debajo de B)"`. Si no, el LLM ve dos `stopped` y no sabe cuál retomar.

4. **Plan desaparecido de la pila.** Si `popPlan` encuentra que el plan de abajo se borró del disco, debe saltarlo (o fallar con mensaje claro, y dejar la pila consistente). Un stack que se corrompe al primer borrado es peor que no tener stack.

