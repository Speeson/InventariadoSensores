# Plantilla de Migracion Glass-Listado (Base Eventos y Productos)

Usa esta plantilla cuando quieras aplicar el mismo patron a otro modulo (por ejemplo, `Stocks`).

## 1. Objetivo

Unificar la experiencia de listado con:
- Acciones de crear y filtrar en popups maestras.
- Estetica glass/liquid en header y card de listado.
- Paginacion consistente.
- Altura adaptativa + centrado vertical con pocos registros.
- Filtros que siempre partan del dataset base (sin encadenar sobre resultados ya filtrados).

## 2. Checklist de Implementacion

1. Reemplazar flujo inline/desplegable por popups.
- Conectar accion superior `Crear` a popup maestra de creacion.
- Conectar accion superior `Buscar/Filtrar` a popup maestra de filtros.
- Mantener referencias de dialogo (`createDialog`, `searchDialog`) y cierre consistente.

2. Eliminar/ocultar legacy inline.
- Quitar el bloque desplegable principal de crear/filtrar (XML + listeners) para evitar doble flujo.
- Quitar tambien el bloque local de acciones de crear/filtrar de la actividad (si existe), para que la pantalla quede centrada en el listado y no duplique acciones.
- Las acciones de crear/filtrar deben quedar solo en popups maestras (top bar / accion global).

3. Aplicar estilo glass al contenedor del listado.
- Card/listado con `bg_liquid_popup_card`.
- Bordes, radios y separadores en linea con estilo liquid.
- Integrar visualmente con top bar global.

4. Aplicar estilo glass a la cabecera del listado.
- Cabecera unificada (tipografia, spacing, contraste).
- Botones de paginacion en estilo liquid.
- Boton refresh con icono/tint coherente en claro/oscuro.

5. Unificar paginacion.
- Mostrar `Pagina X/X`.
- Mostrar `Mostrando X/X`.
- Botones prev/next habilitados o deshabilitados segun offset y total.

6. Altura adaptativa del bloque listado.
- Si `visibleCount in 1..pageSize`: card en `wrap_content`, lista sin peso extra.
- Si supera ese rango: card y lista en modo expandido (`weight=1`).
- Ajustar `nestedScrollingEnabled` segun modo compacto/expandido.

7. Centrado vertical cuando hay pocos registros.
- Activar `topSpacer` y `bottomSpacer` con peso para centrar el bloque.
- Desactivar spacers cuando el listado pasa a modo expandido.

8. Corregir comportamiento de filtros/busqueda.
- Cada nueva busqueda debe partir de dataset base recargado.
- Aplicar filtro despues de cargar base, no sobre subset previamente filtrado.
- Reiniciar `filteredOffset` al aplicar filtros nuevos.

9. Mantener popup de edicion con estilo glass (si existe edicion en modulo).
- Inputs, dropdowns y acciones con look liquid.
- Boton cerrar consistente con popups maestras.
- Titulo y boton cerrar sin solaparse.

## 3. Checkpoints de Verificacion Rapida

1. UI de acciones.
- Pulsar crear abre popup maestra.
- Pulsar filtrar abre popup maestra.
- No aparece formulario inline legacy.
- No aparece bloque local duplicado de crear/filtrar en la actividad (solo listado).

2. UI de listado.
- Header y card muestran estilo glass.
- Refresh/paginacion con estilo y estados correctos.

3. Comportamiento de listado.
- Con pocos items, el bloque queda centrado verticalmente.
- Con muchos items, ocupa alto util y scroll fluido.

4. Filtros y paginacion.
- Nueva busqueda no hereda filtros anteriores por error.
- `Pagina` y `Mostrando` cuadran con los datos visibles.

## 4. Comando de Uso

Cuando quieras aplicarla, pide:
- `Aplica plantilla glass-listado a Stocks`
- `Aplica plantilla glass-listado a [Modulo]`
