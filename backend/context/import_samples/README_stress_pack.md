# Import CSV Stress Pack

Estos ficheros estan pensados para poner a prueba /imports/events/csv y /imports/transfers/csv con mezclas de filas validas, invalidas y review.

Notas:
- Los IDs usados (category_id/location_id = 1 y 2) pueden no existir en tu BD.
- Si no existen, veras errores de validacion (es esperado para pruebas negativas).
- Si quieres validar filas OK, crea primero categorias/ubicaciones con esos IDs o adapta el CSV.

Archivos:
- events_agresivo_mixto.csv: mezcla de IN/OUT/ADJUST con errores de tipo, cantidades, campos obligatorios y una fila para review.
- events_bordes_espacios.csv: prueba normalizacion de espacios, vacios y parseo de enteros.
- transfers_agresivo_mixto.csv: mezcla de transferencias validas/invalidas, misma ubicacion, cantidad <=0, campos vacios y review.
- transfers_lote_grande.csv: lote amplio para probar volumen y comportamiento parcial.
