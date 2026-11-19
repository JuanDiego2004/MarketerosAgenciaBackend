-- REINICIO MANUAL DEL CRM: empresas, contactos, campañas, contratos y resultados.
-- DESTRUCTIVO: después de COMMIT solo se recupera restaurando una copia de seguridad.
-- Antes de ejecutarlo: respalda la base y detén el backend (local y Render),
-- para que automatizaciones, formularios y webhooks no creen registros mientras limpias.
-- Ejecutar completo en el SQL Editor de Supabase, con el rol postgres.
-- Conserva: usuarios, roles, permisos, canales, intereses, servicios y etapas.
-- No modifica auth.*, contraseñas ni variables de entorno. Conserva las estructuras y RLS.
-- No se incorpora a las migraciones ni se ejecuta al iniciar el backend.

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TEMPORARY TABLE resumen_vaciado_crm (
    tabla text PRIMARY KEY,
    registros_antes bigint NOT NULL
) ON COMMIT DROP;

DO $vaciado$
DECLARE
    objetivos constant text[] := ARRAY[
        'campana', 'campana_audiencia', 'cliente',
        'indicador_kpi', 'metrica_operativa', 'reporte',
        'segmento_audiencia', 'segmento_interes', 'sesion',
        'marketing_cobertura', 'marketing_resultado_geografico', 'marketing_ubicacion',
        'crm_actividad', 'crm_acuerdo', 'crm_acuerdo_campana', 'crm_alcance_servicio',
        'crm_alta_archivo', 'crm_alta_contrato', 'crm_analitica',
        'crm_anuncio', 'crm_anuncio_metrica', 'crm_archivo', 'crm_auditoria',
        'crm_automatizacion', 'crm_baja_canal', 'crm_baja_email', 'crm_baja_token',
        'crm_campo', 'crm_campo_valor', 'crm_campos_revision', 'crm_conector',
        'crm_consentimiento', 'crm_contacto', 'crm_contacto_etiqueta', 'crm_contenido',
        'crm_contrato_ampliacion', 'crm_contrato_anuncio', 'crm_contrato_config',
        'crm_contrato_evento', 'crm_cuenta', 'crm_cuenta_publicitaria',
        'crm_ejecucion', 'crm_empresa_migracion', 'crm_envio', 'crm_etiqueta',
        'crm_evento', 'crm_flujo', 'crm_flujo_paso', 'crm_formulario',
        'crm_gasto_google', 'crm_gasto_meta', 'crm_informe_version',
        'crm_integracion_meta', 'crm_inversion', 'crm_media_anuncio', 'crm_meta',
        'crm_nota', 'crm_oportunidad', 'crm_origen', 'crm_portal_acceso',
        'crm_publicidad_demo', 'crm_publicidad_demo_metrica', 'crm_regla_score',
        'crm_revision_cliente', 'crm_segmento', 'crm_trabajo_servicio',
        'crm_transicion', 'crm_whatsapp_estado', 'crm_whatsapp_mensaje'
    ];
    conservadas constant text[] := ARRAY[
        'usuario', 'rol', 'permiso', 'rol_permiso',
        'canal_difusion', 'interes', 'crm_servicio', 'crm_etapa'
    ];
    desconocidas text;
    tablas text;
    nombre text;
    cantidad bigint;
BEGIN
    -- Si el esquema cambió, exige revisar el alcance antes de borrar cualquier fila.
    SELECT string_agg(quote_ident(c.relname), ', ' ORDER BY c.relname)
      INTO desconocidas
      FROM pg_catalog.pg_class c
      JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
       AND NOT (c.relname = ANY(objetivos) OR c.relname = ANY(conservadas));
    IF desconocidas IS NOT NULL THEN
        RAISE EXCEPTION 'No se vació nada. Revisa estas tablas no contempladas: %', desconocidas;
    END IF;

    FOREACH nombre IN ARRAY objetivos LOOP
        IF to_regclass(format('public.%I', nombre)) IS NOT NULL THEN
            EXECUTE format('SELECT count(*) FROM public.%I', nombre) INTO cantidad;
            INSERT INTO pg_temp.resumen_vaciado_crm VALUES (nombre, cantidad);
        END IF;
    END LOOP;

    SELECT string_agg(format('public.%I', tabla), ', ' ORDER BY tabla)
      INTO tablas FROM pg_temp.resumen_vaciado_crm;
    IF tablas IS NULL THEN
        RAISE EXCEPTION 'No se encontraron tablas operativas del CRM';
    END IF;

    -- Todas las tablas relacionadas se incluyen en UNA operación.
    -- RESTRICT impide borrar indirectamente otras tablas; no se usa CASCADE.
    -- RESTART IDENTITY reinicia únicamente secuencias propias de las tablas vaciadas.
    EXECUTE 'TRUNCATE TABLE ' || tablas || ' RESTART IDENTITY RESTRICT';

    FOR nombre IN SELECT tabla FROM pg_temp.resumen_vaciado_crm LOOP
        EXECUTE format('SELECT count(*) FROM public.%I', nombre) INTO cantidad;
        IF cantidad <> 0 THEN
            RAISE EXCEPTION 'La tabla % no quedó vacía. Se revierte la operación', nombre;
        END IF;
    END LOOP;
END;
$vaciado$;

SELECT tabla, registros_antes, 0 AS registros_despues
  FROM pg_temp.resumen_vaciado_crm
 ORDER BY tabla;

COMMIT;

-- Si aparece un error, ejecuta ROLLBACK; antes de volver a intentar.
-- Después: inicia el backend y recarga el frontend. Podrás entrar con tu usuario actual.
