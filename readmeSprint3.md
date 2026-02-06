# Sprint 3 - Cambios y decisiones

Fecha: 2026-02-05

## Movimientos: añadir `delta` para auditoría

Objetivo: conservar el signo del cambio de stock en todos los movimientos.

Reglas:
- `quantity` es magnitud positiva en IN/OUT/TRANSFER.
- `delta` es el cambio con signo:
  - IN: `delta = +quantity`
  - OUT: `delta = -quantity`
  - ADJUST: `delta = input` (puede ser + o -)
- En ADJUST, `quantity` también se guarda con signo (igual a `delta`).

Frontend:
- En Movimientos, el campo `cantidad` se usa como **delta** cuando el tipo es ADJUST.
- Hint ADJUST: `(-)Negativo para retirar | (+)Positivo para introducir`.

Backend:
- Nueva columna `movements.delta` (Integer).
- Migración backfill:
  - OUT: `delta = -quantity`
  - IN/ADJUST: `delta = quantity`
  - Nota: los ADJUST históricos no preservaban signo, por lo que su `delta` queda con el valor previo de `quantity`.
