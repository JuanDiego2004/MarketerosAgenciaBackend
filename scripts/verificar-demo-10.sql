-- Solo lectura: permite verificar desde otra conexión lo que quedó guardado.
WITH campanas AS (SELECT id_campana FROM public.campana WHERE nombre LIKE 'DEMO10 · %'),
 anuncios AS (SELECT id FROM public.crm_anuncio WHERE codigo LIKE 'DEMO10-AD-%')
SELECT 'Empresas' modulo,count(*) registros FROM public.cliente WHERE correo LIKE 'demo10.empresa.%@example.invalid'
UNION ALL SELECT 'Campañas',count(*) FROM campanas
UNION ALL SELECT 'Contratos',count(*) FROM public.crm_acuerdo WHERE nombre LIKE 'DEMO10 · %' AND tipo='CONTRATO'
UNION ALL SELECT 'Cotizaciones',count(*) FROM public.crm_acuerdo WHERE nombre LIKE 'DEMO10 · %' AND tipo='COTIZACION'
UNION ALL SELECT 'Contactos',count(*) FROM public.crm_contacto WHERE correo LIKE 'demo10.contacto.%@example.invalid'
UNION ALL SELECT 'Pipeline',count(*) FROM public.crm_oportunidad WHERE nombre LIKE 'DEMO10 · %'
UNION ALL SELECT 'Actividades',count(*) FROM public.crm_actividad WHERE nombre LIKE 'DEMO10 · %'
UNION ALL SELECT 'Audiencias',count(*) FROM public.segmento_audiencia WHERE nombre LIKE 'DEMO10 · %'
UNION ALL SELECT 'Canales de difusión',count(*) FROM public.canal_difusion WHERE nombre_canal LIKE 'DEMO10 · %'
UNION ALL SELECT 'Cuentas publicitarias',count(*) FROM public.crm_cuenta_publicitaria WHERE codigo LIKE 'DEMO10-CTA-%'
UNION ALL SELECT 'Anuncios / calendario',count(*) FROM anuncios
UNION ALL SELECT 'Contenido',count(*) FROM public.crm_contenido WHERE nombre LIKE 'DEMO10 · %'
UNION ALL SELECT 'Entregables',count(*) FROM public.crm_trabajo_servicio WHERE nombre LIKE 'DEMO10 · %' AND tipo='ENTREGABLE'
UNION ALL SELECT 'Recomendaciones',count(*) FROM public.crm_trabajo_servicio WHERE nombre LIKE 'DEMO10 · %' AND tipo='RECOMENDACION'
UNION ALL SELECT 'Ajustes',count(*) FROM public.crm_trabajo_servicio WHERE nombre LIKE 'DEMO10 · %' AND tipo='AJUSTE'
UNION ALL SELECT 'Informes por contrato',count(*) FROM public.crm_trabajo_servicio WHERE nombre LIKE 'DEMO10 · %' AND tipo='INFORME'
UNION ALL SELECT 'Metas',count(*) FROM public.crm_meta WHERE nombre LIKE 'DEMO10 · %'
UNION ALL SELECT 'Campos personalizados',count(*) FROM public.crm_campo WHERE codigo LIKE 'demo10_campo_%'
UNION ALL SELECT 'Segmentos dinámicos',count(*) FROM public.crm_segmento WHERE nombre LIKE 'DEMO10 · %'
UNION ALL SELECT 'Reglas de puntuación',count(*) FROM public.crm_regla_score WHERE nombre LIKE 'DEMO10 · %'
UNION ALL SELECT 'Automatizaciones inactivas',count(*) FROM public.crm_automatizacion WHERE nombre LIKE 'DEMO10 · %' AND NOT activa
UNION ALL SELECT 'Trabajadores de prueba',count(*) FROM public.usuario WHERE correo LIKE 'demo10.equipo.%@example.invalid'
UNION ALL SELECT 'Perfiles de cliente de prueba',count(*) FROM public.usuario WHERE correo LIKE 'demo10.portal.%@example.invalid'
UNION ALL SELECT 'Accesos al portal desactivados',count(*) FROM public.crm_portal_acceso p JOIN public.usuario u ON u.id_usuario=p.usuario_id WHERE u.correo LIKE 'demo10.portal.%@example.invalid' AND NOT p.activo
UNION ALL SELECT 'Reportes',count(*) FROM public.reporte WHERE id_campana IN(SELECT * FROM campanas)
UNION ALL SELECT 'Indicadores',count(*) FROM public.indicador_kpi WHERE id_campana IN(SELECT * FROM campanas)
UNION ALL SELECT 'Métricas diarias',count(*) FROM public.metrica_operativa WHERE id_campana IN(SELECT * FROM campanas)
UNION ALL SELECT 'Estadísticas de anuncios',count(*) FROM public.crm_anuncio_metrica WHERE anuncio_id IN(SELECT * FROM anuncios)
UNION ALL SELECT 'Resultados del mapa',count(*) FROM public.marketing_resultado_geografico g JOIN public.metrica_operativa m ON m.id_metrica=g.metrica_id WHERE m.id_campana IN(SELECT * FROM campanas)
UNION ALL SELECT 'Países con resultados',count(DISTINCT pais) FROM public.crm_anuncio_metrica WHERE anuncio_id IN(SELECT * FROM anuncios)
ORDER BY modulo;
