-- Restaura únicamente los roles y permisos implementados por el sistema.
-- Repetible: no borra registros ni duplica asignaciones. No contiene contraseñas.
BEGIN;
SELECT pg_advisory_xact_lock(91823471);

INSERT INTO public.rol(nombre_rol,descripcion)
SELECT v.nombre,v.descripcion FROM (VALUES
 ('ADMINISTRADOR','Administración completa del sistema y sus accesos'),
 ('PROJECT MANAGER','Coordinación de empresas, contratos, campañas y equipo'),
 ('ANALISTA','Seguimiento, métricas, actividades propias y análisis de marketing'),
 ('Cliente','Portal privado de la empresa asignada')
) v(nombre,descripcion)
WHERE NOT EXISTS(SELECT 1 FROM public.rol r WHERE upper(r.nombre_rol)=upper(v.nombre));

INSERT INTO public.permiso(codigo,descripcion,modulo)
SELECT v.codigo,v.descripcion,v.modulo FROM (VALUES
 ('DASHBOARD_VER','Consultar el dashboard','Dashboard'),
 ('CAMPANA_CONSULTAR','Consultar campañas','Campañas'),
 ('CAMPANA_REGISTRAR','Registrar campañas','Campañas'),
 ('CAMPANA_MODIFICAR','Modificar campañas','Campañas'),
 ('AUDIENCIA_CONSULTAR','Consultar audiencias','Audiencias'),
 ('AUDIENCIA_GESTIONAR','Gestionar audiencias','Audiencias'),
 ('AUDIENCIA_VINCULAR','Vincular audiencias a campañas','Audiencias'),
 ('METRICA_CONSULTAR','Consultar métricas','Métricas'),
 ('METRICA_REGISTRAR','Registrar métricas','Métricas'),
 ('CLIENTE_CONSULTAR','Consultar empresas','Clientes'),
 ('CLIENTE_GESTIONAR','Gestionar empresas','Clientes'),
 ('CANAL_CONSULTAR','Consultar canales de difusión','Canales'),
 ('CANAL_GESTIONAR','Gestionar canales de difusión','Canales'),
 ('COMPARATIVA_VER','Consultar análisis comparativos','Resultados'),
 ('REPORTE_GENERAR','Generar y exportar reportes','Reportes'),
 ('KPI_RECALCULAR','Recalcular indicadores de campañas','Indicadores')
) v(codigo,descripcion,modulo)
WHERE NOT EXISTS(SELECT 1 FROM public.permiso p WHERE upper(p.codigo)=v.codigo);

INSERT INTO public.permiso(codigo,descripcion,modulo)
SELECT 'CRM_'||r||'_'||a,'CRM: '||lower(r)||' / '||lower(a),'CRM'
FROM unnest(ARRAY['CONTACTOS','CUENTAS','PIPELINE','ETAPAS','ACTIVIDADES','SEGMENTOS',
 'SCORING','ATRIBUCION','METAS','CONTENIDO','AUTOMATIZACIONES','INTEGRACIONES','AUDITORIA','CAMPOS']) r
CROSS JOIN unnest(ARRAY['LEER','GESTIONAR']) a
WHERE NOT EXISTS(SELECT 1 FROM public.permiso p WHERE upper(p.codigo)='CRM_'||r||'_'||a);

INSERT INTO public.rol_permiso(id_rol,id_permiso)
SELECT r.id_rol,p.id_permiso FROM public.rol r CROSS JOIN public.permiso p
WHERE (
 upper(r.nombre_rol)='ADMINISTRADOR'
 OR upper(r.nombre_rol)='PROJECT MANAGER' AND (
   p.modulo<>'CRM' OR p.codigo LIKE 'CRM_%'
   AND p.codigo !~ '^CRM_(ETAPAS|INTEGRACIONES|AUDITORIA|CAMPOS)_'
   AND p.codigo<>'CRM_ATRIBUCION_GESTIONAR'
 )
 OR upper(r.nombre_rol)='ANALISTA' AND p.codigo IN (
   'DASHBOARD_VER','CAMPANA_CONSULTAR','AUDIENCIA_CONSULTAR',
   'METRICA_CONSULTAR','METRICA_REGISTRAR','COMPARATIVA_VER','REPORTE_GENERAR','KPI_RECALCULAR',
   'CRM_CONTACTOS_LEER','CRM_ACTIVIDADES_LEER','CRM_ACTIVIDADES_GESTIONAR',
   'CRM_SEGMENTOS_LEER','CRM_SEGMENTOS_GESTIONAR',
   'CRM_CONTENIDO_LEER','CRM_CONTENIDO_GESTIONAR','CRM_ATRIBUCION_LEER'
 )
)
AND NOT EXISTS(SELECT 1 FROM public.rol_permiso rp WHERE rp.id_rol=r.id_rol AND rp.id_permiso=p.id_permiso);

-- Cliente no recibe permisos internos. Su acceso se concede por empresa en el portal.
COMMIT;
