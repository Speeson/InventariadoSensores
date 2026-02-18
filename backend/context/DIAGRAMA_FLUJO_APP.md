# Diagrama de flujo de la aplicacion (IoTrack)

```mermaid
flowchart TD
    A[Usuario abre app Android] --> B{Hay sesion JWT valida?}
    B -- No --> C[Login / Registro]
    C --> D[Backend FastAPI emite JWT]
    D --> E[App guarda sesion]
    B -- Si --> E

    E --> F{Hay conexion?}
    F -- Si --> G[Modo online]
    F -- No --> H[Modo offline cache-first Room]

    G --> I[Consultar API: productos/stocks/eventos/movimientos]
    I --> J[Backend consulta PostgreSQL]
    J --> K[Respuesta JSON]
    K --> L[App actualiza UI]
    K --> M[App refresca cache local Room]

    H --> N[Mostrar datos cacheados]
    N --> O[Usuario crea accion offline]
    O --> P[Guardar pendiente local]
    P --> Q[WorkManager espera red]
    Q --> R[Reintento de sincronizacion]
    R --> I

    L --> S{Operacion de inventario}
    S -- Escaneo barcode --> T[ML Kit + CameraX]
    T --> U[Crear movimiento/evento]
    U --> I

    S -- Importar CSV --> V[Subida CSV a /imports]
    V --> W[Validacion + review + errores]
    W --> L

    S -- Imprimir etiqueta --> X[Niimbot SDK / fallback app oficial]
    X --> L

    I --> Y[Redis cache + Celery queue]
    Y --> Z[Celery worker procesa eventos]
    Z --> AA[Actualiza stock/movimientos]
    AA --> AB[Genera alertas si umbral bajo]
    AB --> AC[WebSocket/FCM/email]
    AC --> L

    I --> AD[Middleware observabilidad]
    AD --> AE[/metrics Prometheus]
    AE --> AF[Grafana dashboard]
```

