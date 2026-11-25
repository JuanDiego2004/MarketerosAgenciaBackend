# Carga de demostración: 10 registros por módulo operativo

Lote con fecha de referencia 24/11/2026. Sus nombres y descripciones se presentan de forma natural, sin prefijos de prueba. Los correos y sitios web usan dominios reservados bajo `example.invalid`; no representan buzones de personas ni empresas reales. La procedencia sintética y los IDs del lote se conservan en el inventario interno existente `crm_empresa_migracion`, bajo `lote_marketing_20261124`, sin crear tablas adicionales.

| Módulos o datos | Registros del lote |
| --- | ---: |
| Empresas, cotizaciones, contratos, campañas, audiencias, canales de difusión | 10 por módulo |
| Contactos, oportunidades del pipeline, actividades | 10 por módulo |
| Contenido, cuentas publicitarias, anuncios | 10 por módulo |
| Entregables, recomendaciones, ajustes, informes de trabajo por contrato | 10 por módulo |
| Metas, campos personalizados, segmentos, reglas de puntuación | 10 por módulo |
| Automatizaciones, formularios de captación | 10 por módulo, inactivos |
| Indicadores y referencias de reporte por campaña | 10 por módulo |
| Notas, eventos, auditoría de la carga, orígenes de atribución, gastos adicionales | 10 por categoría |
| Perfiles de trabajadores + perfiles de clientes | 10 + 10, sin identidad de autenticación |
| Accesos al portal | 10 desactivados, únicamente para perfiles de prueba |
| Métricas diarias de campañas | 100: 10 campañas × 10 días |
| Estadísticas de anuncios | 900: 10 anuncios × 10 días × 9 países |
| Estadísticas publicitarias DEMO | Las mismas 900 filas, en su modelo independiente |
| Resultados geográficos | 900 desgloses coherentes con los totales diarios |

El calendario usa los anuncios; el dashboard y el análisis comparativo usan las campañas y sus métricas. No se crean tablas duplicadas para esas vistas. Los informes de trabajo y las referencias de reporte son registros de demostración: los PDF/CSV se generan desde las funciones de exportación existentes, no se insertan archivos ficticios.

Los roles, sus permisos y los tres servicios admitidos son catálogos técnicos y se conservan. Los canales de comunicación son configuraciones de proveedores: no se inventan diez conexiones ni historiales de correos enviados. Las etapas del pipeline solo se recuperan si no existen etapas utilizables. Si faltan las tablas geográficas, el ejecutor usa la migración existente `dashboard-v1.sql` dentro de la misma transacción.

Las estadísticas fueron descargadas de Mockaroo con los campos y las validaciones de `CrmMockarooServicio`. Solo se envía el esquema sintético al proveedor, sin información del CRM. Se utiliza PEN para todos los importes del lote. Las conversiones e ingresos son simulados; no se crean oportunidades ganadas ni ventas confirmadas, y el indicador ROI se deja sin calcular. Mockaroo no proporciona alcance único ni interacciones adicionales en ese esquema, por lo que esos campos operativos quedan en cero, sin estimarlos a partir de impresiones.

Los perfiles adicionales están inactivos, sin contraseña y sin `id_supabase`; no se modifica `auth.users` ni `auth.identities`. Sus accesos al portal permanecen desactivados. Los usuarios reales conservan sus empresas y permisos: la carga no reasigna accesos. No se activan automatizaciones, formularios ni publicaciones, ni se registran consentimientos de marketing ficticios.

## Ejecución

Desde el directorio del backend, usando Java y el driver PostgreSQL disponibles localmente:

```powershell
java -cp '<ruta/postgresql.jar>' scripts/PoblarDemo.java --comprobar
java -cp '<ruta/postgresql.jar>' scripts/PoblarDemo.java --aplicar
java -cp '<ruta/postgresql.jar>' scripts/PoblarDemo.java --verificar
java -cp '<ruta/postgresql.jar>' scripts/PoblarDemo.java --normalizar --comprobar
java -cp '<ruta/postgresql.jar>' scripts/PoblarDemo.java --normalizar --aplicar
```

La configuración se lee del entorno y de `.env` sin imprimir credenciales. `--comprobar` ejecuta la carga y la revierte; `--aplicar` confirma solo si pasan las restricciones y la validación de totales; `--verificar` consulta desde una conexión de solo lectura. `--normalizar` modifica únicamente la presentación de los registros ya existentes, conservando IDs, relaciones, importes, estados y métricas; requiere `--comprobar` o `--aplicar`. También se aplica automáticamente al finalizar una carga nueva. El inventario interno permite repetir la carga sin duplicar registros aunque sus nombres y códigos hayan cambiado. Como es habitual en PostgreSQL, una transacción revertida puede consumir valores de las secuencias.

Para preparar un nuevo archivo sintético existe `node scripts/generar-metricas-demo.mjs`; no hace falta volver a llamar a Mockaroo para repetir el lote ya descargado. No se debe reemplazar el archivo de un lote ya aplicado: debe prepararse otro identificador de lote para otra demostración.

La aplicación manual en SQL requiere una sola transacción: comenzar con `BEGIN;`, ejecutar la migración geográfica existente si falta, después `metricas-demo-mockaroo.sql`, después `poblar-demo-10.sql`, después `normalizar-registros-marketing.sql` y finalmente `COMMIT;`. Para cambiar únicamente la presentación del lote existente se ejecuta solo `normalizar-registros-marketing.sql` entre `BEGIN;` y `COMMIT;`. Ante un error, usar `ROLLBACK;`. El ejecutor Java coordina estos pasos automáticamente.

Prueba aislada, sin conexiones externas:

```powershell
node --test tests/poblado-demo.test.mjs
```

Se ejecuta desde el frontend con PostgreSQL WASM desechable. Comprueba reversión, repetición, recuperación de etapas ausentes, conservación de catálogos y registros previos, ausencia de acceso de autenticación y relaciones coherentes entre empresa, contrato, campaña, anuncio y estadísticas.
