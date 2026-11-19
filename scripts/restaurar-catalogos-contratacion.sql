-- Configuración mínima necesaria tras vaciar todas las tablas.
-- No crea empresas, contratos, campañas ni métricas. Conserva catálogos existentes.
BEGIN;
INSERT INTO public.crm_servicio(codigo,nombre,descripcion,activo) VALUES
 ('DIFUSION','Gestión y difusión','Planificación, contenido, aprobaciones y publicaciones simuladas',true),
 ('ANALISIS','Monitoreo y análisis','Consulta de anuncios, estadísticas e informes sin modificar publicidad',true),
 ('INTEGRAL','Gestión integral','Ejecución, análisis y ajustes autorizados dentro del CRM',true)
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO public.canal_difusion(nombre_canal,tipo_canal,estado)
SELECT 'Redes sociales','REDES_SOCIALES','ACTIVO'
WHERE NOT EXISTS(SELECT 1 FROM public.canal_difusion WHERE estado='ACTIVO')
ON CONFLICT(nombre_canal) DO NOTHING;

DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM public.canal_difusion WHERE estado='ACTIVO') THEN
  RAISE EXCEPTION 'Activa un canal existente antes de confirmar contratos';
 END IF;
END $$;
COMMIT;
